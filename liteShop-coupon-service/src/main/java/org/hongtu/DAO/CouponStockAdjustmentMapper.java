package org.hongtu.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.DTO.CouponStockAdjustment;

/** 优惠券库存调整记录持久化接口。 */
@Mapper
public interface CouponStockAdjustmentMapper extends BaseMapper<CouponStockAdjustment> {
}
