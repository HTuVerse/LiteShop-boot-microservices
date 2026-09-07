package org.hongtu.restaurant.VO.cart;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车项视图对象
 *
 * @author Ht
 * @since 2025-11-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 购物车项ID（唯一标识）
     */
    private String cartItemId;

    /**
     * 会话编号
     */
    private String sessionNo;

    /**
     * 菜品ID
     */
    private Long dishId;

    /**
     * 菜品名称
     */
    private String dishName;

    /**
     * 菜品图片
     */
    private String dishPics;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 使用单价（基础价格+额外价格）
     */
    private BigDecimal price;

    /**
     * 基本价格
     * */
    private BigDecimal basePrice;

    /**
     * 额外价格
     * */
    private BigDecimal extraPrice;

    /**
     * 数量
     */
    private Integer quantity;

    /**
     * 小计（单价 × 数量）
     */
    private BigDecimal totalPrice;

    /**
     * 规格选项文本（JSON格式）
     */
    private String specText;

    /**
     * 添加者用户ID
     */
    private Long addedByUserId;

    /**
     * 添加者名称
     */
    private String addedByName;

    /**
     * 添加时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime addedAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}

