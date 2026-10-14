package org.hongtu.restaurant.VO;

import lombok.Data;
import org.hongtu.restaurant.DTO.PricingCampaignAudience;
import org.hongtu.restaurant.DTO.PricingCampaignScope;
import org.hongtu.restaurant.DTO.PricingRule;
import org.hongtu.restaurant.enums.PricingEnums.CampaignType;
import org.hongtu.restaurant.enums.PricingEnums.StackMode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定价活动新增或更新请求。
 */
@Data
public class PricingCampaignSaveRequest {
    private Long id;
    private String campaignCode;
    private String campaignName;
    private CampaignType campaignType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer priority;
    private String exclusiveGroup;
    private StackMode stackMode;
    private Integer status;
    /** 客户端读取到的版本号，用于防止并发覆盖。 */
    private Integer expectedVersion;
    private PricingRule rule;
    private List<PricingCampaignScope> scopes;
    private List<PricingCampaignAudience> audiences;
}
