package org.hongtu.restaurant.VO;

import lombok.Data;
import org.hongtu.restaurant.DTO.PricingCampaign;
import org.hongtu.restaurant.DTO.PricingCampaignAudience;
import org.hongtu.restaurant.DTO.PricingCampaignScope;
import org.hongtu.restaurant.DTO.PricingRule;

import java.util.List;

/**
 * 定价活动及其规则、范围和人群配置详情。
 */
@Data
public class PricingCampaignDetailVO {
    private PricingCampaign campaign;
    private PricingRule rule;
    private List<PricingCampaignScope> scopes;
    private List<PricingCampaignAudience> audiences;
}
