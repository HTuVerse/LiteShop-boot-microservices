package org.hongtu.restaurant.service;



import org.hongtu.restaurant.VO.cart.AddCartItemRequest;
import org.hongtu.restaurant.VO.cart.CartItemVO;
import org.hongtu.restaurant.VO.cart.UpdateCartItemRequest;

import java.util.List;

/**
 * 共享购物车服务
 *
 * @author Ht
 * @since 2025-11-05
 */
public interface SharedCartService {

    /**
     * 添加商品到购物车
     * @param request 添加请求
     * @return 购物车项
     */
    CartItemVO addItem(AddCartItemRequest request);

    /**
     * 更新购物车商品数量
     * @param request 更新请求
     * @return 更新后的购物车项
     */
    CartItemVO updateItem(UpdateCartItemRequest request);

    /**
     * 移除购物车商品
     * @param sessionNo 会话编号
     * @param cartItemId 购物车项ID
     * @return 是否成功
     */
    boolean removeItem(String sessionNo, String cartItemId);

    /**
     * 清空购物车
     * @param sessionNo 会话编号
     * @return 是否成功
     */
    boolean clearCart(String sessionNo);

    /**
     * 获取购物车列表
     * @param sessionNo 会话编号
     * @return 购物车项列表
     */
    List<CartItemVO> getCartItems(String sessionNo);

    /**
     * 获取购物车项数量
     * @param sessionNo 会话编号
     * @return 商品数量
     */
    int getCartItemCount(String sessionNo);

    /**
     * 获取购物车总金额
     * @param sessionNo 会话编号
     * @return 总金额
     */
    java.math.BigDecimal getCartTotalAmount(String sessionNo);
}

