package org.hongtu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.Result.Result;
import org.hongtu.enums.CouponStatusEnum;

import java.util.Date;
import java.util.List;

/**
 * <p>
 * 用户优惠券关联表 服务类
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
public interface UserCouponService extends IService<UserCoupon> {

    /**
     * 获取用户优惠券列表
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param searchKey 搜索关键字
     * @param userId 用户ID
     * @param status 优惠券状态（可选）：0-未生效，1-未使用，2-已使用，3-已过期，null-全部
     * @return 用户优惠券分页列表
     */
    Result getUserCoupons(Integer currentPage, Integer pageSize, String searchKey, Long userId, CouponStatusEnum status);

    /** 管理员按券模板查询发放记录，包含已回收状态。 */
    Result getIssuedCoupons(Long couponId, Long userId, Integer status, Integer currentPage, Integer pageSize);

    /** 预览指定模板当前可回收的未使用券数量。 */
    Result countRevocableCoupons(Long couponId);

    /** 管理员分页查看模板回收审计记录。 */
    Result getRevokeRecords(Long couponId, Integer currentPage, Integer pageSize);

    /** 回收单张尚未使用的用户券。 */
    Result revokeUserCoupon(Long userCouponId, String reason, Long operatorId);

    /** 回收指定模板的全部未使用券并永久停止继续发放。 */
    Result revokeTemplateCoupons(Long couponId, String reason, Long operatorId);

    /** 每轮推进当前租户未完成的模板回收任务。 */
    int reconcilePendingRecalls();

    /**
     * 发送优惠券给用户
     * @param userId 用户ID
     * @param couponId 优惠券ID
     * @return 操作结果
     */
    Result sendCouponToUser(Long userId, Long couponId,Date startTime, Date expiredTime);

    Result sendCouponToUser(Long userId, Long couponId);

    /**
     * 处理用户优惠券过期
     * @param couponId 优惠券ID
     * @param userId 用户ID
     * @return 操作结果
     */
    Result expireUserCoupon(Long couponId, Long userId);

    /**
     * 批量更新过期优惠券状态（定时任务调用）
     * @return 更新的记录数
     */
    int batchUpdateExpiredCoupons();

    /**
     * 统计过期超过指定天数的优惠券数量
     * @param days 过期天数
     * @return 过期优惠券数量
     */
    int countLongExpiredCoupons(int days);

    /**
     * 下单时核销优惠券
     * 核销失败会抛出异常，调用方应阻止下单
     *
     * @param userCouponId 用户优惠券ID
     * @param orderId 订单ID（用于日志追踪）
     */
    void verifyCouponOnOrder(Long userCouponId, Long orderId);

    /**
     * 取消订单时返还优惠券
     * 未过期恢复为未使用，已过期标记为已过期，核销记录标记为无效
     *
     * @param userCouponId 用户优惠券ID
     * @param orderId 订单ID（用于日志追踪）
     */
    void restoreCouponOnCancel(Long userCouponId, Long orderId);

    /** 取消订单事务回滚时，按原核销记录撤销返券。 */
    void undoRestoreOnRollback(Long userCouponId, Long orderId);

    /**
     * 校验多张优惠券的叠加规则
     *
     * @param userCouponIds 用户优惠券ID列表
     * @throws org.hongtu.exception.BusinessException 叠加规则校验失败时抛出异常
     */
    void validateCouponStackRules(List<Long> userCouponIds);
}
