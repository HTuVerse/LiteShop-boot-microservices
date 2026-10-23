package org.hongtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.hongtu.DAO.UserCouponMapper;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.Result.Result;
import org.hongtu.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 积分兑换券发放；券库存和来源订单在同一个本地事务中提交。 */
@Service
public class MallCouponIssueService {
    @Resource private UserCouponMapper userCouponMapper;
    @Resource private UserCouponService userCouponService;

    /** 订单重试时返回已发券 ID。 */
    @Transactional(rollbackFor = Exception.class)
    public Long issue(Long userId, Long couponId, Long orderId) {
        if (userId == null || couponId == null || orderId == null) throw new BusinessException("发券参数不能为空");
        UserCoupon existing = userCouponMapper.selectOne(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getSourceOrderId, orderId).last("LIMIT 1"));
        if (existing != null) {
            if (!userId.equals(existing.getUserId()) || !couponId.equals(existing.getCouponId()))
                throw new BusinessException("兑换订单对应的优惠券不一致");
            return existing.getId();
        }
        Result<?> result = userCouponService.sendCouponToUser(userId, couponId);
        if (result == null || !Result.CODE_SUCCESS.equals(result.getCode())
                || !(result.getData() instanceof UserCoupon issued))
            throw new BusinessException(result == null ? 500 : result.getCode(),
                    result == null || result.getMsg() == null ? "优惠券发放失败" : result.getMsg());
        issued.setSourceOrderId(orderId);
        if (userCouponMapper.updateById(issued) != 1) throw new BusinessException("优惠券发放记录更新失败");
        return issued.getId();
    }
}
