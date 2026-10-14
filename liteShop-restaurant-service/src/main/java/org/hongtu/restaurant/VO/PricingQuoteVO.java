package org.hongtu.restaurant.VO;

import lombok.Data;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.PricingEnums.AdjustmentType;
import org.hongtu.restaurant.enums.PricingEnums.CampaignType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一定价试算结果。
 */
@Data
@Accessors(chain = true)
public class PricingQuoteVO {
    private Long storeId;
    private Long userId;
    private Integer memberLevelCode;
    private BigDecimal listAmount;
    private BigDecimal campaignDiscountAmount;
    private BigDecimal payableAmount;
    private List<Line> lines = new ArrayList<>();

    /**
     * 单个购物车行的定价结果。
     */
    @Data
    @Accessors(chain = true)
    public static class Line {
        private String lineKey;
        private Long dishId;
        private String dishName;
        private Integer quantity;
        private BigDecimal unitListPrice;
        private BigDecimal unitSellingPrice;
        /** 下单时门店实际单位成本；门店未配置时使用总部成本。 */
        private BigDecimal unitCost;
        private BigDecimal lineListAmount;
        private BigDecimal lineDiscountAmount;
        private BigDecimal linePayableAmount;
        private String priceSource;
        private List<Adjustment> adjustments = new ArrayList<>();
        /** 当前菜品可参加的活动，包含尚未达到件数或金额门槛的活动。 */
        private List<CampaignHint> availableCampaigns = new ArrayList<>();
    }

    /** 收银台用于展示活动名称和门槛的轻量提示。 */
    @Data
    @Accessors(chain = true)
    public static class CampaignHint {
        private Long campaignId;
        private String campaignName;
        private CampaignType campaignType;
        private String description;
    }

    /**
     * 单次会员价或活动优惠的金额调整明细。
     */
    @Data
    @Accessors(chain = true)
    public static class Adjustment {
        private Long campaignId;
        private String campaignName;
        private AdjustmentType adjustmentType;
        private BigDecimal beforeAmount;
        private BigDecimal adjustmentAmount;
        private BigDecimal afterAmount;
        private String description;
        private String ruleSnapshot;
    }
}
