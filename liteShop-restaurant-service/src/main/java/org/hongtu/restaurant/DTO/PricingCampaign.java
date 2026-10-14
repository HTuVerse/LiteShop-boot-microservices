package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.PricingEnums.CampaignType;
import org.hongtu.restaurant.enums.PricingEnums.StackMode;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 可定时、可限定门店/商品/人群的定价活动。 */
@Data
@Accessors(chain = true)
@TableName("pricing_campaign")
public class PricingCampaign implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private String campaignCode;
    private String campaignName;
    private CampaignType campaignType;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
    private Integer priority;
    private String exclusiveGroup;
    private StackMode stackMode;
    private Integer status;
    private Integer version;
    private Integer deleted;
    private Long createdBy;
    private Long updatedBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
