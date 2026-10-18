package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.hongtu.DTO.SysUser;
import org.hongtu.DTO.UserLevel;
import org.hongtu.DAO.UserLevelMapper;
import org.hongtu.Result.Result;
import org.hongtu.VO.CashierMemberVO;
import org.hongtu.enums.StatusEnum;
import org.hongtu.service.SysUserService;
import org.hongtu.utils.IpUtil;
import org.hongtu.utils.hongTuUtil;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 *用户
 */
@RestController
@RequestMapping("/user")
public class SysUserController {

    @Resource
    SysUserService sysUserService;
    @Resource
    org.hongtu.service.AdminLoginSecurity adminLoginSecurity;

    @GetMapping("/admin/captcha")
    @SaIgnore
    public Result<?> adminCaptcha(HttpServletRequest request) {
        return Result.success(adminLoginSecurity.captcha(request.getRemoteAddr()));
    }
    @Resource
    IpUtil ipUtil;
    /**
     * 用户登录。商户由 X-Public-Tenant-Id 请求头确定；开启图片验证码时先完成一次性校验。
     */
    @PostMapping("/Login")
    @SaIgnore
    public Result login(@RequestParam("account") String account,
                        @RequestParam("password") String password,
                        @RequestParam(required = false) String captchaId,
                        @RequestParam(required = false) String captchaCode){
        if(StrUtil.isBlank(account) ||StrUtil.isBlank(password)){
            return Result.error("用户名/邮箱/手机号不能为空");
        }
        if(hongTuUtil.containsChinese(account)){
            return Result.error("用户名不能包含中文");
        }
        if(StrUtil.isBlank(password)){
            return Result.error("密码不能为空");
        }
        adminLoginSecurity.verifyCustomerCaptcha(captchaId, captchaCode);
        return sysUserService.login(account,password);
    }

    /**
     * 管理端账号密码预认证，不接收 tenantCode；租户账号验证后进入商户选择阶段。
     */
    @PostMapping("/admin/Login")
    @SaIgnore
    public Result adminLogin(@RequestParam("account") String account,
                             @RequestParam("password") String password,
                             @RequestParam(required = false) String captchaId,
                             @RequestParam(required = false) String captchaCode) {
        if (StrUtil.isBlank(account) || StrUtil.isBlank(password)) {
            return Result.error("用户名/邮箱/手机号不能为空");
        }
        if (hongTuUtil.containsChinese(account)) {
            return Result.error("用户名不能包含中文");
        }
        return adminLoginSecurity.authenticate(account, captchaId, captchaCode,
                () -> sysUserService.adminLogin(account, password));
    }

    /**
     * 管理端动态口令二次验证，验证成功后继续商户选择或签发正式 Token。
     */
    @PostMapping("/admin/verify-totp")
    @SaIgnore
    public Result verifyAdminTotp(@RequestParam("challengeTicket") String challengeTicket,
                                  @RequestParam("totpCode") String totpCode) {
        if (StrUtil.isBlank(challengeTicket)) {
            return Result.error("二次验证凭证不能为空");
        }
        if (StrUtil.isBlank(totpCode) || !totpCode.matches("\\d{6}")) {
            return Result.error("请输入 6 位动态口令");
        }
        return sysUserService.verifyAdminTotp(challengeTicket, totpCode);
    }

    /**
     * 管理端第二阶段登录：使用预认证返回的一次性凭证选择商户，成功后签发正式 Token。
     */
    @PostMapping("/admin/select-tenant")
    @SaIgnore
    public Result selectAdminTenant(@RequestParam("selectionTicket") String selectionTicket,
                                    @RequestParam("tenantId") Long tenantId,
                                    @RequestParam(value = "storeId", required = false) Long storeId) {
        if (StrUtil.isBlank(selectionTicket) || tenantId == null || tenantId <= 0) {
            return Result.error("登录凭证和商户不能为空");
        }
        return sysUserService.selectAdminTenant(selectionTicket, tenantId, storeId);
    }

    /** 返回本次身份验证允许进入的全部租户及门店工作空间。 */
    @GetMapping("/admin/workspaces")
    public Result adminWorkspaces() {
        return sysUserService.listAdminWorkspaces();
    }

    /** 不退出账号，切换到已授权的租户或门店。跨租户时返回新的正式 Token。 */
    @PostMapping("/admin/switch-workspace")
    public Result switchAdminWorkspace(@RequestParam("tenantId") Long tenantId,
                                       @RequestParam(value = "storeId", required = false) Long storeId) {
        if (tenantId == null || tenantId <= 0) {
            return Result.error("商户不能为空");
        }
        return sysUserService.switchAdminWorkspace(tenantId, storeId);
    }

    /**
     * 登出
     * */
    @PostMapping("/logout")
    public Result logout(){
        StpUtil.logout();
        return Result.success("登出成功");
    }

    /**
     * 手机/邮箱验证码自动登入注册
     * */
    @PostMapping("/phoneOrEmailVerifyCodeLogin")
    @SaIgnore
    public Result phoneOrEmailVerifyCodeLogin(@RequestParam String account,@RequestParam String verifyCode){
        if(StrUtil.isBlank(account)){
            return Result.error("请将信息补充完整");
        }
        return sysUserService.phoneOrEmailVerifyCodeLogin(account,verifyCode);
    }

    /**
     * 管理员手机/邮箱验证码登入（仅登录，不自动注册）
     */
    @PostMapping("/admin/phoneOrEmailVerifyCodeLogin")
    @SaIgnore
    public Result adminPhoneOrEmailVerifyCodeLogin(
            @RequestParam String account, @RequestParam String verifyCode,
            @RequestParam(required = false) String captchaId,
            @RequestParam(required = false) String captchaCode) {
        if (StrUtil.isBlank(account)) {
            return Result.error("请将信息补充完整");
        }
        if (StrUtil.isBlank(verifyCode)) {
            return Result.error("验证码不能为空");
        }
        if (!hongTuUtil.isPhone(account) && !hongTuUtil.isEmail(account)) {
            return Result.error("请输入正确的手机号或邮箱");
        }
        return adminLoginSecurity.authenticate(account, captchaId, captchaCode,
                () -> sysUserService.adminPhoneOrEmailVerifyCodeLogin(account, verifyCode));
    }

    /**
     * 获取用户基本信息
     * */
    @GetMapping("/getUserBasicInfo")
    public Result getUserBasicInfo(){
        return sysUserService.getUserBasicInfo();
    }

    /**
     * 用户更新用户个人信息
     * */
    @PostMapping("/updateUserInfo")
    public Result updateUserInfo(@RequestBody SysUser sysUser){
        long loginId = StpUtil.getLoginIdAsLong();
        if (sysUser.getId() == null || sysUser.getId() != loginId) {
            return Result.error(401,"您无权限更新其它用户的信息");
        }
        return sysUserService.updateUserInfo(sysUser);
    }

    /**
     * 用户通过验证码重置密码（支持手机号和邮箱）
     * @param account 账号（手机号或邮箱）
     * @param verifyCode 验证码
     * @param newPassword 新密码
     * @return 重置结果
     */
    @PostMapping("/resetPassword")
    @SaIgnore
    public Result resetPassword(@RequestParam String account,
                                @RequestParam String verifyCode,
                                @RequestParam(required = false) String newPassword) {
        if (StrUtil.isBlank(account)) {
            return Result.error("账号不能为空");
        }
        if (StrUtil.isBlank(verifyCode)) {
            return Result.error("验证码不能为空");
        }
        return sysUserService.resetPassword(account, verifyCode, newPassword);
    }

    @Resource
    private org.hongtu.DAO.SysUserMapper recipientMapper;
    @Resource
    private UserLevelMapper cashierLevelMapper;

    /** 收银台按手机号精确加载会员，只返回结算所需字段。 */
    @GetMapping("/cashier/member")
    @SaCheckPermission(value = {"operations:direct-cashier", "operations:table-cashier", "restaurant:order:settle"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<CashierMemberVO> cashierMember(@RequestParam String phone) {
        String normalizedPhone = phone == null ? "" : phone.trim();
        if (!normalizedPhone.matches("^1\\d{10}$")) {
            return Result.error("请输入正确的11位手机号");
        }
        SysUser user = recipientMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getNickname, SysUser::getPhone,
                        SysUser::getAvatar, SysUser::getLevel, SysUser::getPoints, SysUser::getSurplusMoney,
                        SysUser::getStatus)
                .eq(SysUser::getPhone, normalizedPhone)
                .last("LIMIT 1"));
        if (user == null) {
            return Result.error("未找到该手机号对应的会员");
        }
        if (user.getStatus() == StatusEnum.DISABLED) {
            return Result.error("该会员已停用，无法用于收银");
        }
        return Result.success(toCashierMember(user));
    }

    /** 按首笔订单的会员ID恢复桌位收银会员信息。 */
    @GetMapping("/cashier/memberById")
    @SaCheckPermission(value = {"operations:direct-cashier", "operations:table-cashier", "restaurant:order:settle"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<CashierMemberVO> cashierMemberById(@RequestParam Long userId) {
        SysUser user = recipientMapper.selectById(userId);
        if (user == null || user.getStatus() == StatusEnum.DISABLED) {
            return Result.error("首笔订单会员不存在或已停用");
        }
        return Result.success(toCashierMember(user));
    }

    private CashierMemberVO toCashierMember(SysUser user) {
        CashierMemberVO result = new CashierMemberVO();
        result.setId(user.getId());
        result.setUsername(user.getUsername());
        result.setNickname(user.getNickname());
        result.setPhone(user.getPhone());
        result.setAvatar(user.getAvatar());
        result.setLevel(user.getLevel());
        UserLevel level = user.getLevel() == null ? null
                : cashierLevelMapper.getLevelByLevelCode(user.getLevel());
        result.setLevelName(level == null ? null : level.getLevelName());
        result.setPoints(user.getPoints());
        result.setSurplusMoney(user.getSurplusMoney());
        return result;
    }

    @GetMapping("/recipients")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"marketing:coupon", "system:user", "system:user-store", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result recipients(@RequestParam(defaultValue = "") String keyWord,
                             @RequestParam(defaultValue = "1") int currentPage,
                             @RequestParam(defaultValue = "30") int pageSize) {
        org.hongtu.tenant.TenantContext.getTenantId();
        var query = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getNickname);
        if (StrUtil.isNotBlank(keyWord)) {
            query.and(w -> w.like(SysUser::getUsername, keyWord).or().like(SysUser::getNickname, keyWord)
                    .or().eq(SysUser::getPhone, keyWord)
                    .or().apply("CAST(id AS CHAR) = {0}", keyWord));
        }
        return Result.success(recipientMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                Math.max(1, currentPage), Math.min(100, Math.max(1, pageSize))), query.orderByDesc(SysUser::getId)));
    }

    /*管理员接口*/

    /**
     * 管理员编辑用户信息
     */
    @PutMapping("/admin/edit")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result adminEditUser(@RequestBody SysUser sysUser) {
        if (sysUser == null || sysUser.getId() == null) {
            return Result.error("用户ID不能为空");
        }
        return sysUserService.adminEditUser(sysUser);
    }

    /**
     * 管理员重置用户密码
     * @param userId 用户ID
     * @param newPassword 管理员设置的新临时密码
     * @return 重置结果
     */
    @PostMapping("/admin/resetPassword")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user:reset-password"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result adminResetPassword(@RequestParam Long userId,
                                     @RequestParam String newPassword) {
        if (userId == null) {
            return Result.error("用户ID不能为空");
        }
        return sysUserService.adminResetPassword(userId, newPassword);
    }

    /**
     * 分页查询用户列表
     */
    @GetMapping("/list")
    @SaCheckPermission(value = {"system:user"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result listUsers(@RequestParam(defaultValue = "1") Integer currentPage,
                            @RequestParam(defaultValue = "10") Integer pageSize,
                            @RequestParam(required = false) String keyWord,
                            @RequestParam(required = false) Integer level
    ) {
        return sysUserService.listUsers(currentPage, pageSize, keyWord,level);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result deleteUser(@PathVariable Long id) {
        if (id == null) {
            return Result.error("用户ID不能为空");
        }
        return sysUserService.deleteUser(id);
    }

    /**
     * 添加用户
     */
    @PostMapping("/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result addUser(@RequestBody SysUser sysUser) {
        if (sysUser == null) {
            return Result.error("用户信息不能为空");
        }
        if (StrUtil.isBlank(sysUser.getUsername())) {
            return Result.error("用户名不能为空");
        }
        return sysUserService.addUser(sysUser);
    }

    /**
     * 管理员为用户充值余额
     */
    @PostMapping("/admin/recharge")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:user:recharge"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result rechargeBalance(@RequestParam Long userId, @RequestParam Integer amount) {
        if (userId == null) {
            return Result.error("用户ID不能为空");
        }
        if (amount == null || amount <= 0) {
            return Result.error("充值金额必须大于0");
        }
        return sysUserService.rechargeBalance(userId, amount);
    }


    /*微信小程序接口*/

    /**
     * 微信小程序登入
     */
    @SaIgnore
    @PostMapping("/wxLogin")
    public Result wxLoginAndBindPhone(@RequestParam String code) {
        //微信登录
        return sysUserService.wxLoginAndBindPhone(code);
    }

    /**
     * 微信小程序获取手机号
     * */
    @PostMapping("/wxGetPhoneNumber")
    public Result wxGetPhoneNumber(@RequestParam String code){

        return sysUserService.wxGetPhoneNumber(code);
    }

}
