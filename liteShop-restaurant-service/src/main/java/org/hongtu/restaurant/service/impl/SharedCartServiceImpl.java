package org.hongtu.restaurant.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.DishCategoryMapper;
import org.hongtu.restaurant.DAO.DishMapper;
import org.hongtu.restaurant.DAO.TableSessionMapper;
import org.hongtu.restaurant.DTO.Dish;

import org.hongtu.restaurant.DTO.TableSession;
import org.hongtu.restaurant.VO.cart.AddCartItemRequest;
import org.hongtu.restaurant.VO.cart.CartItemVO;
import org.hongtu.restaurant.VO.cart.UpdateCartItemRequest;
import org.hongtu.restaurant.enums.SessionStatusEnum;
import org.hongtu.restaurant.service.SharedCartService;
import org.hongtu.restaurant.service.TableSessionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.hongtu.enums.CodeMsg.DINNER_NOT_EXIST;
import static org.hongtu.enums.CodeMsg.DINNER_STATUS_ERROR;

/**
 * 共享购物车服务实现
 * 使用内存存储，购物车数据与会话绑定
 *
 * @author Ht
 * @since 2025-11-05
 */
@Slf4j
@Deprecated(forRemoval = false)
public class SharedCartServiceImpl implements SharedCartService {

    @Resource
    private DishMapper dishMapper;
    @Resource
    DishCategoryMapper dishCategoryMapper;
    @Resource
    TableSessionService tableSessionService;

    /**
     * 购物车存储
     * Key: sessionNo（会话编号）
     * Value: Map<cartItemId, CartItemVO>（该会话的购物车）
     */
    private final Map<String, Map<String, CartItemVO>> cartStorage = new ConcurrentHashMap<>();

    @Override
    public CartItemVO addItem(AddCartItemRequest request) {
        //1.会话有效性验证
        tableSessionService.validateSession(request.getSessionNo());

        // 2. 查询菜品信息
        Dish dish = dishMapper.selectById(request.getDishId());
        if (dish == null) {
            throw new RuntimeException("菜品不存在");
        }

        // 3. 获取或创建购物车
        Map<String, CartItemVO> cart = cartStorage.computeIfAbsent(
                request.getSessionNo(),
                k -> new ConcurrentHashMap<>()
        );

        // 4. 生成购物车项ID（菜品ID + 规格文本的组合）
        String cartItemId = generateCartItemId(request.getDishId(), request.getSpecText());

        // 5. 检查是否已存在相同商品（相同菜品+相同规格）
        CartItemVO existingItem = cart.get(cartItemId);
        if (existingItem != null) {
            // 如果已存在，累加数量
            existingItem.setQuantity(existingItem.getQuantity() + request.getQuantity());
            existingItem.setTotalPrice(existingItem.getPrice().multiply(new BigDecimal(existingItem.getQuantity())));
            existingItem.setUpdatedAt(LocalDateTime.now());
            log.info("购物车商品数量累加: sessionNo={}, cartItemId={}, newQuantity={}", 
                    request.getSessionNo(), cartItemId, existingItem.getQuantity());
            return existingItem;
        }

        // 6. 创建新的购物车项
        BigDecimal price = request.getPrice() != null ? request.getPrice() : request.getPrice();
        BigDecimal totalPrice = price.multiply(new BigDecimal(request.getQuantity()));

        CartItemVO cartItem = CartItemVO.builder()
                .cartItemId(cartItemId)
                .sessionNo(request.getSessionNo())
                .dishId(dish.getId())
                .dishName(dish.getDishName())
                .dishPics(dish.getDishPics())
                .categoryId(dish.getCategoryId())
                .categoryName(dishCategoryMapper.selectById(dish.getCategoryId()).getCategoryName())
                .price(price)
                .basePrice(request.getBasePrice())
                .extraPrice(request.getExtraPrice())
                .quantity(request.getQuantity())
                .totalPrice(totalPrice)
                .specText(request.getSpecText())
                .addedByUserId(request.getUserId())
                .addedByName(request.getUserName())
                .addedAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        cart.put(cartItemId, cartItem);

        log.info("添加商品到购物车: sessionNo={}, dishName={}, quantity={}", 
                request.getSessionNo(), dish.getDishName(), request.getQuantity());

        return cartItem;
    }

    @Override
    public CartItemVO updateItem(UpdateCartItemRequest request) {
        //1.会话有效性验证
        tableSessionService.validateSession(request.getSessionNo());

        // 2. 获取购物车
        Map<String, CartItemVO> cart = cartStorage.get(request.getSessionNo());
        if (cart == null || cart.isEmpty()) {
            throw new RuntimeException("购物车为空");
        }

        // 3. 获取购物车项
        CartItemVO cartItem = cart.get(request.getCartItemId());
        if (cartItem == null) {
            throw new RuntimeException("购物车项不存在");
        }

        // 4. 更新数量
        cartItem.setQuantity(request.getQuantity());
        cartItem.setTotalPrice(cartItem.getPrice().multiply(new BigDecimal(request.getQuantity())));
        cartItem.setUpdatedAt(LocalDateTime.now());

        log.info("更新购物车商品数量: sessionNo={}, cartItemId={}, newQuantity={}", 
                request.getSessionNo(), request.getCartItemId(), request.getQuantity());

        return cartItem;
    }

    @Override
    public boolean removeItem(String sessionNo, String cartItemId) {
        tableSessionService.validateSession(sessionNo);
        Map<String, CartItemVO> cart = cartStorage.get(sessionNo);
        if (cart == null) {
            return false;
        }

        CartItemVO removed = cart.remove(cartItemId);
        log.info("移除购物车商品: sessionNo={}, cartItemId={}, success={}", 
                sessionNo, cartItemId, removed != null);

        return removed != null;
    }

    @Override
    public boolean clearCart(String sessionNo) {
        tableSessionService.validateSession(sessionNo);
        Map<String, CartItemVO> cart = cartStorage.remove(sessionNo);
        log.info("清空购物车: sessionNo={}, success={}", sessionNo, cart != null);
        return cart != null;
    }

    @Override
    public List<CartItemVO> getCartItems(String sessionNo) {
        tableSessionService.validateSession(sessionNo);
        Map<String, CartItemVO> cart = cartStorage.get(sessionNo);
        if (cart == null || cart.isEmpty()) {
            return new ArrayList<>();
        }

        // 按添加时间倒序返回
        return cart.values().stream()
                .sorted(Comparator.comparing(CartItemVO::getAddedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public int getCartItemCount(String sessionNo) {
        tableSessionService.validateSession(sessionNo);
        Map<String, CartItemVO> cart = cartStorage.get(sessionNo);
        if (cart == null || cart.isEmpty()) {
            return 0;
        }

        return cart.values().stream()
                .mapToInt(CartItemVO::getQuantity)
                .sum();
    }

    @Override
    public BigDecimal getCartTotalAmount(String sessionNo) {
        tableSessionService.validateSession(sessionNo);
        Map<String, CartItemVO> cart = cartStorage.get(sessionNo);
        if (cart == null || cart.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return cart.values().stream()
                .map(CartItemVO::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 生成购物车项ID
     * 规则：dishId + "-" + specText的MD5（如果有规格）
     */
    private String generateCartItemId(Long dishId, String specText) {
        if (specText == null || specText.trim().isEmpty()) {
            return dishId.toString();
        }
        String specTextMd5 = SecureUtil.md5(specText);
        // 简化版：直接拼接（生产环境可以用 MD5）
        return dishId + "-" + specTextMd5;
    }
}

