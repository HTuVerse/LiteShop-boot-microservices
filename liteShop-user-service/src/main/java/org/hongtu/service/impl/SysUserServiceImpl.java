package org.hongtu.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.session.SaSession;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.hongtu.DAO.*;
import org.hongtu.DTO.*;
import org.hongtu.Result.Result;
import org.hongtu.api.util.ResourceBindingCoordinator;
import org.hongtu.api.dto.ResourceBindingPlan;
import org.hongtu.api.dto.ResourceBinding;
import org.hongtu.VO.AdminStoreOptionVO;
import org.hongtu.VO.AdminTenantOptionVO;
import org.hongtu.VO.AdminTenantSelectionVO;
import org.hongtu.VO.AdminTotpChallengeVO;
import org.hongtu.VO.SysUserVO;
import org.hongtu.constant.RabbitMQConstants;
import org.hongtu.enums.*;
import org.hongtu.service.SysUserRoleService;
import org.hongtu.service.SysUserService;
import org.hongtu.service.SysTenantService;
import org.hongtu.service.SysUserStoreService;
import org.hongtu.service.UserLevelService;
import org.hongtu.saas.service.SaasSubscriptionService;
import org.hongtu.api.client.RestaurantFeignClient;
import org.hongtu.api.dto.StoreSummaryDTO;
import org.hongtu.utils.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.hongtu.utils.RabbitMQUtils;
import org.springframework.transaction.annotation.Transactional;
import org.hongtu.tenant.TenantConstants;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.hongtu.tenant.StoreContext;

import static org.hongtu.constant.CommonConst.*;


/**
 * <p>
 * 系统用户表 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Slf4j
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /** 不存在账号也执行一次 BCrypt，降低通过响应耗时枚举账号的风险。 */
    private static final String DUMMY_PASSWORD_HASH = BCrypt.hashpw("invalid-login-password");

    private static final String ADMIN_LOGIN_TICKET_PREFIX = "admin:login:tenant-selection:";
    private static final int ADMIN_LOGIN_TICKET_TTL_SECONDS = 300;
    private static final String ADMIN_TOTP_CHALLENGE_PREFIX = "admin:login:totp-challenge:";
    private static final int ADMIN_TOTP_CHALLENGE_TTL_SECONDS = 300;

    @Value("${app.webSite.name}")
    private String webSiteName;

    @Resource
    SysUserMapper sysUserMapper;
    @Resource
    ResourceBindingCoordinator resourceBindings;
    @Resource
    wxUtils wxUtilS;
    @Resource
    RedisCache redisCache;
    @Resource
    SysUserRoleMapper sysUserRoleMapper;
    @Resource
    private RabbitMQUtils rabbitMQUtils;
    @Resource
    UserPointsRecordMapper userPointsRecordMapper;
    @Resource
    SysRoleMapper sysRoleMapper;
    @Resource
    UserLevelService userLevelService;
    @Resource
    SysUserRoleService sysUserRoleService;
    @Resource
    UserLevelMapper userLevelMapper;
    @Resource
    SurplusRecordMapper surplusRecordMapper;
    @Resource
    private RateLimitUtil rateLimitUtil;
    @Resource
    private SysTenantService sysTenantService;
    @Resource
    private SysUserStoreService sysUserStoreService;
    @Resource
    private RestaurantFeignClient restaurantFeignClient;
    @Resource
    private org.hongtu.service.AdminLoginSecurity adminLoginSecurity;
    @Resource
    private SaasSubscriptionService subscriptions;

    @Override
    public Result login(String account,String password) {
        if (StrUtil.isBlank(account) || StrUtil.isBlank(password)) {
            return Result.error("账号或密码错误");
        }
        SysTenant tenant;
        try {
            tenant = requireActiveTenant(TenantContext.getTenantIdOrNull());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        String normalizedAccount = StrUtil.trim(account).toLowerCase(Locale.ROOT);
        if (!rateLimitUtil.isAllowed("customer-login:" + tenant.getId() + ":" + normalizedAccount + ":" + clientIp(), 10, 300)) {
            return Result.error("登录尝试过于频繁，请稍后再试");
        }
        SysUser one = findLoginUser(tenant.getId(), account);
        if(one == null) {
            BCrypt.checkpw(password, DUMMY_PASSWORD_HASH);
            return Result.error("账号或密码错误");
        }
        if(one.getStatus()== StatusEnum.DISABLED){
            return Result.error("账号被封禁，无法登入");
        }
        boolean BCrtyCheck = BCrypt.checkpw(password,one.getPassword());
        if(BCrtyCheck){
            List<String> roleCodes = sysUserRoleMapper.getRoleCodesByUserId(one.getId());
            return finishCustomerLogin(one, roleCodes, tenant.getTenantCode());
        }else {
            return Result.error("账号或密码错误");
        }
    }

    @Override
    public Result adminLogin(String account, String password) {
        String normalizedAccount = StrUtil.trim(account);
        return prepareAdminTenantSelection(normalizedAccount, password);
    }

    /**
     * 第一阶段只验证账号密码并列出可管理商户，不创建 Sa-Token 会话。
     * 平台超级管理员仍直接进入平台会话，避免改变平台账号原有语义。
     */
    private Result prepareAdminTenantSelection(String account, String password) {
        String rateLimitAccount = account.toLowerCase(Locale.ROOT);
        if (!rateLimitUtil.isAllowed(RateLimitUtil.globalKey("admin-login", rateLimitAccount + ":" + clientIp()), 20, 60)) {
            return Result.error("登录尝试过于频繁，请稍后再试");
        }
        LambdaQueryWrapper<SysUser> accountQuery = new LambdaQueryWrapper<>();
        applyLoginAccountCondition(accountQuery, account);
        List<SysUser> accountUsers = TenantIgnore.get(() -> sysUserMapper.selectList(
                accountQuery));
        if (accountUsers == null || accountUsers.isEmpty()) {
            return Result.error("账号或密码错误");
        }

        List<SysUser> authenticatedUsers = accountUsers.stream()
                .filter(user -> StrUtil.isNotBlank(user.getPassword())
                        && BCrypt.checkpw(password, user.getPassword()))
                .toList();
        if (authenticatedUsers.isEmpty()) {
            return Result.error("账号或密码错误");
        }
        boolean totpRequired = authenticatedUsers.stream().anyMatch(user -> StrUtil.isNotBlank(user.getTotpSecret()));
        if (totpRequired) {
            return createAdminTotpChallenge(authenticatedUsers);
        }
        return createAdminTenantSelection(authenticatedUsers,
                "账号密码验证成功，请选择商户", "账号或密码错误");
    }

    /**
     * 密码验证通过后创建短时二次验证凭证，凭证中只保存候选用户 ID。
     */
    private Result createAdminTotpChallenge(List<SysUser> authenticatedUsers) {
        String challengeTicket = UUID.randomUUID().toString().replace("-", "");
        List<Long> userIds = authenticatedUsers.stream().map(SysUser::getId).toList();
        try {
            redisCache.setCacheObject(
                    ADMIN_TOTP_CHALLENGE_PREFIX + challengeTicket,
                    JSON.toJSONString(userIds),
                    ADMIN_TOTP_CHALLENGE_TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("创建动态口令二次验证凭证失败", e);
            return Result.error("暂时无法创建二次验证凭证，请稍后重试");
        }
        return Result.success("请输入动态口令",
                new AdminTotpChallengeVO(true, challengeTicket, ADMIN_TOTP_CHALLENGE_TTL_SECONDS));
    }

    /**
     * 校验二次验证凭证与动态口令，通过后继续原有管理端登录流程。
     */
    @Override
    public Result verifyAdminTotp(String challengeTicket, String totpCode) {
        String normalizedTicket = StrUtil.trim(challengeTicket);
        if (!rateLimitUtil.isAllowed(
                RateLimitUtil.globalKey("admin-totp", normalizedTicket + ":" + clientIp()), 10, 300)) {
            return Result.error("动态口令尝试过于频繁，请重新登录");
        }
        String serializedUserIds;
        try {
            serializedUserIds = redisCache.getCacheObject(ADMIN_TOTP_CHALLENGE_PREFIX + normalizedTicket);
        } catch (Exception e) {
            log.error("读取动态口令二次验证凭证失败", e);
            return Result.error("二次验证凭证校验失败，请重新登录");
        }
        if (StrUtil.isBlank(serializedUserIds)) {
            return Result.error("二次验证凭证已失效，请重新登录");
        }

        List<Long> userIds;
        try {
            userIds = JSON.parseArray(serializedUserIds, Long.class);
        } catch (Exception e) {
            log.warn("动态口令二次验证凭证内容非法", e);
            return Result.error("二次验证凭证无效，请重新登录");
        }
        List<SysUser> authenticatedUsers = TenantIgnore.get(() -> userIds.stream()
                .map(sysUserMapper::selectById)
                .filter(Objects::nonNull)
                .toList());
        boolean verified = authenticatedUsers.stream().anyMatch(user ->
                org.hongtu.security.TotpVerifier.matches(user.getTotpSecret(), totpCode));
        if (!verified) {
            return Result.error("动态口令错误");
        }
        redisCache.deleteObject(ADMIN_TOTP_CHALLENGE_PREFIX + normalizedTicket);
        return createAdminTenantSelection(authenticatedUsers,
                "动态口令验证成功，请选择商户", "账号或密码错误");
    }

    private Result createAdminTenantSelection(List<SysUser> accountUsers,
                                              String successMessage,
                                              String noPermissionMessage) {
        Map<Long, Long> tenantUserIds = new LinkedHashMap<>();
        boolean disabledCredentialMatched = false;
        boolean adminCredentialMatched = false;

        for (SysUser user : accountUsers) {
            List<String> roleCodes = TenantIgnore.get(
                    () -> sysUserRoleMapper.getRoleCodesByUserId(user.getId()));
            List<String> safeRoleCodes = roleCodes == null ? List.of() : roleCodes;
            boolean superAdmin = safeRoleCodes.contains(TenantConstants.ROLE_SUPER_ADMIN);
            boolean tenantAdmin = safeRoleCodes.stream().anyMatch(code -> !"USER".equals(code) && !"SUPER_ADMIN".equals(code));

            if (superAdmin && user.getTenantId() == null) {
                if (user.getStatus() == StatusEnum.DISABLED) {
                    return Result.error("账号被封禁，无法登入");
                }
                return finishLogin(user, safeRoleCodes, null);
            }
            if (!tenantAdmin || superAdmin || user.getTenantId() == null) {
                continue;
            }
            adminCredentialMatched = true;
            if (user.getStatus() == StatusEnum.DISABLED) {
                disabledCredentialMatched = true;
                continue;
            }

            SysTenant tenant = TenantIgnore.get(() -> sysTenantService.getById(user.getTenantId()));
            if (!isTenantActive(tenant)) {
                continue;
            }
            tenantUserIds.putIfAbsent(tenant.getId(), user.getId());
        }

        if (tenantUserIds.isEmpty()) {
            if (disabledCredentialMatched) {
                return Result.error("账号被封禁，无法登入");
            }
            return adminCredentialMatched
                    ? Result.error("当前账号没有可用的管理商户")
                    : Result.error(noPermissionMessage);
        }

        List<AdminTenantOptionVO> options = tenantUserIds.entrySet().stream()
                .map(entry -> buildWorkspaceOption(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AdminTenantOptionVO::getTenantName,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
        if (options.isEmpty()) {
            return Result.error("当前账号没有可用工作空间");
        }

        adminLoginSecurity.requireOpenForMerchant();
        if (options.size() == 1) {
            AdminTenantOptionVO option = options.getFirst();
            boolean tenantAdmin = option.getRoleCodes().contains(TenantConstants.ROLE_ADMIN);
            boolean singleStore = option.getStores().size() == 1;
            if (tenantAdmin || singleStore) {
                Long userId = tenantUserIds.get(option.getTenantId());
                SysUser user = TenantIgnore.get(() -> sysUserMapper.selectById(userId));
                Long activeStoreId = tenantAdmin ? null : option.getStores().getFirst().getStoreId();
                return finishLogin(user, option.getRoleCodes(), option.getTenantCode(),
                        activeStoreId, tenantUserIds);
            }
        }

        String selectionTicket = UUID.randomUUID().toString().replace("-", "");
        Map<String, Long> ticketPayload = new LinkedHashMap<>();
        tenantUserIds.forEach((candidateTenantId, userId) ->
                ticketPayload.put(String.valueOf(candidateTenantId), userId));
        try {
            redisCache.setCacheObject(
                    ADMIN_LOGIN_TICKET_PREFIX + selectionTicket,
                    JSON.toJSONString(ticketPayload),
                    ADMIN_LOGIN_TICKET_TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("创建商户选择登录凭证失败", e);
            return Result.error("暂时无法创建登录凭证，请稍后重试");
        }

        return Result.success(successMessage,
                new AdminTenantSelectionVO(true, selectionTicket,
                        ADMIN_LOGIN_TICKET_TTL_SECONDS, options));
    }

    @Override
    public Result selectAdminTenant(String selectionTicket, Long tenantId, Long storeId) {
        adminLoginSecurity.requireOpenForMerchant();
        String serializedTicket;
        try {
            serializedTicket = redisCache.getAndDeleteCacheObject(
                    ADMIN_LOGIN_TICKET_PREFIX + StrUtil.trim(selectionTicket));
        } catch (Exception e) {
            log.error("读取商户选择登录凭证失败", e);
            return Result.error("登录凭证校验失败，请重新登录");
        }
        if (StrUtil.isBlank(serializedTicket)) {
            return Result.error("登录凭证已失效，请重新登录");
        }

        JSONObject ticketPayload;
        try {
            ticketPayload = JSON.parseObject(serializedTicket);
        } catch (Exception e) {
            log.warn("商户选择登录凭证内容非法", e);
            return Result.error("登录凭证无效，请重新登录");
        }
        Long userId = ticketPayload.getLong(String.valueOf(tenantId));
        if (userId == null) {
            return Result.error("无权进入所选商户，请重新登录");
        }

        SysUser user = TenantIgnore.get(() -> sysUserMapper.selectById(userId));
        if (user == null || !Objects.equals(user.getTenantId(), tenantId)) {
            return Result.error("账号与商户的授权关系已失效，请重新登录");
        }
        if (user.getStatus() == StatusEnum.DISABLED) {
            return Result.error("账号被封禁，无法登入");
        }
        SysTenant tenant = TenantIgnore.get(() -> sysTenantService.getById(tenantId));
        if (!isTenantActive(tenant)) {
            return Result.error("商户已停用、已到期或不存在");
        }

        List<String> roleCodes = TenantIgnore.get(
                () -> sysUserRoleMapper.getRoleCodesByUserId(userId));
        List<String> safeRoleCodes = roleCodes == null ? List.of() : roleCodes;
        boolean allowed = safeRoleCodes.stream().anyMatch(code -> !"USER".equals(code) && !"SUPER_ADMIN".equals(code));
        if (!allowed || safeRoleCodes.contains(TenantConstants.ROLE_SUPER_ADMIN)) {
            return Result.error("当前账号已无该商户的管理权限，请重新登录");
        }
        Map<Long, Long> workspaceUsers = new LinkedHashMap<>();
        ticketPayload.forEach((key, value) -> {
            Long mappedUserId = ticketPayload.getLong(key);
            if (mappedUserId != null) {
                workspaceUsers.put(Long.valueOf(key), mappedUserId);
            }
        });
        return finishLogin(user, safeRoleCodes, tenant.getTenantCode(), storeId, workspaceUsers);
    }

    @Override
    public Result listAdminWorkspaces() {
        if (TenantContext.isSuperAdmin()) {
            return Result.error("平台超级管理员请使用商户管理进入工作空间");
        }
        Map<Long, Long> workspaceUsers = currentWorkspaceUsers();
        List<AdminTenantOptionVO> options = workspaceUsers.entrySet().stream()
                .map(entry -> buildWorkspaceOption(entry.getKey(), entry.getValue()))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AdminTenantOptionVO::getTenantName,
                        Comparator.nullsLast(String::compareTo)))
                .toList();
        return Result.success(options);
    }

    @Override
    public Result switchAdminWorkspace(Long tenantId, Long storeId) {
        if (TenantContext.isSuperAdmin()) {
            return Result.error("平台超级管理员请使用商户管理切换商户");
        }
        Map<Long, Long> workspaceUsers = currentWorkspaceUsers();
        Long targetUserId = workspaceUsers.get(tenantId);
        if (targetUserId == null) {
            return Result.error("无权进入所选商户");
        }
        SysUser user = TenantIgnore.get(() -> sysUserMapper.selectById(targetUserId));
        SysTenant tenant = TenantIgnore.get(() -> sysTenantService.getById(tenantId));
        if (user == null || !Objects.equals(user.getTenantId(), tenantId) || !isTenantActive(tenant)) {
            return Result.error("商户或账号授权已失效");
        }
        if (user.getStatus() == StatusEnum.DISABLED) {
            return Result.error("账号被封禁，无法进入");
        }
        List<String> roleCodes = TenantIgnore.get(
                () -> sysUserRoleMapper.getRoleCodesByUserId(targetUserId));
        List<String> safeRoleCodes = roleCodes == null ? List.of() : roleCodes;
        if (safeRoleCodes.contains(TenantConstants.ROLE_SUPER_ADMIN)
                || safeRoleCodes.stream().noneMatch(code -> !"USER".equals(code))) {
            return Result.error("当前账号已无该商户的管理权限");
        }

        long currentUserId = StpUtil.getLoginIdAsLong();
        if (currentUserId == targetUserId) {
            return TenantScope.call(tenantId, () -> refreshCurrentWorkspace(
                    user, safeRoleCodes, tenant, storeId, workspaceUsers));
        }

        String previousToken = StpUtil.getTokenValue();
        Result result = finishLogin(user, safeRoleCodes, tenant.getTenantCode(), storeId, workspaceUsers);
        if (Result.CODE_SUCCESS.equals(result.getCode()) && StrUtil.isNotBlank(previousToken)) {
            StpUtil.getStpLogic().logoutByTokenValue(previousToken);
        }
        return result;
    }

    @Override
    public Result listUsers(Integer currentPage, Integer pageSize, String keyWord,Integer level) {
        // 构建查询条件
        LambdaQueryWrapper<SysUser> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyWord)) {
            queryWrapper.like(SysUser::getUsername, keyWord)
                    .or().like(SysUser::getNickname, keyWord)
                    .or().like(SysUser::getPhone, keyWord)
                    .or().like(SysUser::getEmail, keyWord);
        }
        if(level!=null){
            queryWrapper.eq(SysUser::getLevel, level);
        }
        // 按创建时间降序排序
        queryWrapper.orderByDesc(SysUser::getCreateTime);

        // 分页查询
        Page<SysUser> page = new Page<>(currentPage, pageSize);
        Page<SysUser> sysUserPage = sysUserMapper.selectPage(page, queryWrapper);

        List<SysUser> records = sysUserPage.getRecords();
        if (records.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("total", 0);
            result.put("pages", 0);
            result.put("current", currentPage);
            result.put("size", pageSize);
            result.put("records", new ArrayList<>());
            return Result.success(result);
        }

        // 批量查询角色信息（优化N+1问题）
        List<Long> userIds = records.stream().map(SysUser::getId).collect(Collectors.toList());
        Map<Long, List<String>> userRolesMap = new HashMap<>();
        List<SysUserRole> userRoles = sysUserRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getUserId, userIds)
        );
        for (SysUserRole userRole : userRoles) {
            Long userId = userRole.getUserId();
            SysRole role = sysRoleMapper.selectById(userRole.getRoleId());
            if (role != null) {
                userRolesMap.computeIfAbsent(userId, k -> new ArrayList<>()).add(role.getRoleCode());
            }
        }

        // 批量查询等级信息（优化N+1问题）
        Set<Integer> levelCodes = records.stream().map(SysUser::getLevel).collect(Collectors.toSet());
        Map<Integer, String> levelNamesMap = new HashMap<>();
        if (!levelCodes.isEmpty()) {
            List<UserLevel> levels = userLevelMapper.listLevels();
            for (UserLevel userLevel : levels) {
                levelNamesMap.put(userLevel.getLevelCode(), userLevel.getLevelName());
            }
        }

        // 转换为VO列表
        List<SysUserVO> userVOList = records.stream()
                .map(user -> {
                    SysUserVO vo = BeanCopyUtils.copyBean(user, SysUserVO.class);
                    vo.setRoleCode(userRolesMap.getOrDefault(user.getId(), new ArrayList<>()));
                    vo.setLevelName(levelNamesMap.getOrDefault(user.getLevel(), "未知等级"));
                    return vo;
                })
                .toList();

        // 构建分页结果
        Map<String, Object> result = new HashMap<>();
        result.put("total", sysUserPage.getTotal());
        result.put("pages", sysUserPage.getPages());
        result.put("current", sysUserPage.getCurrent());
        result.put("size", sysUserPage.getSize());
        result.put("records", userVOList);

        return Result.success(result);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result addUser(SysUser sysUser) {
        boolean superAdmin = StpUtil.hasRole(TenantConstants.ROLE_SUPER_ADMIN);
        Long currentTenantId = TenantContext.getTenantIdOrNull();
        Long targetTenantId = superAdmin && currentTenantId == null
                ? sysUser.getTenantId() : currentTenantId;
        if (targetTenantId == null) {
            return Result.error("新增租户用户时必须指定租户");
        }
        if (sysTenantService.getById(targetTenantId) == null) {
            return Result.error("目标租户不存在");
        }
        if (sysUser.getRoleCode() != null && sysUser.getRoleCode().contains(TenantConstants.ROLE_SUPER_ADMIN)) {
            return Result.error("不能在租户内创建平台超级管理员");
        }
        sysUser.setTenantId(targetTenantId);
        // 检查用户名是否已存在
        SysUser existUser = lambdaQuery().eq(SysUser::getTenantId, targetTenantId)
                .eq(SysUser::getUsername, sysUser.getUsername()).one();
        if (existUser != null) {
            return Result.error("用户名已存在");
        }
        if(hongTuUtil.containsChinese(sysUser.getUsername())){
            return Result.error("用户名不能包含中文");
        }
        // 检查手机号是否已存在
        if (StrUtil.isNotBlank(sysUser.getPhone())) {
            Long phoneCount = lambdaQuery().eq(SysUser::getTenantId, targetTenantId)
                    .eq(SysUser::getPhone, sysUser.getPhone()).count();
            if (phoneCount > 0) {
                return Result.error("手机号已被使用");
            }
        }

        // 密码加密
        if (StrUtil.isNotBlank(sysUser.getPassword())) {
            if (sysUser.getPassword().length() < 8) {
                return Result.error("初始密码长度不能少于8位");
            }
            sysUser.setPassword(BCrypt.hashpw(sysUser.getPassword()));
        } else {
            return Result.error("新增用户必须设置初始密码");
        }

        // 提前生成主键，确保业务提交前能占用头像资源。
        if (sysUser.getId() == null) sysUser.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
        ResourceBindingPlan avatarPlan = resourceBindings.prepare(new org.hongtu.api.dto.ResourceBindingRequest(
                targetTenantId, "USER", sysUser.getId(), List.of("avatar"),
                List.of(new ResourceBinding("avatar", 0, sysUser.getAvatarResourceId(), sysUser.getAvatar()))));
        sysUser.setAvatar(resourceBindings.resolvedSingle(avatarPlan, sysUser.getAvatar()));
        // 保存用户
        boolean saved = save(sysUser);
        if (!saved) {
            return Result.error("添加用户失败");
        }

        List<String> requestedCodes = sysUser.getRoleCode() == null || sysUser.getRoleCode().isEmpty() ? List.of("USER") : sysUser.getRoleCode().stream().distinct().toList();
        if (!superAdmin && !requestedCodes.equals(List.of("USER"))) StpUtil.checkPermission("system:user:assign-role");
        TenantScope.run(targetTenantId, () -> {
            List<Long> ids = getUserRoleIds(requestedCodes);
            if (ids.size() != requestedCodes.size()) throw new org.hongtu.exception.BusinessException(400,"存在无效或跨租户角色");
            org.hongtu.api.util.FeignResultUtil.unwrap(sysUserRoleService.assignRoles(sysUser.getId(), ids));
            UserLevel initial = sysUser.getLevel() == null ? userLevelMapper.getLevelByExp(0) : userLevelMapper.getLevelByLevelCode(sysUser.getLevel());
            if (initial == null) throw new org.hongtu.exception.BusinessException(400,"请选择当前租户方案中的有效等级");
            sysUser.setLevel(initial.getLevelCode());sysUser.setExp(initial.getRequiredExp());
            if (!updateById(sysUser)) throw new IllegalStateException("初始化用户等级失败");
        });
        resourceBindings.completeAfterCommit(avatarPlan);

        return Result.success("添加用户成功", org.hongtu.utils.BeanCopyUtils.copyBean(sysUser, org.hongtu.VO.SysUserVO.class));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteUser(Long id) {
        // 检查用户是否存在
        SysUser user = getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        long loginId = StpUtil.getLoginIdAsLong();
        if(loginId ==id){
            return Result.error("无法删除自己");
        }
        List<String> targetId = sysRoleMapper.selectRoleCodesByUserId(id);
        if(StpUtil.hasRole("SUPER_ADMIN")){
            if( targetId.contains("SUPER_ADMIN")){
                return Result.error("无法删除其它的超级管理员");
            }
        } else if (StpUtil.hasPermission("system:user:delete")) {
            if( targetId.contains("SUPER_ADMIN")||targetId.contains("ADMIN")){
                return Result.error("无法删除超级管理员以及其它的管理员");
            }
        }else {
            return Result.error("无权限删除用户");
        }
        // 删除用户
        boolean removed = removeById(id);
        if (removed) {
            resourceBindings.clearAfterCommit(user.getTenantId() == null ? 0L : user.getTenantId(),
                    "USER", id, "avatar");
            return Result.success("删除用户成功");
        } else {
            return Result.error("删除用户失败");
        }
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result phoneOrEmailVerifyCodeLogin(String account,String verifyCode){
        SysTenant tenant;
        try {
            tenant = requireActiveTenant(TenantContext.getTenantIdOrNull());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        SysUser one = lambdaQuery().eq(SysUser::getTenantId, tenant.getId()).and(wrapper -> wrapper
                .eq(SysUser::getPhone, account)
                .or()
                .eq(SysUser::getEmail,account)).one();
        SysUser sysUser1 = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenant.getId())
                .eq(SysUser::getUsername, account));

        if(one==null){
            // 用户不存在，注册流程
            Result verifyResult = verifyCode(String.valueOf(tenant.getId()), account, verifyCode, SMS_LOGIN_CODE_PREFIX);
            if (!Result.CODE_SUCCESS.equals(verifyResult.getCode())) {
                return verifyResult;
            }

            try {
                SysUser sysUser = new SysUser();
                sysUser.setTenantId(tenant.getId());
                if(sysUser1!=null){
                    sysUser.setUsername(account+ RandomUtil.randomString(8));
                }else {
                    sysUser.setUsername(account);
                }
                sysUser.setNickname(account);
                if(hongTuUtil.isPhone(account)){
                    sysUser.setPhone(account);
                }else if (hongTuUtil.isEmail(account)){
                    sysUser.setEmail(account);
                }
                save(sysUser);
                assignDefaultUserRole(sysUser);
                // 构造完整的返回信息
                List<String> roleCodes = sysUserRoleMapper.getRoleCodesByUserId(sysUser.getId());
                return finishCustomerLogin(sysUser, roleCodes, tenant.getTenantCode());
            }catch (Exception e){
                log.error("验证码注册失败, account: {}", account, e);
                return Result.error("验证码注册失败");
            }
        }else {
            // 用户已存在，登录流程
            Result verifyResult = verifyCode(String.valueOf(tenant.getId()), account, verifyCode, SMS_LOGIN_CODE_PREFIX);
            if (!Result.CODE_SUCCESS.equals(verifyResult.getCode())) {
                return verifyResult;
            }

            try {
                // 检查账号状态
                if(one.getStatus() == StatusEnum.DISABLED){
                    return Result.error("账号被封禁，无法登入");
                }

                List<String> roleCodes = sysUserRoleMapper.getRoleCodesByUserId(one.getId());
                return finishCustomerLogin(one, roleCodes, tenant.getTenantCode());
            }catch (Exception e){
                log.error("验证码登录失败, account: {}", account, e);
                return Result.error("验证码登录失败");
            }
        }
    }

    @Override
    public Result adminPhoneOrEmailVerifyCodeLogin(String account, String verifyCode) {
        // 管理端验证码在选择商户前按平台作用域验证，验证成功后再汇总该账号的商户授权。
        Result verifyResult = verifyCode("PLATFORM", account, verifyCode, SMS_LOGIN_CODE_PREFIX);
        if (!Result.CODE_SUCCESS.equals(verifyResult.getCode())) {
            return verifyResult;
        }
        LambdaQueryWrapper<SysUser> loginQuery = new LambdaQueryWrapper<>();
        applyLoginAccountCondition(loginQuery, account);
        List<SysUser> accountUsers = TenantIgnore.get(() -> sysUserMapper.selectList(loginQuery));
        if (accountUsers == null || accountUsers.isEmpty()) {
            return Result.error("账号不存在，无法使用管理员验证码登录");
        }
        return createAdminTenantSelection(accountUsers,
                "验证码验证成功，请选择商户", "无管理员权限");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result updateUserInfo(SysUser sysUser) {
        // 获取当前登录用户ID
        long userId = StpUtil.getLoginIdAsLong();
        // 查询当前用户信息
        SysUser currentUser = sysUserMapper.selectById(userId);
        if (currentUser == null) {
            return Result.error("用户不存在");
        }
        // 设置不允许修改的字段
        sysUser.setId((long) userId);
        sysUser.setTenantId(currentUser.getTenantId());
        // 如果修改了用户名，检查是否与其他用户冲突
        if (!currentUser.getUsername().equals(sysUser.getUsername())) {
            if(hongTuUtil.containsChinese(sysUser.getUsername())){
                return Result.error("用户名不能包含中文");
            }
            SysUser usernameCheck = lambdaQuery()
                    .eq(SysUser::getUsername, sysUser.getUsername())
                    .ne(SysUser::getId, sysUser.getId())
                    .one();
            if (usernameCheck != null) {
                return Result.error("用户名已被使用");
            }

        }
        sysUser.setOpenId(currentUser.getOpenId());
        sysUser.setStatus(currentUser.getStatus());
        sysUser.setCreateTime(currentUser.getCreateTime());
        sysUser.setUpdateTime(LocalDateTime.now());
        sysUser.setSurplusMoney(currentUser.getSurplusMoney());
        sysUser.setPoints(currentUser.getPoints());
        sysUser.setExp(currentUser.getExp());
        sysUser.setLevel(currentUser.getLevel());
        sysUser.setPhone(currentUser.getPhone());//通过特定接口修改手机号
        //加密用户密码
        sysUser.setPassword(currentUser.getPassword());
        // 更新用户信息
        ResourceBindingPlan avatarPlan = normalizeAvatar(sysUser, currentUser);
        boolean updated = updateById(sysUser);
        if (updated) {
            resourceBindings.completeAfterCommit(avatarPlan);
            // 查询更新后的用户信息
            SysUser updatedUser = sysUserMapper.selectById(userId);
            SysUserVO sysUserVO = BeanCopyUtils.copyBean(updatedUser, SysUserVO.class);
            return Result.success("更新成功", sysUserVO);
        } else {
            return Result.error("更新失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result addUserPoints(Long userId, Integer points, PointsSourceEnum sourceType,String remark) {
        // 参数校验
        if (userId == null || points == null || points <= 0) {
            return Result.error("参数错误");
        }
        try {
            // 查询用户
            SysUser user = getById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            // 更新用户积分
            int i = sysUserMapper.addUserPoints(userId, points);
            if (i>0) {
                // 查询更新后的用户信息并记录积分变动详情
                SysUser updatedUser = getById(userId);
                UserPointsRecord userPointsRecord = new UserPointsRecord();
                userPointsRecord.setUserId(userId);
                userPointsRecord.setUsername(updatedUser.getUsername());
                userPointsRecord.setAfterPoints(updatedUser.getPoints());
                userPointsRecord.setBeforePoints(user.getPoints());
                userPointsRecord.setOperationType(PointsOperationEnum.INCREASE);
                userPointsRecord.setPoints(points);
                userPointsRecord.setSource(sourceType);
                userPointsRecord.setRemark(remark);
                userPointsRecordMapper.insert(userPointsRecord);
                return Result.success("积分增加成功", updatedUser.getPoints());
            } else {
                return Result.error("积分增加失败");
            }
        } catch (Exception e) {
            return Result.error("系统异常，积分增加失败");
        }
    }

    /**
     * 获取用户优惠券数量
     * @param userId 用户ID
     * @return 优惠券数量
     */
    @Override
    public Long getUserCouponCount(Long userId) {
        Long previousTenantId = TenantContext.getTenantIdOrNull();
        boolean previousSuperAdmin = TenantContext.isSuperAdmin();
        boolean temporaryTenantContext = false;
        try {
            // 平台超级管理员查看某租户用户时当前 tenantId 为空，MQ 仍必须携带目标用户租户。
            if (previousTenantId == null) {
                SysUser targetUser = TenantIgnore.get(() -> getById(userId));
                if (targetUser == null || targetUser.getTenantId() == null) {
                    return 0L;
                }
                TenantContext.setTenantId(targetUser.getTenantId());
                temporaryTenantContext = true;
            }
            // 创建请求消息，包含用户ID
            Map<String, Object> message = new HashMap<>();
            message.put("userId", userId);
            log.info("发送优惠券数量查询请求: userId={}", userId);
            // 使用优化后的RabbitMQUtils发送请求并等待响应
            Object response = rabbitMQUtils.sendAndReceive(
                    RabbitMQConstants.Coupon.EXCHANGE,
                    RabbitMQConstants.Coupon.COUNT_QUERY_KEY,
                    message,
                    3000 // 3秒超时
            );
            // 处理响应
            if (response != null && response instanceof Map) {
                Map<String, Object> responseMap = (Map<String, Object>) response;
                if (responseMap.get("count") != null) {
                    return Long.valueOf(responseMap.get("count").toString());
                }
            }
            log.warn("获取用户优惠券数量超时或返回为空: userId={}", userId);
            return 0L;
        } catch (Exception e) {
            log.error("获取用户优惠券数量异常", e);
            return 0L;
        } finally {
            if (temporaryTenantContext) {
                TenantContext.clear();
                if (previousTenantId != null) {
                    TenantContext.setTenantId(previousTenantId);
                }
                TenantContext.setSuperAdmin(previousSuperAdmin);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result adminEditUser(SysUser sysUser) {
        if (sysUser == null || sysUser.getId() == null) {
            return Result.error("用户信息不完整");
        }
        SysUser existingUser = getById(sysUser.getId());
        if (existingUser == null) {
            return Result.error("用户不存在");
        }
        if (TenantContext.isSuperAdmin() && TenantContext.getTenantIdOrNull() == null
                && existingUser.getTenantId() != null) {
            return TenantScope.call(existingUser.getTenantId(),
                    () -> adminEditUserInCurrentTenant(sysUser));
        }
        return adminEditUserInCurrentTenant(sysUser);
    }

    private Result adminEditUserInCurrentTenant(SysUser sysUser) {
        // 切入目标租户后重新读取，后续用户名、手机号、角色和等级校验都只能命中该租户。
        SysUser existingUser = getById(sysUser.getId());
        if (existingUser == null) {
            return Result.error("用户不存在或不在当前租户");
        }
        sysUser.setTenantId(existingUser.getTenantId());
        // 保留不允许修改的字段
        sysUser.setOpenId(existingUser.getOpenId());
        sysUser.setPassword(existingUser.getPassword()); // 密码通过专门的接口修改
        sysUser.setCreateTime(existingUser.getCreateTime());
        sysUser.setSurplusMoney(existingUser.getSurplusMoney());
        sysUser.setPoints(existingUser.getPoints());
        // 如果修改了用户名，检查是否与其他用户冲突
        if (!existingUser.getUsername().equals(sysUser.getUsername())) {
            if(hongTuUtil.containsChinese(sysUser.getUsername())){
                return Result.error("用户名不能包含中文");
            }
            SysUser usernameCheck = lambdaQuery()
                    .eq(SysUser::getUsername, sysUser.getUsername())
                    .ne(SysUser::getId, sysUser.getId())
                    .one();
            if (usernameCheck != null) {
                return Result.error("用户名已被使用");
            }
        }

        //状态修改判断
        if (!existingUser.getStatus().equals(sysUser.getStatus())) {
            List<String> targetId = sysRoleMapper.selectRoleCodesByUserId(sysUser.getId());
            if(StpUtil.hasRole("SUPER_ADMIN")){
                if( targetId.contains("SUPER_ADMIN")){
                    return Result.error("无法编辑其它的超级管理员");
                }
            } else if (StpUtil.hasPermission("system:user:edit")) {
                if( targetId.contains("SUPER_ADMIN")||targetId.contains("ADMIN")){
                    return Result.error("无法编辑超级管理员以及其它的管理员");
                }
            }else {
                return Result.error("无权限编辑用户");
            }
        }

        // 如果修改了手机号，检查是否与其他用户冲突
        if (sysUser.getPhone() != null && !sysUser.getPhone().equals(existingUser.getPhone())) {
            if(!hongTuUtil.isPhone(sysUser.getPhone())){
                return Result.error("手机号格式有误");
            }
            Long phoneCheck = sysUserMapper.getUserIdByPhone(sysUser.getPhone());
            if (phoneCheck != null && !phoneCheck.equals(sysUser.getId())) {
                return Result.error("手机号已被使用");
            }
        }

        if (sysUser.getLevel() == null) sysUser.setLevel(existingUser.getLevel());
        //等级修改判断
        if (!existingUser.getLevel().equals(sysUser.getLevel())) {
            if (!TenantContext.isSuperAdmin()) StpUtil.checkPermission("system:user:level");
            org.hongtu.api.util.FeignResultUtil.unwrap(userLevelService.updateUserLevel(sysUser.getId(),sysUser.getLevel()));
            sysUser.setExp(userLevelMapper.getLevelByLevelCode(sysUser.getLevel()).getRequiredExp());
        }
        //角色修改判断
        List<String> roleList = StpUtil.getRoleList(sysUser.getId());
        // 将两个列表转换为Set后比较，忽略元素顺序
        Set<String> currentRoleSet = new HashSet<>(roleList);
        Set<String> newRoleSet = sysUser.getRoleCode() == null ? currentRoleSet : new HashSet<>(sysUser.getRoleCode());
        if(!currentRoleSet.equals(newRoleSet)){
            if (!TenantContext.isSuperAdmin()) StpUtil.checkPermission("system:user:assign-role");
            // 根据角色编码列表获取角色ID列表
            List<Long> userRoleIds = getUserRoleIds(sysUser.getRoleCode());
            // 分配角色
            if (userRoleIds.size() != newRoleSet.size()) throw new org.hongtu.exception.BusinessException(400,"存在无效或跨租户角色");
            org.hongtu.api.util.FeignResultUtil.unwrap(sysUserRoleService.assignRoles(sysUser.getId(), userRoleIds));
        }

        // 更新用户信息
        ResourceBindingPlan avatarPlan = normalizeAvatar(sysUser, existingUser);
        boolean success = updateById(sysUser);
        if (success) {
            resourceBindings.completeAfterCommit(avatarPlan);
            return Result.success("更新用户信息成功");
        } else {
            throw new org.hongtu.exception.BusinessException(500,"更新用户信息失败");
        }
    }

    @Override
    public Result resetPassword(String account, String verifyCode, String newPassword) {
        // 1. 验证账号格式
        boolean isPhone = hongTuUtil.isPhone(account);
        boolean isEmail = hongTuUtil.isEmail(account);

        if (!isPhone && !isEmail) {
            return Result.error("请输入正确的手机号或邮箱");
        }

        // 2. 查询用户是否存在
        SysTenant tenant;
        try {
            tenant = requireActiveTenant(TenantContext.getTenantIdOrNull());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        SysUser user = lambdaQuery()
                .eq(SysUser::getTenantId, tenant.getId())
                .and(wrapper -> wrapper
                        .eq(isPhone, SysUser::getPhone, account)
                        .eq(isEmail, SysUser::getEmail, account))
                .one();

        if (user == null) {
            return Result.error(isPhone ? "该手机号未注册" : "该邮箱未注册");
        }

        // 3. 验证验证码
        String publicTenantScope = String.valueOf(tenant.getId());
        Result verifyResult = verifyCode(publicTenantScope, account, verifyCode, SMS_RESET_CODE_PREFIX);
        if (!Result.CODE_SUCCESS.equals(verifyResult.getCode())) {
            return verifyResult;
        }

        // 4. 验证密码强度
        if(StrUtil.isBlank(newPassword)){
            // 仅验证验证码，不重置密码
            redisCache.deleteObject(tenantCacheKey(RETRY_KEY_PREFIX, publicTenantScope, account));
            return Result.success("验证码正确，请输入新密码");
        }

        if (newPassword.length() < 6) {
            return Result.error("密码长度不能少于6位");
        }

        // 5. 更新密码
        user.setPassword(BCrypt.hashpw(newPassword));
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);

        // 6. 验证成功，立即删除验证码缓存和错误计数
        redisCache.deleteObject(tenantCacheKey(SMS_RESET_CODE_PREFIX, publicTenantScope, account));
        redisCache.deleteObject(tenantCacheKey(RETRY_KEY_PREFIX, publicTenantScope, account));
        redisCache.deleteObject(tenantCacheKey(SEND_COUNT_CODE_PREFIX, publicTenantScope, account));

        return Result.success("密码重置成功");
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result adminResetPassword(Long userId, String newPassword) {
        // 查询用户是否存在
        SysUser user = getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 获取当前管理员ID
        long adminId = StpUtil.getLoginIdAsLong();
        if (adminId == userId) {
            return Result.error("不能重置自己的密码，请使用修改密码功能");
        }

        // 权限检查：超级管理员可以重置所有人密码，管理员不能重置超级管理员和其他管理员密码
        List<String> targetRoleCodes = sysRoleMapper.selectRoleCodesByUserId(userId);
        if (StpUtil.hasRole("SUPER_ADMIN")) {
            // 超级管理员不能重置其他超级管理员的密码
            if (targetRoleCodes.contains("SUPER_ADMIN")) {
                return Result.error("无法重置其他超级管理员的密码");
            }
        } else if (StpUtil.hasPermission("system:user:reset-password")) {
            // 管理员不能重置超级管理员和其他管理员的密码
            if (targetRoleCodes.contains("SUPER_ADMIN") || targetRoleCodes.contains("ADMIN")) {
                return Result.error("无法重置超级管理员或其他管理员的密码");
            }
        } else {
            return Result.error("无权限重置用户密码");
        }

        String passwordToSet = randomPassword();
        user.setPassword(BCrypt.hashpw(passwordToSet));
        user.setMustChangePassword(1);
        user.setUpdateTime(LocalDateTime.now());

        boolean success = updateById(user);
        if (success) {
            // 强制该用户下线
            try {
                StpUtil.logout(userId);
            } catch (Exception e) {
                log.warn("用户{}当前未登录，无需强制下线", userId);
            }

            return Result.success("已生成一次性密码，用户首次登录后必须修改", passwordToSet);
        } else {
            return Result.error("密码重置失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result rechargeBalance(Long userId, Integer amount){
        SysUser targetUser = getById(userId);
        if (targetUser == null) {
            return Result.error("用户不存在");
        }
        if (TenantContext.isSuperAdmin() && TenantContext.getTenantIdOrNull() == null
                && targetUser.getTenantId() != null) {
            return TenantScope.call(targetUser.getTenantId(),
                    () -> rechargeBalanceInCurrentTenant(userId, amount));
        }
        return rechargeBalanceInCurrentTenant(userId, amount);
    }

    private Result rechargeBalanceInCurrentTenant(Long userId, Integer amount) {
        SysUser user = getById(userId);
        if (user == null) {
            return Result.error("用户不存在或不在当前租户");
        }

        // 计算新余额
        BigDecimal surplusMoney = user.getSurplusMoney();
        BigDecimal newBalance = NumberUtil.add(surplusMoney, amount);

        // 更新用户余额
        user.setSurplusMoney(newBalance);
        user.setUpdateTime(LocalDateTime.now());

        boolean success = updateById(user);
        if (success) {
            //记录余额变动详情
            UserSurplusRecord surplusRecord = new UserSurplusRecord();
            surplusRecord.setUserId(userId);
            surplusRecord.setOrderNo(null);
            surplusRecord.setAmount(BigDecimal.valueOf(amount));
            surplusRecord.setNewBalance(newBalance);
            surplusRecord.setOldBalance(surplusMoney);
            surplusRecord.setType(BalanceResourceEnum.getByCode(1));
            surplusRecord.setOperateUserId(StpUtil.getLoginIdAsLong());
            surplusRecordMapper.insert(surplusRecord);
            return Result.success("充值成功", surplusRecord);
        } else {
            return Result.error("充值失败");
        }
    }

    @Override
    public Result addUserPoints(Long userId, Integer points,String remark) {
        return addUserPoints(userId, points, PointsSourceEnum.OTHERS,remark);
    }
    @Override
    public Result addUserPoints(Long userId, Integer points, PointsSourceEnum sourceType) {
        return addUserPoints(userId, points,sourceType,null);
    }
    @Override
    public Result addUserPoints(Long userId, Integer points) {
        return addUserPoints(userId, points,PointsSourceEnum.OTHERS,null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result addUserPointsIdempotent(Long userId, Integer points, String idempotencyKey) {
        if (userId == null || points == null || points <= 0 || StrUtil.isBlank(idempotencyKey)) {
            return Result.error("参数错误");
        }
        UserPointsRecord existing = userPointsRecordMapper.selectOne(new LambdaQueryWrapper<UserPointsRecord>()
                .eq(UserPointsRecord::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"));
        if (existing != null) {
            return Result.success(existing.getAfterPoints());
        }
        Result result = addUserPoints(userId, points, PointsSourceEnum.ACTIVITY, idempotencyKey);
        if (result == null || !Result.CODE_SUCCESS.equals(result.getCode())) {
            return result == null ? Result.error("积分增加失败") : result;
        }
        userPointsRecordMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserPointsRecord>()
                .eq(UserPointsRecord::getRemark, idempotencyKey)
                .isNull(UserPointsRecord::getIdempotencyKey)
                .set(UserPointsRecord::getIdempotencyKey, idempotencyKey));
        return result;
    }

    private String clientIp() {
        org.springframework.web.context.request.ServletRequestAttributes attributes =
                (org.springframework.web.context.request.ServletRequestAttributes)
                        org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        String forwarded = attributes.getRequest().getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return attributes.getRequest().getRemoteAddr();
    }

    private String randomPassword() {
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    @Override
    public List<Long> getUserRoleIds(List<String> roleCodes) {
        LambdaQueryWrapper<SysRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(SysRole::getRoleCode, roleCodes);
        List<SysRole> roles = sysRoleMapper.selectList(queryWrapper);
        List<Long> roleIds = roles.stream().map(SysRole::getId).collect(Collectors.toList());
        return roleIds;
    }


    /*微信小程序*/
    @Transactional(rollbackFor = Exception.class)
    public Result wxLoginAndBindPhone(String code){
        SysTenant tenant;
        try {
            tenant = requireActiveTenant(TenantContext.getTenantIdOrNull());
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        //调用微信接口服务，获得当前微信用户的openid
        HashMap<String, String> map = new HashMap<>();
        String sessionKey = null;
        String openid = null;
        String jsonString = wxUtilS.wxLogin(code);
        try {
            //解析json，获得openid和sessionKey
            JSONObject jsonObject = JSON.parseObject(jsonString);
            openid = jsonObject.getString("openid");
            sessionKey = jsonObject.getString("session_key");
        }catch (Exception e){
            e.printStackTrace();
        }
        //判断openid是否为空，如果为空表示登录失败，抛出业务异常
        if(openid == null || sessionKey == null){
            return Result.error(401,"微信调用官方接口失败");
        }
        //判断当前用户是否为新用户
        Long isNewUser = sysUserMapper.existsUserByOpenId(tenant.getId(), openid);
        //如果是新用户，自动完成注册
        if(isNewUser == null){
            SysUser u = new SysUser();
            u.setUsername(openid);
            u.setNickname(openid);
            u.setOpenId(openid);
            u.setTenantId(tenant.getId());
            sysUserMapper.insert(u);
            // 为新用户分配默认角色（普通用户角色）
            assignDefaultUserRole(u);
            List<String> roleCodes = sysUserRoleMapper.getRoleCodesByUserId(u.getId());
            return finishCustomerLogin(sysUserMapper.selectById(u.getId()), roleCodes, tenant.getTenantCode());
        }else {
            List<String> roleCodesByUserId = sysUserRoleMapper.getRoleCodesByUserId(isNewUser);
            return finishCustomerLogin(sysUserMapper.selectById(isNewUser), roleCodesByUserId, tenant.getTenantCode());
        }
    }

    public Result wxGetPhoneNumber(String code){
        //若access_token在redis中存在，则从缓存中获取，不再次调用接口
        String accessToken = null;
        Object accessTokenForRedis = redisCache.getCacheObject("access_token");
        if(accessTokenForRedis!=null){
            accessToken = accessTokenForRedis.toString();
        }else{
            accessToken = wxUtilS.wxGetAccessToken();
        }
        String phoneNumber = wxUtilS.wxGetPhoneNumber(accessToken, code);
        SysUser sysUser = sysUserMapper.selectById(StpUtil.getLoginIdAsLong());
        sysUserMapper.updateById(sysUser.setPhone(phoneNumber));
        return Result.success(phoneNumber);
    }

    public Result getUserBasicInfo(){
        long userId = StpUtil.getLoginIdAsLong();
        // Only the authenticated platform identity bypasses the selected business tenant.
        SysUser sysUser = TenantContext.isSuperAdmin()
                ? TenantIgnore.get(() -> sysUserMapper.selectById(userId))
                : sysUserMapper.selectById(userId);
        if (sysUser == null) {
            return Result.error(401, "登录账号已不存在，请重新登录");
        }
        SysUserVO sysUserVO = BeanCopyUtils.copyBean(sysUser, SysUserVO.class);
        sysUserVO.setRoleCode(TenantContext.isSuperAdmin()
                ? TenantIgnore.get(() -> sysUserRoleMapper.getRoleCodesByUserId(userId))
                : sysUserRoleMapper.getRoleCodesByUserId(userId));
        sysUserVO.setCoupons(sysUser.getTenantId() == null ? 0L : getUserCouponCount(userId));
        Long currentTenantId = TenantContext.getTenantIdOrNull();
        sysUserVO.setCurrentTenant(currentTenantId == null ? null : sysTenantService.getById(currentTenantId));
        UserLevel level = sysUser.getLevel() == null ? null : userLevelMapper.getLevelByLevelCode(sysUser.getLevel());
        sysUserVO.setLevelName(level == null ? null : level.getLevelName());
        sysUserVO.setStoreIds(sysUser.getTenantId() == null ? List.of() : sysUserStoreService.getStoreIds(userId));
        SaSession currentSession = StpUtil.getSession();
        Object activeStoreId = currentSession == null ? null
                : currentSession.get(TenantConstants.SESSION_ACTIVE_STORE_ID);
        sysUserVO.setActiveStoreId(activeStoreId == null ? null : Long.valueOf(activeStoreId.toString()));
        if (sysUser.getTenantId() != null) {
            SysTenant tenant = sysTenantService.getById(sysUser.getTenantId());
            sysUserVO.setTenantCode(tenant == null ? null : tenant.getTenantCode());
        }

        return Result.success(sysUserVO);
    }

    /** 普通用户端通过 X-Public-Tenant-Id 建立的请求上下文定位商户。 */
    private SysTenant requireActiveTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("无法识别当前商户，请从正确的商户入口进入");
        }
        SysTenant tenant = TenantIgnore.get(() -> sysTenantService.getById(tenantId));
        if (tenant == null) {
            throw new IllegalArgumentException("商户不存在");
        }
        if (!Objects.equals(tenant.getStatus(), 1)) {
            throw new IllegalArgumentException("商户已停用");
        }
        if (tenant.getExpireTime() != null && tenant.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("商户服务已到期");
        }
        return tenant;
    }

    private boolean isTenantActive(SysTenant tenant) {
        return tenant != null
                && Objects.equals(tenant.getStatus(), 1)
                && (tenant.getExpireTime() == null
                || !tenant.getExpireTime().isBefore(LocalDateTime.now()))
                && TenantScope.call(tenant.getId(),
                () -> subscriptions.hasEffectiveSubscription(tenant.getId()));
    }

    private SysUser findLoginUser(Long tenantId, String account) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getTenantId, tenantId);
        applyLoginAccountCondition(wrapper, account);
        return sysUserMapper.selectOne(wrapper);
    }

    /** 按账号格式选择唯一字段，避免一个人的用户名误命中另一个人的手机号或邮箱。 */
    private void applyLoginAccountCondition(LambdaQueryWrapper<SysUser> wrapper, String account) {
        if (hongTuUtil.isEmail(account)) {
            wrapper.eq(SysUser::getEmail, account);
        } else if (hongTuUtil.isPhone(account)) {
            wrapper.eq(SysUser::getPhone, account);
        } else {
            wrapper.eq(SysUser::getUsername, account);
        }
    }

    private Result finishCustomerLogin(SysUser user, List<String> roleCodes, String tenantCode) {
        adminLoginSecurity.requireCustomerLoginAllowed(roleCodes);
        return finishLogin(user, roleCodes, tenantCode);
    }

    private Result finishLogin(SysUser user, List<String> roleCodes, String tenantCode) {
        return finishLogin(user, roleCodes, tenantCode, null, Map.of());
    }

    private Result finishLogin(SysUser user, List<String> roleCodes, String tenantCode,
                               Long requestedStoreId, Map<Long, Long> workspaceUsers) {
        if (user.getTenantId() != null) {
            return TenantScope.call(user.getTenantId(),
                    () -> finishLoginInCurrentScope(user, roleCodes, tenantCode,
                            requestedStoreId, workspaceUsers));
        }
        return finishLoginInCurrentScope(user, roleCodes, tenantCode,
                requestedStoreId, workspaceUsers);
    }

    private Result finishLoginInCurrentScope(SysUser user, List<String> roleCodes, String tenantCode,
                                             Long requestedStoreId, Map<Long, Long> workspaceUsers) {
        List<String> safeRoleCodes = roleCodes == null ? List.of() : roleCodes;
        boolean superAdmin = safeRoleCodes.contains(TenantConstants.ROLE_SUPER_ADMIN);
        if (superAdmin && user.getTenantId() != null) {
            return Result.error("平台超级管理员不能归属于租户");
        }
        if (!superAdmin && user.getTenantId() == null) {
            return Result.error("非超级管理员必须归属于租户");
        }

        StoreScope storeScope;
        try {
            storeScope = resolveStoreScope(user, safeRoleCodes, requestedStoreId);
        } catch (IllegalArgumentException error) {
            return Result.error(error.getMessage());
        }
        String token = StpUtil.getStpLogic().createLoginSession(user.getId());
        SaSession session = StpUtil.getSessionByLoginId(user.getId());
        configureWorkspaceSession(session, user, superAdmin, storeScope, workspaceUsers);

        SysUserVO vo = BeanCopyUtils.copyBean(user, SysUserVO.class);
        vo.setRoleCode(safeRoleCodes);
        vo.setToken(token);
        vo.setTenantCode(tenantCode);
        vo.setStoreIds(storeScope.authorizedStoreIds());
        vo.setActiveStoreId(storeScope.activeStoreId());
        vo.setCurrentTenant(user.getTenantId() == null ? null : sysTenantService.getById(user.getTenantId()));
        UserLevel level = user.getLevel() == null || user.getTenantId() == null ? null :
                TenantScope.call(user.getTenantId(), () -> userLevelMapper.getLevelByLevelCode(user.getLevel()));
        vo.setLevelName(level == null ? null : level.getLevelName());
        vo.setCoupons(user.getTenantId() == null ? 0L : getUserCouponCount(user.getId()));
        return Result.success(vo);
    }

    private Result refreshCurrentWorkspace(SysUser user, List<String> roleCodes, SysTenant tenant,
                                           Long requestedStoreId, Map<Long, Long> workspaceUsers) {
        StoreScope storeScope;
        try {
            storeScope = resolveStoreScope(user, roleCodes, requestedStoreId);
        } catch (IllegalArgumentException error) {
            return Result.error(error.getMessage());
        }
        configureWorkspaceSession(StpUtil.getSession(), user, false, storeScope, workspaceUsers);
        SysUserVO vo = BeanCopyUtils.copyBean(user, SysUserVO.class);
        vo.setRoleCode(roleCodes);
        vo.setToken(StpUtil.getTokenValue());
        vo.setTenantCode(tenant.getTenantCode());
        vo.setCurrentTenant(tenant);
        vo.setStoreIds(storeScope.authorizedStoreIds());
        vo.setActiveStoreId(storeScope.activeStoreId());
        return Result.success(vo);
    }

    private void configureWorkspaceSession(SaSession session, SysUser user, boolean superAdmin,
                                           StoreScope storeScope, Map<Long, Long> workspaceUsers) {
        if (user.getTenantId() == null) session.delete(TenantConstants.SESSION_TENANT_ID);
        else session.set(TenantConstants.SESSION_TENANT_ID, user.getTenantId());
        session.set(TenantConstants.SESSION_SUPER_ADMIN, superAdmin);
        session.set(TenantConstants.SESSION_STORE_RESTRICTED, storeScope.restricted());
        session.set(TenantConstants.SESSION_AUTHORIZED_STORE_IDS, storeScope.authorizedStoreIds());
        session.set(TenantConstants.SESSION_STORE_IDS,
                storeScope.activeStoreId() == null ? List.of() : List.of(storeScope.activeStoreId()));
        if (storeScope.activeStoreId() == null) session.delete(TenantConstants.SESSION_ACTIVE_STORE_ID);
        else session.set(TenantConstants.SESSION_ACTIVE_STORE_ID, storeScope.activeStoreId());
        if (workspaceUsers != null && !workspaceUsers.isEmpty()) {
            session.set(TenantConstants.SESSION_WORKSPACE_USERS, JSON.toJSONString(workspaceUsers));
        }
    }

    private StoreScope resolveStoreScope(SysUser user, List<String> roleCodes, Long requestedStoreId) {
        if (user.getTenantId() == null) return new StoreScope(false, List.of(), null);
        List<Long> storeIds = sysUserStoreService.getStoreIds(user.getId());
        List<Long> authorized = storeIds == null ? List.of() : storeIds.stream().distinct().toList();
        boolean restricted = isStoreRestricted(roleCodes);
        if (!restricted) return new StoreScope(false, authorized, null);
        if (authorized.isEmpty()) {
            throw new IllegalArgumentException("当前账号尚未分配门店，请联系商户管理员");
        }
        Long activeStoreId = requestedStoreId;
        if (activeStoreId == null && authorized.size() == 1) activeStoreId = authorized.getFirst();
        if (activeStoreId == null) throw new IllegalArgumentException("请选择本次要管理的门店");
        if (!authorized.contains(activeStoreId)) throw new IllegalArgumentException("无权进入所选门店");
        return new StoreScope(true, authorized, activeStoreId);
    }

    private boolean isStoreRestricted(List<String> roleCodes) {
        List<String> safe = roleCodes == null ? List.of() : roleCodes;
        return !safe.contains(TenantConstants.ROLE_SUPER_ADMIN)
                && !safe.contains(TenantConstants.ROLE_ADMIN)
                && safe.stream().anyMatch(code -> !"USER".equals(code));
    }

    private Map<Long, Long> currentWorkspaceUsers() {
        Object raw = StpUtil.getSession().get(TenantConstants.SESSION_WORKSPACE_USERS);
        Map<Long, Long> result = new LinkedHashMap<>();
        if (raw != null && StrUtil.isNotBlank(raw.toString())) {
            JSONObject object = JSON.parseObject(raw.toString());
            object.forEach((key, value) -> result.put(Long.valueOf(key), Long.valueOf(value.toString())));
        }
        if (result.isEmpty() && TenantContext.getTenantIdOrNull() != null) {
            result.put(TenantContext.getTenantIdOrNull(), StpUtil.getLoginIdAsLong());
        }
        return result;
    }

    private AdminTenantOptionVO buildWorkspaceOption(Long tenantId, Long userId) {
        SysTenant tenant = TenantIgnore.get(() -> sysTenantService.getById(tenantId));
        SysUser user = TenantIgnore.get(() -> sysUserMapper.selectById(userId));
        if (!isTenantActive(tenant) || user == null || user.getStatus() == StatusEnum.DISABLED
                || !Objects.equals(user.getTenantId(), tenantId)) return null;
        List<String> roles = TenantIgnore.get(() -> sysUserRoleMapper.getRoleCodesByUserId(userId));
        roles = roles == null ? List.of() : roles;
        if (roles.contains(TenantConstants.ROLE_SUPER_ADMIN)
                || roles.stream().noneMatch(code -> !"USER".equals(code))) return null;
        List<AdminStoreOptionVO> stores = isStoreRestricted(roles)
                ? loadStoreOptions(tenantId, userId) : List.of();
        return new AdminTenantOptionVO(tenantId, tenant.getTenantCode(), tenant.getTenantName(),
                roles, stores, isStoreRestricted(roles) && stores.size() != 1);
    }

    private List<AdminStoreOptionVO> loadStoreOptions(Long tenantId, Long userId) {
        if (sysUserStoreService == null) return List.of();
        return TenantScope.call(tenantId, () -> {
            boolean previousRestricted = StoreContext.isRestricted();
            List<Long> previousStoreIds = StoreContext.getStoreIds();
            StoreContext.setRestricted(false, List.of());
            try {
                List<SysUserStore> links = sysUserStoreService.list(new LambdaQueryWrapper<SysUserStore>()
                        .eq(SysUserStore::getUserId, userId)
                        .orderByDesc(SysUserStore::getIsDefault)
                        .orderByAsc(SysUserStore::getStoreId));
                if (links == null || links.isEmpty()) return List.of();
                Map<Long, String> names = new HashMap<>();
                if (restaurantFeignClient != null) {
                    try {
                        List<StoreSummaryDTO> summaries = org.hongtu.api.util.FeignResultUtil.unwrap(
                                restaurantFeignClient.getStoreOptions(
                                        links.stream().map(SysUserStore::getStoreId).toList()));
                        if (summaries != null) {
                            summaries.forEach(store -> names.put(store.getStoreId(), store.getStoreName()));
                        }
                    } catch (RuntimeException error) {
                        log.warn("读取租户 {} 的门店名称失败，使用门店编号兜底", tenantId, error);
                    }
                }
                return links.stream().map(link -> new AdminStoreOptionVO(
                        link.getStoreId(), names.getOrDefault(link.getStoreId(), "门店 " + link.getStoreId()),
                        Integer.valueOf(1).equals(link.getIsDefault()))).toList();
            } finally {
                StoreContext.setRestricted(previousRestricted, previousStoreIds);
            }
        });
    }

    private record StoreScope(boolean restricted, List<Long> authorizedStoreIds, Long activeStoreId) {
    }

    private void assignDefaultUserRole(SysUser user) {
        if (user.getTenantId() == null) {
            throw new IllegalArgumentException("租户用户缺少tenantId");
        }
        UserLevel baseline = userLevelMapper.getLevelByExp(user.getExp() == null ? 0 : user.getExp());
        if (baseline == null) throw new org.hongtu.exception.BusinessException(400,"当前租户没有有效的会员等级方案");
        user.setLevel(baseline.getLevelCode());
        sysUserMapper.updateById(user);
        SysRole defaultRole = sysRoleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getTenantId, user.getTenantId())
                .eq(SysRole::getRoleCode, "USER"));
        if (defaultRole == null) {
            throw new IllegalStateException("当前租户缺少USER角色");
        }
        SysUserRole userRole = new SysUserRole()
                .setTenantId(user.getTenantId())
                .setUserId(user.getId())
                .setRoleId(defaultRole.getId());
        sysUserRoleMapper.insert(userRole);
    }

    private String tenantCacheKey(String prefix, String tenantScope, String account) {
        String scope = StrUtil.blankToDefault(tenantScope, "PLATFORM").toUpperCase(Locale.ROOT);
        return prefix + scope + ":" + account;
    }

    // 获取剩余可尝试次数
    public int getRemainingRetryCount(String tenantScope, String account) {
        if (StrUtil.isBlank(account)) {
            return MAX_RETRY_LIMIT;
        }
        String retryKey = tenantCacheKey(RETRY_KEY_PREFIX, tenantScope, account);
        try {
            Integer retryCount = redisCache.getCacheObject(retryKey);
            if (retryCount == null) {
                return MAX_RETRY_LIMIT;
            }
            return Math.max(0, MAX_RETRY_LIMIT - retryCount);
        } catch (Exception e) {
            log.error("无法获取最大次数: {}", retryKey, e);
            return MAX_RETRY_LIMIT;
        }
    }

    /**
     * 验证验证码（提取的公共方法）
     * @param account 账号（邮箱或手机号）
     * @param verifyCode 验证码
     * @param codePrefix 验证码前缀
     * @return Result 验证结果
     */
    private Result verifyCode(String tenantScope, String account, String verifyCode, String codePrefix) {
        // 1. 验证码错误次数检查
        String errorCountKey = tenantCacheKey(RETRY_KEY_PREFIX, tenantScope, account);
        Integer errorCount = redisCache.getCacheObject(errorCountKey);
        if (errorCount != null && errorCount >= MAX_RETRY_LIMIT) {
            long expire = redisCache.getExpire(errorCountKey, TimeUnit.HOURS);
            return Result.error("验证码错误次数过多，请" + expire + "小时后再试或重新获取验证码");
        }

        // 2. 获取缓存的验证码
        String cacheKey = tenantCacheKey(codePrefix, tenantScope, account);
        String encryptedCode = redisCache.getCacheObject(cacheKey);
        if (StrUtil.isBlank(encryptedCode)) {
            incrementVerifyCodeErrorCount(tenantScope, account);
            return Result.error("验证码已过期，请重新获取");
        }

        // 3. 使用 BCrypt 验证加密的验证码
        if (!BCrypt.checkpw(verifyCode, encryptedCode)) {
            incrementVerifyCodeErrorCount(tenantScope, account);
            return Result.error("验证码错误，您还有" + getRemainingRetryCount(tenantScope, account) + "次尝试机会");
        }

        // 4. 验证成功，删除验证码缓存和错误计数
        redisCache.deleteObject(cacheKey);
        redisCache.deleteObject(errorCountKey);

        return Result.success("验证码校验成功");
    }

    /**
     * 递增验证码错误次数（使用Redis原子性操作避免并发问题）
     * @param account 账号（邮箱或手机号）
     * @return 当前错误次数
     */
    private Integer incrementVerifyCodeErrorCount(String tenantScope, String account) {
        String errorCountKey = tenantCacheKey(RETRY_KEY_PREFIX, tenantScope, account);
        // 使用Redis的INCR命令，保证原子性
        Long errorCount = redisCache.redisTemplate.opsForValue().increment(errorCountKey);
        if (errorCount != null && errorCount == 1) {
            // 首次错误，设置过期时间
            redisCache.expire(errorCountKey, 12 * 3600, TimeUnit.SECONDS);
        }
        return errorCount != null ? errorCount.intValue() : 1;
    }

    /** 头像关联存在时用资源 ID 派生地址；旧客户端未修改头像则保留原关联。 */
    private ResourceBindingPlan normalizeAvatar(SysUser target, SysUser existing) {
        if (target.getAvatar() == null) target.setAvatar(existing.getAvatar());
        if (target.getAvatarResourceId() == null && Objects.equals(target.getAvatar(), existing.getAvatar()))
            target.setAvatarResourceId(resourceBindings.references(existing.getTenantId() == null ? 0L : existing.getTenantId(),
                    "USER", existing.getId()).stream().findFirst().map(ResourceBinding::resourceId).orElse(null));
        Long tenantId = existing.getTenantId() == null ? 0L : existing.getTenantId();
        ResourceBindingPlan plan = resourceBindings.prepare(new org.hongtu.api.dto.ResourceBindingRequest(
                tenantId, "USER", existing.getId(), List.of("avatar"),
                List.of(new ResourceBinding("avatar", 0, target.getAvatarResourceId(), target.getAvatar()))));
        target.setAvatar(resourceBindings.resolvedSingle(plan, target.getAvatar()));
        return plan;
    }
}
