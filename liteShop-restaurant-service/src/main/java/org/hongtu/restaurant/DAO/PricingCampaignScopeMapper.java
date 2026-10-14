package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.PricingCampaignScope;

/**
 * 定价活动适用范围数据访问接口。
 */
@Mapper
public interface PricingCampaignScopeMapper extends BaseMapper<PricingCampaignScope> {
}
