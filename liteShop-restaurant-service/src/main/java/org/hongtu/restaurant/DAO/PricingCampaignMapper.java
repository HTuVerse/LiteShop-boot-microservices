package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.PricingCampaign;

/**
 * 定价活动数据访问接口。
 */
@Mapper
public interface PricingCampaignMapper extends BaseMapper<PricingCampaign> {
}
