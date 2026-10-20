package org.hongtu.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.CouponMapper;
import org.hongtu.DAO.CouponStackPairMapper;
import org.hongtu.DAO.UserCouponMapper;
import org.hongtu.DAO.VerifyRecordMapper;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.DTO.*;
import org.hongtu.Result.Result;
import org.hongtu.VO.UserCouponVO;
import org.hongtu.enums.*;
import org.hongtu.exception.BusinessException;
import org.hongtu.service.CouponDishItemService;
import org.hongtu.service.UserCouponService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户优惠券关联表 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Slf4j
@Service
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements UserCouponService {

    @Resource
    CouponMapper couponMapper;
    @Resource
    UserCouponMapper userCouponMapper;
    @Resource
    UserFeignClient userFeignClient;
    @Resource
    CouponDishItemService couponDishItemService;
    @Resource
    CouponStackPairMapper stackPairMapper;
    @Resource
    VerifyRecordMapper verifyRecordMapper;

    // ==================== 用户优惠券查询 ====================

    /**
     * 分页获取用户优惠券列表
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param searchKey 搜索关键字（"即将到期"/"最近领取"）
     * @param userId 用户ID
     * @param status 优惠券状态：0-未生效，1-未使用，2-已使用，3-已过期，null-全部
     * @return 用户优惠券分页列表
     */
    @Override
    public Result getUserCoupons(Integer currentPage, Integer pageSize, String searchKey, Long userId, CouponStatusEnum status) {
        // 创建分页对象
        Page<UserCoupon> page = new Page<>(currentPage, pageSize);
        // 创建查询条件
        LambdaQueryWrapper<UserCoupon> lambdaQuery = new LambdaQueryWrapper<>();
        // 只查询当前用户的优惠券
        lambdaQuery.eq(UserCoupon::getUserId, userId);
        // 更新优惠券状态（如果已过期但状态未更新）
        LocalDateTime now = LocalDateTime.now();
        // 如果传入了状态参数，则按状态筛选
        if (status != null) {
            // 如果查询已失效的优惠券（包括已使用和已过期）
            if (status == CouponStatusEnum.USED || status == CouponStatusEnum.OUT_OF_DATE) {
                lambdaQuery.in(UserCoupon::getStatus, 2, 3);
            } else {
                lambdaQuery.eq(UserCoupon::getStatus, status);
            }
        }
        // 根据搜索关键字排序
        if (StringUtils.hasText(searchKey)) {
            switch (searchKey) {
                case "即将到期" -> lambdaQuery.orderByAsc(UserCoupon::getExpireTime);
                case "最近领取" -> lambdaQuery.orderByDesc(UserCoupon::getObtainTime);
            }
        }
        // 执行分页查询
        Page<UserCoupon> userCouponPage = userCouponMapper.selectPage(page, lambdaQuery);
        // 创建返回的VO分页对象
        Page<UserCouponVO> voPage = new Page<>();
        // 复制分页信息
        BeanUtils.copyProperties(userCouponPage, voPage, "records");
        // 转换记录为VO对象
        List<UserCouponVO> voList = new ArrayList<>();
        if (userCouponPage.getRecords() != null && !userCouponPage.getRecords().isEmpty()) {
            // 获取所有优惠券ID
            List<Long> couponIds = userCouponPage.getRecords().stream()
                    .map(UserCoupon::getCouponId)
                    .collect(Collectors.toList());
            // 批量查询优惠券信息
            LambdaQueryWrapper<Coupon> couponQuery = new LambdaQueryWrapper<>();
            couponQuery.in(Coupon::getId, couponIds);
            List<Coupon> coupons = couponMapper.selectList(couponQuery);
            // 创建优惠券ID到优惠券对象的映射，方便后续查找
            Map<Long, Coupon> couponMap = coupons.stream()
                    .collect(Collectors.toMap(Coupon::getId, coupon -> coupon));
            // 一页内批量解析配对券名称，避免顾客端只看到内部 ID。
            Map<Long, List<Long>> stackPartners = new HashMap<>();
            Set<Long> partnerIds = new HashSet<>();
            Long tenantId = org.hongtu.tenant.TenantContext.getTenantId();
            for (Coupon coupon : coupons) {
                List<Long> ids = stackPairMapper.listPartnerIds(tenantId, coupon.getId());
                stackPartners.put(coupon.getId(), ids);
                partnerIds.addAll(ids);
            }
            Map<Long, String> partnerNames = partnerIds.isEmpty() ? Map.of()
                    : couponMapper.selectByIds(partnerIds).stream()
                    .collect(Collectors.toMap(Coupon::getId, Coupon::getName));
            // 转换为VO对象
            for (UserCoupon userCoupon : userCouponPage.getRecords()) {
                UserCouponVO vo = new UserCouponVO();
                // 复制用户优惠券基本信息
                vo.setId(userCoupon.getId());
                vo.setCouponId(userCoupon.getCouponId());
                vo.setCode(userCoupon.getCode());
                vo.setStartTime(userCoupon.getStartTime());
                vo.setExpireTime(userCoupon.getExpireTime());
                // 实时计算状态（不更新数据库，状态更新由定时任务统一处理）
                CouponStatusEnum displayStatus = calculateCouponStatus(userCoupon, now);
                vo.setStatus(displayStatus);
                // 获取关联的优惠券信息
                Coupon coupon = couponMap.get(userCoupon.getCouponId());
                if (coupon != null) {
                    vo.setName(coupon.getName());
                    vo.setDescription(coupon.getDescription());
                    vo.setType(coupon.getType());
                    vo.setValue(coupon.getValue());
                    vo.setMinAmount(coupon.getMinAmount());
                    vo.setStoreIds(coupon.getStoreIds());
                    vo.setUseWeekdays(coupon.getUseWeekdays());
                    vo.setUseStartTime(coupon.getUseStartTime());
                    vo.setUseEndTime(coupon.getUseEndTime());
                    // 结算页需同时展示领券有效期与券模板有效期。
                    vo.setTemplateStartTime(coupon.getStartTime());
                    vo.setTemplateEndTime(coupon.getEndTime());
                    // 设置叠加规则字段
                    vo.setStackable(coupon.getStackable());
                    vo.setExclusiveGroup(coupon.getExclusiveGroup());
                    List<Long> allowedIds = stackPartners.getOrDefault(coupon.getId(), List.of());
                    vo.setStackWithCouponIds(allowedIds);
                    vo.setStackWithCouponNames(allowedIds.stream().map(partnerNames::get)
                            .filter(Objects::nonNull).collect(Collectors.toList()));
                    // 只有菜品免单券需要关联菜品
                    if (coupon.getType().needDishItems()) {
                        vo.setDishItems(couponDishItemService.getItemsByCouponId(coupon.getId()));
                    }
                }
                voList.add(vo);
            }
        }
        // 设置转换后的记录
        voPage.setRecords(voList);
        return Result.success(voPage);
    }

    // ==================== 优惠券发放 ====================

    /**
     * 发放优惠券给用户（指定有效期）
     * @param userId 用户ID
     * @param couponId 优惠券ID
     * @param startTime 生效时间
     * @param expiredTime 过期时间
     * @return 发放结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result sendCouponToUser(Long userId, Long couponId, Date startTime, Date expiredTime) {
        if (userId == null || couponId == null) {
            return Result.error("参数错误：用户ID和优惠券ID不能为空");
        }
        try {
            // 检查用户是否存在（微服务改造：经 Feign 调 user-service）
            SysUser user = FeignResultUtil.unwrap(userFeignClient.getUserById(userId));
            if (user == null) {
                log.warn("发放优惠券失败：用户不存在，userId={}", userId);
                return Result.error("用户不存在或不属于当前租户，请从当前租户的用户列表选择");
            }
            // 检查优惠券是否存在
            Coupon coupon = couponMapper.selectById(couponId);
            if (coupon == null) {
                log.warn("发放优惠券失败：优惠券不存在，couponId={}", couponId);
                return Result.error("优惠券不存在");
            }
            // 检查优惠券是否上架可用
            if (coupon.getStatus() != OnOrOffShelfEnum.ON_SHELF) {
                log.warn("发放优惠券失败：优惠券未上架，couponId={}", couponId);
                return Result.error("优惠券未上架，不可发放");
            }
            // 检查优惠券是否在有效期内
            LocalDateTime now = LocalDateTime.now();
            if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(now)) {
                log.warn("发放优惠券失败：优惠券已过期，couponId={}", couponId);
                return Result.error("优惠券已过期");
            }
            // 先校验有效期，再扣库存。独立处理两个可选时间，避免只填一个时被忽略。
            LocalDateTime couponStartTime = startTime == null ? now : LocalDateTimeUtil.of(startTime);
            if (startTime == null && coupon.getStartTime() != null && coupon.getStartTime().isAfter(couponStartTime)) {
                couponStartTime = coupon.getStartTime();
            }
            // 未单独指定截止时间时沿用券模板：模板长期有效则用户券也长期有效。
            LocalDateTime couponExpireTime = expiredTime == null ? coupon.getEndTime() : LocalDateTimeUtil.of(expiredTime);
            if (couponExpireTime != null && (!couponExpireTime.isAfter(couponStartTime)
                    || !couponExpireTime.isAfter(now))) {
                return Result.error("过期时间必须晚于生效时间及当前时间");
            }
            if ((coupon.getStartTime() != null && couponStartTime.isBefore(coupon.getStartTime()))
                    || (coupon.getEndTime() != null && couponExpireTime != null
                    && couponExpireTime.isAfter(coupon.getEndTime()))) {
                return Result.error("发放有效期必须在优惠券的有效期范围内");
            }
            // 检查库存是否充足
            if (coupon.getRemainCount() == null || coupon.getRemainCount() <= 0) {
                log.warn("发放优惠券失败：优惠券库存不足，couponId={}, remainCount={}", couponId, coupon.getRemainCount());
                return Result.error("优惠券库存不足");
            }
            // 扣减库存（使用乐观锁，确保并发安全）
            int affected = couponMapper.decreaseStock(couponId, 1);
            if (affected <= 0) {
                log.warn("发放优惠券失败：库存扣减失败（可能已被抢完），couponId={}", couponId);
                return Result.error("优惠券已被抢完，请稍后再试");
            }
            // 创建用户优惠券记录
            UserCoupon userCoupon = new UserCoupon();
            userCoupon.setUserId(userId);
            userCoupon.setCouponId(couponId);
            userCoupon.setObtainTime(now);
            // 生成优惠券唯一码
            userCoupon.setCode(generateCouponCode());
            // 设置有效期
            userCoupon.setStartTime(couponStartTime);
            userCoupon.setExpireTime(couponExpireTime);
            // 根据开始时间设置状态
            if (couponStartTime.isAfter(now)) {
                userCoupon.setStatus(CouponStatusEnum.NOT_EFFECTIVE);
            } else {
                userCoupon.setStatus(CouponStatusEnum.NOT_USE);
            }
            // 保存用户优惠券
            boolean saved = save(userCoupon);
            if (saved) {
                log.info("优惠券发放成功：userId={}, couponId={}, userCouponId={}", userId, couponId, userCoupon.getId());
                return Result.success("优惠券发送成功", userCoupon);
            } else {
                // 发放失败，恢复库存
                couponMapper.increaseStock(couponId, 1);
                log.error("发放优惠券失败：保存用户优惠券记录失败，userId={}, couponId={}", userId, couponId);
                return Result.error("优惠券发送失败");
            }
        } catch (Exception e) {
            log.error("发放优惠券异常：userId={}, couponId={}", userId, couponId, e);
            throw new RuntimeException("系统异常，优惠券发送失败", e);
        }
    }

    // ==================== 私有工具方法 ====================

    /**
     * 生成优惠券唯一码（15位纯数字）
     * 格式：时间戳后8位 + 7位随机数
     */
    private String generateCouponCode() {
        int maxRetry = 10;
        for (int i = 0; i < maxRetry; i++) {
            String code = generateNumericCode();
            // 检查是否已存在
            LambdaQueryWrapper<UserCoupon> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(UserCoupon::getCode, code);
            Long count = userCouponMapper.selectCount(queryWrapper);
            if (count == 0) {
                return code;
            }
            log.warn("优惠券码冲突，重新生成：code={}, retry={}", code, i + 1);
        }
        // 极端情况：使用更长的时间戳确保唯一
        String fallbackCode = String.valueOf(System.currentTimeMillis()) + String.format("%02d", (int) (Math.random() * 100));
        return fallbackCode.substring(fallbackCode.length() - 15);
    }

    /**
     * 生成15位纯数字码
     * @return 15位数字字符串
     */
    private String generateNumericCode() {
        // 时间戳后8位（毫秒级，确保时间维度唯一性）
        String timestamp = String.valueOf(System.currentTimeMillis());
        String timePart = timestamp.substring(timestamp.length() - 8);
        // 7位随机数（0000000-9999999）
        int randomNum = (int) (Math.random() * 10000000);
        String randomPart = String.format("%07d", randomNum);
        return timePart + randomPart;
    }

    /**
     * 实时计算优惠券状态（用于展示，不修改数据库）
     * @param userCoupon 用户优惠券
     * @param now 当前时间
     * @return 计算后的状态
     */
    private CouponStatusEnum calculateCouponStatus(UserCoupon userCoupon, LocalDateTime now) {
        // 已使用的状态不变
        if (userCoupon.getStatus() == CouponStatusEnum.USED) {
            return CouponStatusEnum.USED;
        }
        // 检查是否已过期
        if (userCoupon.getExpireTime() != null && userCoupon.getExpireTime().isBefore(now)) {
            return CouponStatusEnum.OUT_OF_DATE;
        }
        // 检查是否未生效
        if (userCoupon.getStartTime() != null && userCoupon.getStartTime().isAfter(now)) {
            return CouponStatusEnum.NOT_EFFECTIVE;
        }
        // 默认为未使用
        return CouponStatusEnum.NOT_USE;
    }
    /**
     * 发放优惠券给用户（默认3天有效期）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result sendCouponToUser(Long userId, Long couponId) {
        return sendCouponToUser(userId, couponId, null, null);
    }

    // ==================== 优惠券状态管理 ====================

    /**
     * 处理用户优惠券过期
     * @param couponId 优惠券ID
     * @param userId 用户ID
     * @return 操作结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result expireUserCoupon(Long couponId, Long userId) {
        if (couponId == null || userId == null) {
            return Result.error("参数错误");
        }
        try {
            // 查询用户持有的该优惠券（未使用状态）
            LambdaQueryWrapper<UserCoupon> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(UserCoupon::getUserId, userId)
                    .eq(UserCoupon::getCouponId, couponId)
                    .in(UserCoupon::getStatus, CouponStatusEnum.NOT_EFFECTIVE, CouponStatusEnum.NOT_USE);
            List<UserCoupon> userCoupons = userCouponMapper.selectList(queryWrapper);
            if (userCoupons.isEmpty()) {
                log.info("用户没有待过期的优惠券：userId={}, couponId={}", userId, couponId);
                return Result.success("没有需要过期的优惠券");
            }
            // 更新状态为已过期
            int updatedCount = 0;
            for (UserCoupon userCoupon : userCoupons) {
                userCoupon.setStatus(CouponStatusEnum.OUT_OF_DATE);
                userCouponMapper.updateById(userCoupon);
                updatedCount++;
            }
            log.info("用户优惠券过期处理完成：userId={}, couponId={}, updatedCount={}", userId, couponId, updatedCount);
            return Result.success("已过期 " + updatedCount + " 张优惠券");
        } catch (Exception e) {
            log.error("处理用户优惠券过期异常：userId={}, couponId={}", userId, couponId, e);
            throw new RuntimeException("处理优惠券过期失败", e);
        }
    }

    /**
     * 批量更新过期优惠券状态（定时任务调用）
     * @return 更新的记录数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchUpdateExpiredCoupons() {
        try {
            // 先更新已生效的优惠券（未生效 -> 未使用）
            int effectiveCount = userCouponMapper.batchUpdateEffectiveStatus();
            log.info("批量更新已生效优惠券状态完成，更新数量：{}", effectiveCount);
            // 再更新已过期的优惠券（未使用 -> 已过期）
            int expiredCount = userCouponMapper.batchUpdateExpiredStatus();
            log.info("批量更新过期优惠券状态完成，更新数量：{}", expiredCount);
            return effectiveCount + expiredCount;
        } catch (Exception e) {
            log.error("批量更新优惠券状态异常", e);
            throw new RuntimeException("批量更新优惠券状态失败", e);
        }
    }

    /**
     * 统计过期超过指定天数的优惠券数量
     * @param days 过期天数
     * @return 过期优惠券数量
     */
    @Override
    public int countLongExpiredCoupons(int days) {
        return userCouponMapper.countLongExpiredCoupons(days);
    }

    // ==================== 优惠券核销与返还 ====================

    /**
     * 下单时核销优惠券（CAS乐观锁，防止并发一券多用）
     * 核销失败抛出 BusinessException，由调用方（订单模块）捕获后阻止下单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void verifyCouponOnOrder(Long userCouponId, Long orderId) {
        if (userCouponId == null || orderId == null) {
            throw new BusinessException(400, "优惠券ID和订单ID不能为空");
        }

        VerifyRecord existingRecord = verifyRecordMapper.selectOne(new LambdaQueryWrapper<VerifyRecord>()
                .eq(VerifyRecord::getUserCouponId, userCouponId)
                .eq(VerifyRecord::getOrderId, orderId)
                .last("LIMIT 1"));
        if (existingRecord != null) {
            if (existingRecord.getStatus() == VerifyStatusEnum.VERIFYED) {
                return;
            }
        }

        // 1. 查询用户优惠券
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null) {
            throw new BusinessException(400, "优惠券不存在，无法下单");
        }

        // 2. 校验状态必须为"未使用"
        if (userCoupon.getStatus() != CouponStatusEnum.NOT_USE) {
            throw new BusinessException(400, "优惠券状态异常（" + userCoupon.getStatus().getDesc() + "），无法使用");
        }

        // 3. 校验是否过期
        if (userCoupon.getExpireTime() != null && userCoupon.getExpireTime().isBefore(LocalDateTime.now())) {
            // 顺便更新为过期状态
            userCoupon.setStatus(CouponStatusEnum.OUT_OF_DATE);
            userCouponMapper.updateById(userCoupon);
            throw new BusinessException(400, "优惠券已过期，无法使用");
        }

        // 4. 校验是否到了生效时间
        if (userCoupon.getStartTime() != null && userCoupon.getStartTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "优惠券尚未生效，无法使用");
        }

        // 5. 【CAS乐观锁】核销：只有 status=1(未使用) 才能更新为 status=2(已使用)
        //    防止并发场景下同一张券被两个订单同时核销
        LocalDateTime now = LocalDateTime.now();
        int updateRows = userCouponMapper.casVerifyCoupon(userCouponId, now);
        if (updateRows <= 0) {
            throw new BusinessException(400, "优惠券已被使用或状态已变更，请刷新后重试");
        }

        // 6. 创建核销记录
        VerifyRecord record = existingRecord != null ? existingRecord : new VerifyRecord();
        record.setUserCouponId(userCouponId);
        record.setOrderId(orderId);
        record.setVerifyCode(userCoupon.getCode());
        record.setVerifyTime(now);
        record.setStatus(VerifyStatusEnum.VERIFYED);
        if (existingRecord == null) {
            verifyRecordMapper.insert(record);
        } else {
            verifyRecordMapper.updateById(record);
        }

        log.info("优惠券核销成功(CAS) - userCouponId: {}, orderId: {}", userCouponId, orderId);
    }

    /**
     * 取消订单时返还优惠券（CAS乐观锁）
     * 未过期恢复为"未使用"，已过期标记为"已过期"；
     * 同时重置券码，避免再次核销时与 verify_record.verify_code 唯一索引冲突；
     * 对应核销记录标记为"未核销"（无效）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreCouponOnCancel(Long userCouponId, Long orderId) {
        if (userCouponId == null || orderId == null) {
            throw new BusinessException(400, "优惠券ID和订单ID不能为空");
        }

        // 返还必须绑定原订单，不能仅凭 userCouponId 返还其他订单已核销的券。
        VerifyRecord record = verifyRecordMapper.selectOne(new LambdaQueryWrapper<VerifyRecord>()
                .eq(VerifyRecord::getUserCouponId, userCouponId)
                .eq(VerifyRecord::getOrderId, orderId)
                .last("FOR UPDATE"));
        if (record == null) {
            throw new BusinessException(409, "未找到该订单对应的优惠券核销记录");
        }
        if (record.getStatus() == VerifyStatusEnum.NOT_VERIFY) {
            return;
        }

        // 1. 查询用户优惠券
        UserCoupon userCoupon = userCouponMapper.selectById(userCouponId);
        if (userCoupon == null) {
            throw new BusinessException(404, "用户优惠券不存在，无法返还");
        }

        // 2. 只有已使用的券才需要返还
        if (userCoupon.getStatus() != CouponStatusEnum.USED) {
            throw new BusinessException(409, "优惠券状态与核销记录不一致，拒绝返还");
        }

        // 3. 【CAS乐观锁】判断是否已过期，决定恢复后的状态
        int newStatus;
        String statusDesc;
        if (userCoupon.getExpireTime() != null && userCoupon.getExpireTime().isBefore(LocalDateTime.now())) {
            newStatus = 3; // OUT_OF_DATE
            statusDesc = "已过期";
        } else {
            newStatus = 1; // NOT_USE
            statusDesc = "未使用";
        }
        int updateRows = userCouponMapper.casRestoreCoupon(userCouponId, newStatus);
        if (updateRows <= 0) {
            throw new BusinessException(409, "优惠券状态已变化，返还失败");
        }

        // 3.5 重置券码：避免同一张券再次核销时与 verify_record 表 verify_code 唯一索引冲突（历史记录仍保留原码）
        String newCode = generateCouponCode();
        userCouponMapper.updateCodeById(userCouponId, newCode);

        // 4. 只撤销当前订单对应的核销记录。
        record.setStatus(VerifyStatusEnum.NOT_VERIFY);
        verifyRecordMapper.updateById(record);

        log.info("优惠券返还成功(CAS) - userCouponId: {}, orderId: {}, 恢复状态: {}", userCouponId, orderId, statusDesc);
    }

    // ======================== 优惠券叠加规则校验 ========================

    /**
     * 校验多张优惠券的叠加规则
     */
    @Override
    public void validateCouponStackRules(List<Long> userCouponIds) {
        if (userCouponIds == null || userCouponIds.size() <= 1) {
            return; // 单张或无券不需要校验叠加规则
        }

        // 批量查询用户优惠券
        List<UserCoupon> userCoupons = userCouponMapper.selectByIds(userCouponIds);
        if (userCoupons.size() != userCouponIds.size()) {
            throw new BusinessException(400, "优惠券不存在");
        }

        // 批量查询优惠券详情
        List<Long> couponIds = userCoupons.stream()
                .map(UserCoupon::getCouponId).collect(Collectors.toList());
        if (new HashSet<>(couponIds).size() != couponIds.size()) {
            throw new BusinessException(400, "同一种优惠券每单只能使用一张");
        }
        List<Coupon> coupons = couponMapper.selectByIds(couponIds);

        if (coupons.size() != couponIds.size()) {
            throw new BusinessException(400, "部分优惠券信息不存在");
        }

        // 1. 检查是否有不可叠加的券
        for (Coupon coupon : coupons) {
            BooleanEnum stackable = coupon.getStackable();
            if (stackable != BooleanEnum.YES) {
                throw new BusinessException(400, "优惠券「" + coupon.getName() + "」不可与其他优惠券叠加使用");
            }
        }

        // 无向配对由后台维护：每两张券必须有明确关系，未配置的组合默认不可叠加。
        Long tenantId = org.hongtu.tenant.TenantContext.getTenantId();
        for (Coupon coupon : coupons) {
            Set<Long> partners = new HashSet<>(stackPairMapper.listPartnerIds(tenantId, coupon.getId()));
            for (Coupon other : coupons) {
                if (!coupon.getId().equals(other.getId()) && !partners.contains(other.getId())) {
                    throw new BusinessException(400, "优惠券「" + coupon.getName()
                            + "」与「" + other.getName() + "」未配置为可叠加");
                }
            }
        }

        // 2. 旧互斥组仍生效：相同组号的券不能同单使用。
        for (Coupon coupon : coupons) {
            String exclusiveGroup = coupon.getExclusiveGroup();
            if (exclusiveGroup == null || exclusiveGroup.trim().isEmpty()) {
                continue;
            }
            for (Coupon otherCoupon : coupons) {
                if (!coupon.getId().equals(otherCoupon.getId())
                        && exclusiveGroup.equals(otherCoupon.getExclusiveGroup())) {
                    throw new BusinessException(400,
                            "优惠券「" + coupon.getName() + "」与「" + otherCoupon.getName() + "」互斥，不能同时使用");
                }
            }
        }

        log.info("优惠券叠加规则校验通过 - userCouponIds: {}", userCouponIds);
    }

}
