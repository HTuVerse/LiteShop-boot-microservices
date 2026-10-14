package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.PricingCampaignAudience;

/**
 * 定价活动适用人群数据访问接口。
 */
@Mapper
public interface PricingCampaignAudienceMapper extends BaseMapper<PricingCampaignAudience> {
}
