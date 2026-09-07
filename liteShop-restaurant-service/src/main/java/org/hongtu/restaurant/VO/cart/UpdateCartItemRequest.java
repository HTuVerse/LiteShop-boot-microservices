package org.hongtu.restaurant.VO.cart;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新购物车项请求
 *
 * @author Ht
 * @since 2025-11-05
 */
@Data
public class UpdateCartItemRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 购物车项ID
     */
    private String cartItemId;

    /**
     * 会话编号
     */
    private String sessionNo;

    /**
     * 新的数量
     */
    private Integer quantity;
}

