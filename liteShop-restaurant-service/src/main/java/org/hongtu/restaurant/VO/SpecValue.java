package org.hongtu.restaurant.VO;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 规格选项值
 * @author Ht
 * @since 2025-09-19
 */
@Data
public class SpecValue {
    
    /**
     * 选项值
     */
    private String value;
    
    /**
     * 额外价格
     */
    private BigDecimal extraPrice;
    
    /**
     * 是否默认选中
     */
    private Boolean isDefault;
    
    /**
     * 排序值
     */
    private Integer sortOrder;
    
    /**
     * 图片URL
     */
    private String imageUrl;
} 