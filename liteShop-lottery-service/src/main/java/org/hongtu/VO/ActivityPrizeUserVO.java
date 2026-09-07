package org.hongtu.VO;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.PrizeTypeEnum;

/**
 * 活动奖品VO - 面向用户端
 * 隐藏了敏感信息（如中奖概率）
 *
 * @author Honghong
 * @since 2025-07-02
 */
@Data
public class ActivityPrizeUserVO {

    /**
     * 奖品ID
     */
    private Long id;
    
    /**
     * 奖品名称
     */
    private String name;
    
    /**
     * 奖品图片URL
     */
    private String imgUrl;
    
    /**
     * 奖品背景颜色
     */
    private String bgColor;
    
    /**
     * 奖品类型
     */
    private PrizeTypeEnum type;
    
    /**
     * 是否启用
     */
    private BooleanEnum enabled;
    
    /**
     * 积分数量（积分奖品时显示）
     */
    private Integer points;
    
    /**
     * 剩余库存（可选择是否展示给用户）
     */
    private Long surplusStock;

    // 注意：不包含以下敏感字段
    // - probability（中奖概率）
    // - couponId（优惠券ID）
    // - totalStock（总库存）
}
