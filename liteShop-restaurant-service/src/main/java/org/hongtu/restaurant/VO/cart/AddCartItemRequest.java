package org.hongtu.restaurant.VO.cart;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 添加购物车项请求
 *
 * @author Ht
 * @since 2025-11-05
 */
@Data
public class AddCartItemRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话编号
     */
    private String sessionNo;

    /**
     * 菜品ID
     */
    private Long dishId;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 规格选项文本（前端显示格式）（分量:大份、辣度:重辣）
     */
    private String specText;

    /**
     * 基本价格
     */
    private BigDecimal basePrice;

    /**
     * 额外价格
     */
    private BigDecimal extraPrice;

    /**
     * 单价（基本价格+额外价格）
     */
    private BigDecimal price;

    /**
     * 添加者用户ID（可选）
     */
    private Long userId;

    /**
     * 添加者名称（可选）
     */
    private String userName;
}

