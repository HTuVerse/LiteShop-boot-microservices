package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.PricingEnums.TargetType;

import java.io.Serializable;

/** 活动门店及商品范围。 */
@Data
@Accessors(chain = true)
@TableName("pricing_campaign_scope")
public class PricingCampaignScope implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long campaignId;
    /** 为空表示全部门店。 */
    private Long storeId;
    /** 门店分组ID；与 storeId 互斥，为空且 storeId 为空表示全部门店。 */
    private Long storeGroupId;
    /** 0全部，1菜品，2分类。 */
    private TargetType targetType;
    private Long targetId;
}
