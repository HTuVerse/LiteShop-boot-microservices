package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.PricingRule;

/**
 * 定价执行规则数据访问接口。
 */
@Mapper
public interface PricingRuleMapper extends BaseMapper<PricingRule> {
}
