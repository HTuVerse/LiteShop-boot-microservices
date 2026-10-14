package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.PricingEnums.AudienceType;

import java.io.Serializable;

/** 活动适用人群。 */
@Data
@Accessors(chain = true)
@TableName("pricing_campaign_audience")
public class PricingCampaignAudience implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long campaignId;
    /** 0全部，1会员，2指定会员等级。 */
    private AudienceType audienceType;
    /** MEMBER_LEVEL 时保存等级编码。 */
    private Integer audienceValue;
}
