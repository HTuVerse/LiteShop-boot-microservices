package org.hongtu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DAO.CouponMapper;
import org.hongtu.DAO.CouponRevokeRecordMapper;
import org.hongtu.DAO.CouponStockAdjustmentMapper;
import org.hongtu.DAO.CouponStackPairMapper;
import org.hongtu.DAO.UserCouponMapper;
import org.hongtu.DTO.Coupon;
import org.hongtu.DTO.CouponDishItem;
import org.hongtu.DTO.CouponStockAdjustment;
import org.hongtu.DTO.CouponRevokeRecord;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.Result.Result;
import org.hongtu.enums.CouponStatusEnum;
import org.hongtu.enums.CouponTypeEnum;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.OnOrOffShelfEnum;
import org.hongtu.service.CouponDishItemService;
import org.hongtu.service.CouponService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.utils.CouponUnavailableDay;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;
import java.util.List;

/**
 * <p>
 * 优惠券 服务实现类
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Slf4j
@Service
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements CouponService {

    @Resource
    private UserCouponMapper userCouponMapper;

    @Resource
    private CouponDishItemService couponDishItemService;

    @Resource
    private CouponStockAdjustmentMapper stockAdjustmentMapper;

    @Resource
    private CouponRevokeRecordMapper revokeRecordMapper;

    @Resource
    private CouponStackPairMapper stackPairMapper;

    // ==================== 优惠券查询 ====================

    /**
     * 分页查询优惠券列表
     */
    @Override
    public Result<Page<Coupon>> getCouponList(Integer currentPage, Integer pageSize, String name, OnOrOffShelfEnum status) {
        Page<Coupon> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<Coupon> queryWrapper = new LambdaQueryWrapper<>();
        // 名称模糊查询
        if (StringUtils.hasText(name)) {
            queryWrapper.like(Coupon::getName, name);
        }
        // 状态筛选
        if (status != null) {
            queryWrapper.eq(Coupon::getStatus, status);
        }
        // 按创建时间倒序
        queryWrapper.orderByDesc(Coupon::getCreateTime);
        Page<Coupon> result = page(page, queryWrapper);
        return Result.success(result);
    }

    // ==================== 优惠券增删改 ====================

    /**
     * 创建优惠券
     * 支持满减、整单折扣、菜品免单、菜品折扣和代金券。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Coupon> createCoupon(Coupon coupon) {
        // 参数校验
        if (coupon == null || !StringUtils.hasText(coupon.getName())) {
            return Result.error("优惠券名称不能为空");
        }
        if (coupon.getType() == null) {
            return Result.error("优惠券类型不能为空");
        }
        String ruleError = validateRules(coupon);
        if (ruleError != null) return Result.error(ruleError);
        if (coupon.getStackable() == null) coupon.setStackable(BooleanEnum.NO);
        String stackError = validateStackPartners(coupon);
        if (stackError != null) return Result.error(stackError);
        if (coupon.getType() == CouponTypeEnum.DISH_FREE) coupon.setValue(java.math.BigDecimal.ZERO);
        if (coupon.getTotalCount() == null || coupon.getTotalCount() <= 0) {
            return Result.error("发放总数必须大于0");
        }
        // 需要关联菜品的类型校验
        if (needDishItems(coupon.getType())) {
            if (coupon.getDishItems() == null || coupon.getDishItems().isEmpty()) {
                return Result.error("该类型优惠券必须关联至少一个菜品");
            }
        }
        // 设置默认值
        coupon.setRemainCount(coupon.getTotalCount());
        coupon.setCreateTime(LocalDateTime.now());
        if (coupon.getStatus() == null) {
            coupon.setStatus(OnOrOffShelfEnum.OFF_SHELF); // 默认下架
        }
        // 保存优惠券主表
        boolean saved = save(coupon);
        if (saved) {
            // 保存关联菜品
            if (usesDishItems(coupon.getType(), coupon.getDishItems())) {
                couponDishItemService.saveItems(coupon.getId(), coupon.getDishItems());
            }
            saveStackPartners(coupon.getId(), coupon.getStackWithCouponIds());
            log.info("优惠券创建成功：id={}, name={}, type={}", coupon.getId(), coupon.getName(), coupon.getType());
            return Result.success("创建成功", coupon);
        }
        return Result.error("创建失败");
    }

    /**
     * 更新优惠券
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Coupon> updateCoupon(Coupon coupon) {
        if (coupon == null || coupon.getId() == null) {
            return Result.error("优惠券ID不能为空");
        }
        // 检查是否存在
        Coupon existCoupon = getById(coupon.getId());
        if (existCoupon == null) {
            return Result.error("优惠券不存在");
        }
        if (existCoupon.getRecallTime() != null) return Result.error("已批量回收的模板不可编辑");
        CouponTypeEnum targetType = coupon.getType() == null ? existCoupon.getType() : coupon.getType();
        if (targetType.needDishItems()
                && (coupon.getDishItems() == null || coupon.getDishItems().isEmpty())) {
            return Result.error("菜品券必须关联至少一个菜品");
        }
        coupon.setType(targetType);
        if (coupon.getStackable() == null) coupon.setStackable(existCoupon.getStackable());
        String ruleError = validateRules(coupon);
        if (ruleError != null) return Result.error(ruleError);
        String stackError = validateStackPartners(coupon);
        if (stackError != null) return Result.error(stackError);
        if (targetType == CouponTypeEnum.DISH_FREE) coupon.setValue(java.math.BigDecimal.ZERO);
        org.hongtu.security.OperationPermission.requireChange("marketing:coupon:status", existCoupon.getStatus(), coupon.getStatus());
        // 库存只能通过专用增减接口修改，避免普通编辑覆盖并发发券后的库存。
        if (coupon.getTotalCount() != null && !coupon.getTotalCount().equals(existCoupon.getTotalCount())) {
            return Result.error("请通过库存调整功能增减剩余库存");
        }
        coupon.setTotalCount(null);
        coupon.setRemainCount(null);
        // 更新主表
        boolean updated = updateById(coupon);
        if (updated) {
            // 更新关联菜品（如果传了dishItems，则重新保存）
            if (usesDishItems(targetType, coupon.getDishItems())) {
                couponDishItemService.saveItems(coupon.getId(), coupon.getDishItems());
            } else if (existCoupon.getType().needDishItems() || existCoupon.getType() == CouponTypeEnum.CASH_VOUCHER) {
                couponDishItemService.deleteItemsByCouponId(coupon.getId());
            }
            if (coupon.getStackWithCouponIds() != null || coupon.getStackable() != BooleanEnum.YES) {
                saveStackPartners(coupon.getId(), coupon.getStackWithCouponIds());
            }
            log.info("优惠券更新成功：id={}", coupon.getId());
            // 查询完整数据返回
            Coupon result = getById(coupon.getId());
            result.setDishItems(couponDishItemService.getItemsByCouponId(coupon.getId()));
            result.setStackWithCouponIds(stackPairMapper.listPartnerIds(TenantContext.getTenantId(), coupon.getId()));
            return Result.success("更新成功", result);
        }
        return Result.error("更新失败");
    }

    /**
     * 删除优惠券
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result deleteCoupon(Long id) {
        if (id == null) {
            return Result.error("优惠券ID不能为空");
        }
        // 检查是否存在
        Coupon coupon = getById(id);
        if (coupon == null) {
            return Result.error("优惠券不存在");
        }
        if (coupon.getRecallTime() != null) return Result.error("已批量回收的模板须保留审计记录，不可删除");
        // 单张回收同样需要保留模板，保证回收原因与发放记录可追溯。
        if (revokeRecordMapper.selectCount(new LambdaQueryWrapper<CouponRevokeRecord>()
                .eq(CouponRevokeRecord::getCouponId, id)) > 0) {
            return Result.error("该模板已有用户券回收记录，不可删除");
        }
        // 检查是否有用户持有该优惠券
        LambdaQueryWrapper<UserCoupon> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserCoupon::getCouponId, id)
                .in(UserCoupon::getStatus, CouponStatusEnum.NOT_EFFECTIVE, CouponStatusEnum.NOT_USE);
        Long count = userCouponMapper.selectCount(queryWrapper);
        if (count > 0) {
            return Result.error("该优惠券还有用户持有，无法删除");
        }
        // 删除关联菜品
        couponDishItemService.deleteItemsByCouponId(id);
        stackPairMapper.deleteByCouponId(TenantContext.getTenantId(), id);
        // 删除优惠券
        boolean removed = removeById(id);
        if (removed) {
            log.info("优惠券删除成功：id={}", id);
            return Result.success(200, "删除成功");
        }
        return Result.error("删除失败");
    }

    /**
     * 根据ID获取优惠券详情（带关联菜品）
     */
    @Override
    public Result<Coupon> getCouponById(Long id) {
        if (id == null) {
            return Result.error("优惠券ID不能为空");
        }
        Coupon coupon = getById(id);
        if (coupon == null) {
            return Result.error("优惠券不存在");
        }
        // 查询关联菜品
        if (needDishItems(coupon.getType()) || coupon.getType() == CouponTypeEnum.CASH_VOUCHER) {
            coupon.setDishItems(couponDishItemService.getItemsByCouponId(id));
        }
        coupon.setStackWithCouponIds(stackPairMapper.listPartnerIds(TenantContext.getTenantId(), id));
        return Result.success(coupon);
    }

    // ==================== 优惠券状态管理 ====================

    /**
     * 批量下架过期优惠券（定时任务调用）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchOffShelfExpiredCoupons() {
        int updatedCount = baseMapper.batchOffShelfExpiredCoupons();
        if (updatedCount > 0) {
            log.info("批量下架过期优惠券成功，下架数量：{}", updatedCount);
        }
        return updatedCount;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 判断该优惠券类型是否需要关联菜品
     */
    private boolean needDishItems(CouponTypeEnum type) {
        return type != null && type.needDishItems();
    }

    /** 代金券可选菜品范围，菜品专属券仍强制指定菜品。 */
    private boolean usesDishItems(CouponTypeEnum type, List<CouponDishItem> items) {
        return needDishItems(type) || (type == CouponTypeEnum.CASH_VOUCHER && items != null && !items.isEmpty());
    }

    /** 规范化适用门店和时段，拒绝无效折扣率与不完整的使用限制。 */
    private String validateRules(Coupon coupon) {
        if (coupon.getStartTime() == null) return "请选择优惠券生效时间";
        if (coupon.getType().needValue() && coupon.getValue() == null) return "优惠券面值或折扣率不能为空";
        if ((coupon.getType() == CouponTypeEnum.FULL_REDUCTION || coupon.getType() == CouponTypeEnum.CASH_VOUCHER)
                && coupon.getValue().signum() <= 0) return "抵扣金额必须大于0";
        if ((coupon.getType() == CouponTypeEnum.ORDER_DISCOUNT || coupon.getType() == CouponTypeEnum.DISH_DISCOUNT)
                && (coupon.getValue().compareTo(java.math.BigDecimal.ZERO) <= 0
                || coupon.getValue().compareTo(java.math.BigDecimal.ONE) >= 0)) return "折扣率必须大于0且小于1";
        if (coupon.getMinAmount() != null && coupon.getMinAmount().signum() < 0) return "最低消费不能小于0";
        if (coupon.getStartTime() != null && coupon.getEndTime() != null
                && !coupon.getStartTime().isBefore(coupon.getEndTime())) return "有效期结束必须晚于开始";
        if ((coupon.getUseStartTime() == null) != (coupon.getUseEndTime() == null)) return "每日可用时段需同时填写起止时间";
        if (coupon.getUseStartTime() != null && !coupon.getUseStartTime().isBefore(coupon.getUseEndTime()))
            return "每日可用结束时间必须晚于开始时间";
        try {
            coupon.setStoreIds(normalizeIds(coupon.getStoreIds(), false));
            coupon.setUseWeekdays(normalizeIds(coupon.getUseWeekdays(), true));
            coupon.setUnavailableDates(normalizeUnavailableDates(coupon.getUnavailableDates()));
            coupon.setUnavailableHolidays(normalizeUnavailableHolidays(coupon.getUnavailableHolidays()));
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    /** 只接受完整 ISO 日期，防止无效日期被保存后静默失效。 */
    private String normalizeUnavailableDates(String value) {
        if (!StringUtils.hasText(value)) return null;
        List<String> dates = Arrays.stream(value.split(",")).map(String::trim).distinct().toList();
        if (dates.size() > 100) throw new IllegalArgumentException("不可用日期最多选择100天");
        for (String date : dates) {
            if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("不可用日期格式应为yyyy-MM-dd");
            try { LocalDate.parse(date); } catch (Exception e) { throw new IllegalArgumentException("不可用日期无效：" + date); }
        }
        return String.join(",", dates);
    }

    private String normalizeUnavailableHolidays(String value) {
        if (!StringUtils.hasText(value)) return null;
        List<String> holidays = Arrays.stream(value.split(",")).map(String::trim).distinct().toList();
        if (holidays.stream().anyMatch(code -> !CouponUnavailableDay.HOLIDAYS.contains(code)))
            throw new IllegalArgumentException("不可用节日无效");
        return String.join(",", holidays);
    }

    /** 配对按券模板 ID 维护；两端共用一条记录，禁止跨租户或与不可叠加券配对。 */
    private String validateStackPartners(Coupon coupon) {
        List<Long> ids = coupon.getStackWithCouponIds();
        if (ids == null || ids.isEmpty()) return null;
        if (coupon.getStackable() != BooleanEnum.YES) return "选择可叠加优惠券前，请先开启叠加使用";
        if (ids.size() > 50 || new HashSet<>(ids).size() != ids.size()) return "可叠加优惠券不能重复且最多选择50张";
        for (Long id : ids) {
            if (id == null || id <= 0 || id.equals(coupon.getId())) return "可叠加优惠券ID无效";
            Coupon partner = getById(id);
            if (partner == null) return "选择的可叠加优惠券不存在或不属于当前租户";
            if (partner.getStackable() != BooleanEnum.YES) return "优惠券「" + partner.getName() + "」未开启叠加使用";
            if (StringUtils.hasText(coupon.getExclusiveGroup())
                    && coupon.getExclusiveGroup().equals(partner.getExclusiveGroup()))
                return "相同互斥组的优惠券不能配置为可叠加";
        }
        return null;
    }

    /** 删除旧关系后保存新的无向配对，单张券解除配对会同步影响另一端。 */
    private void saveStackPartners(Long couponId, List<Long> partnerIds) {
        Long tenantId = TenantContext.getTenantId();
        stackPairMapper.deleteByCouponId(tenantId, couponId);
        if (partnerIds == null) return;
        for (Long partnerId : partnerIds) {
            stackPairMapper.insertPair(tenantId, Math.min(couponId, partnerId), Math.max(couponId, partnerId));
        }
    }

    private String normalizeIds(String raw, boolean weekdays) {
        if (!StringUtils.hasText(raw)) return "";
        String value = Arrays.stream(raw.split(","))
                .map(String::trim).distinct().collect(Collectors.joining(","));
        for (String part : value.split(",")) {
            if (!part.matches("[1-9][0-9]*")) throw new IllegalArgumentException(weekdays ? "可用星期格式错误" : "门店ID格式错误");
            long id;
            try { id = Long.parseLong(part); } catch (NumberFormatException e) {
                throw new IllegalArgumentException(weekdays ? "可用星期格式错误" : "门店ID格式错误");
            }
            if (weekdays && id > 7) throw new IllegalArgumentException("可用星期只能是1至7");
        }
        if (value.length() > (weekdays ? 13 : 2000)) throw new IllegalArgumentException("适用范围过长");
        return value;
    }

    /** 库存增减与记录同事务提交，按旧库存条件更新以防并发覆盖。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Coupon> adjustStock(Long couponId, Integer delta, String reason, Long operatorId) {
        if (couponId == null || delta == null || delta == 0 || delta == Integer.MIN_VALUE
                || !StringUtils.hasText(reason) || reason.trim().length() > 200) {
            return Result.error("请填写有效的库存增减数量和200字以内的原因");
        }
        Coupon coupon = getById(couponId);
        if (coupon == null) return Result.error("优惠券不存在");
        if (coupon.getRecallTime() != null) return Result.error("已批量回收的模板不可调整库存");
        long after = (long) coupon.getRemainCount() + delta;
        long totalAfter = (long) coupon.getTotalCount() + delta;
        if (after < 0 || totalAfter < 0 || after > Integer.MAX_VALUE || totalAfter > Integer.MAX_VALUE)
            return Result.error("调整后的剩余库存或总量超出有效范围");
        if (baseMapper.adjustStock(couponId, coupon.getRemainCount(), delta) != 1)
            return Result.error("库存已变化，请刷新后重试");
        CouponStockAdjustment record = new CouponStockAdjustment();
        record.setCouponId(couponId);
        record.setDelta(delta);
        record.setBeforeCount(coupon.getRemainCount());
        record.setAfterCount((int) after);
        record.setOperatorId(operatorId);
        record.setReason(reason.trim());
        record.setCreateTime(LocalDateTime.now());
        record.setTenantId(TenantContext.getTenantId());
        stockAdjustmentMapper.insert(record);
        return Result.success("库存调整成功", getById(couponId));
    }

    @Override
    public Result<Page<CouponStockAdjustment>> getStockAdjustments(Long couponId, Integer currentPage, Integer pageSize) {
        if (couponId == null || getById(couponId) == null) return Result.error("优惠券不存在");
        Page<CouponStockAdjustment> page = new Page<>(Math.max(1, currentPage), Math.min(50, Math.max(1, pageSize)));
        return Result.success(stockAdjustmentMapper.selectPage(page,
                new LambdaQueryWrapper<CouponStockAdjustment>().eq(CouponStockAdjustment::getCouponId, couponId)
                        .orderByDesc(CouponStockAdjustment::getCreateTime)));
    }

    /**
     * 切换优惠券上下架状态（自动取反）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result toggleCouponStatus(Long id) {
        if (id == null) {
            return Result.error("优惠券ID不能为空");
        }
        Coupon coupon = getById(id);
        if (coupon == null) {
            return Result.error("优惠券不存在");
        }
        if (coupon.getRecallTime() != null) return Result.error("已批量回收的模板不可重新上架");
        // 获取当前状态，切换为相反状态
        OnOrOffShelfEnum currentStatus = coupon.getStatus();
        OnOrOffShelfEnum targetStatus = (currentStatus == OnOrOffShelfEnum.ON_SHELF)
                ? OnOrOffShelfEnum.OFF_SHELF
                : OnOrOffShelfEnum.ON_SHELF;
        // 上架时校验
        if (targetStatus == OnOrOffShelfEnum.ON_SHELF) {
            if (coupon.getEndTime() != null && coupon.getEndTime().isBefore(LocalDateTime.now())) {
                return Result.error("优惠券已过期，无法上架");
            }
            if (coupon.getRemainCount() == null || coupon.getRemainCount() <= 0) {
                return Result.error("优惠券库存不足，无法上架");
            }
        }
        // 更新状态
        // 仅更新状态，不能用读出的旧库存覆盖同时进行的发券或补库。
        boolean updated = baseMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, id).eq(Coupon::getStatus, currentStatus)
                .isNull(Coupon::getRecallTime)
                .set(Coupon::getStatus, targetStatus)) == 1;
        if (updated) {
            String message = (targetStatus == OnOrOffShelfEnum.ON_SHELF) ? "上架成功" : "下架成功";
            log.info("优惠券状态切换成功：id={}, {} -> {}", id, currentStatus.getDesc(), targetStatus.getDesc());
            return Result.success(200,message);
        }
        return Result.error("操作失败");
    }

}
