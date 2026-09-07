package org.hongtu.restaurant.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.DishCategoryMapper;
import org.hongtu.restaurant.DAO.DishMapper;
import org.hongtu.restaurant.DTO.Dish;
import org.hongtu.restaurant.DTO.DishCategory;
import org.hongtu.restaurant.VO.cart.AddCartItemRequest;
import org.hongtu.restaurant.VO.cart.CartItemVO;
import org.hongtu.restaurant.VO.cart.UpdateCartItemRequest;
import org.hongtu.restaurant.service.SharedCartService;
import org.hongtu.restaurant.service.TableSessionService;
import org.hongtu.tenant.TenantContext;
import org.hongtu.utils.RedisCache;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Redis 共享购物车：按租户和桌台会话隔离，并支持多服务实例。 */
@Slf4j
@Service
public class RedisSharedCartServiceImpl implements SharedCartService {

    private static final String CART_KEY_PREFIX = "restaurant:cart:";
    private static final String CART_LOCK_PREFIX = "restaurant:cart:lock:";
    private static final long CART_TTL_HOURS = 24;
    private static final long LOCK_TTL_SECONDS = 5;
    private static final long LOCK_WAIT_MILLIS = 2_000;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    @Resource
    private DishMapper dishMapper;
    @Resource
    private DishCategoryMapper dishCategoryMapper;
    @Resource
    private TableSessionService tableSessionService;
    @Resource
    private RedisCache redisCache;

    @Override
    public CartItemVO addItem(AddCartItemRequest request) {
        validateAddRequest(request);
        tableSessionService.validateSession(request.getSessionNo());
        Dish dish = dishMapper.selectById(request.getDishId());
        if (dish == null) {
            throw new BusinessException("菜品不存在");
        }

        String cartKey = cartKey(request.getSessionNo());
        String cartItemId = generateCartItemId(request.getDishId(), request.getSpecText());
        return withCartLock(cartKey, () -> {
            CartItemVO existingItem = getCartItem(cartKey, cartItemId);
            if (existingItem != null) {
                int newQuantity = existingItem.getQuantity() + request.getQuantity();
                existingItem.setQuantity(newQuantity);
                existingItem.setTotalPrice(existingItem.getPrice().multiply(BigDecimal.valueOf(newQuantity)));
                existingItem.setUpdatedAt(LocalDateTime.now());
                saveCartItem(cartKey, existingItem);
                return existingItem;
            }

            BigDecimal basePrice = dish.getPrice() != null ? dish.getPrice() : dish.getOriginalPrice();
            if (basePrice == null || basePrice.signum() < 0) {
                throw new BusinessException("菜品价格配置异常");
            }
            // 下单时仍会按服务端规格配置二次校验；这里不接受前端直接指定总价。
            BigDecimal extraPrice = request.getExtraPrice() == null ? BigDecimal.ZERO : request.getExtraPrice();
            if (extraPrice.signum() < 0) {
                throw new BusinessException("规格加价不能小于0");
            }
            BigDecimal price = basePrice.add(extraPrice);
            DishCategory category = dish.getCategoryId() == null
                    ? null : dishCategoryMapper.selectById(dish.getCategoryId());
            CartItemVO cartItem = CartItemVO.builder()
                    .cartItemId(cartItemId)
                    .sessionNo(request.getSessionNo())
                    .dishId(dish.getId())
                    .dishName(dish.getDishName())
                    .dishPics(dish.getDishPics())
                    .categoryId(dish.getCategoryId())
                    .categoryName(category == null ? null : category.getCategoryName())
                    .price(price)
                    .basePrice(basePrice)
                    .extraPrice(extraPrice)
                    .quantity(request.getQuantity())
                    .totalPrice(price.multiply(BigDecimal.valueOf(request.getQuantity())))
                    .specText(request.getSpecText())
                    .addedByUserId(request.getUserId())
                    .addedByName(request.getUserName())
                    .addedAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            saveCartItem(cartKey, cartItem);
            log.info("添加购物车商品: tenantId={}, sessionNo={}, dishId={}, quantity={}",
                    TenantContext.getTenantId(), request.getSessionNo(), dish.getId(), request.getQuantity());
            return cartItem;
        });
    }

    @Override
    public CartItemVO updateItem(UpdateCartItemRequest request) {
        if (request == null || request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BusinessException("商品数量必须大于0");
        }
        tableSessionService.validateSession(request.getSessionNo());
        String cartKey = cartKey(request.getSessionNo());
        return withCartLock(cartKey, () -> {
            CartItemVO cartItem = getCartItem(cartKey, request.getCartItemId());
            if (cartItem == null) {
                throw new BusinessException("购物车项不存在");
            }
            cartItem.setQuantity(request.getQuantity());
            cartItem.setTotalPrice(cartItem.getPrice().multiply(BigDecimal.valueOf(request.getQuantity())));
            cartItem.setUpdatedAt(LocalDateTime.now());
            saveCartItem(cartKey, cartItem);
            return cartItem;
        });
    }

    @Override
    public boolean removeItem(String sessionNo, String cartItemId) {
        tableSessionService.validateSession(sessionNo);
        String cartKey = cartKey(sessionNo);
        return withCartLock(cartKey, () -> {
            boolean existed = getCartItem(cartKey, cartItemId) != null;
            if (existed) {
                redisCache.delCacheMapValue(cartKey, cartItemId);
                refreshCartTtl(cartKey);
            }
            return existed;
        });
    }

    @Override
    public boolean clearCart(String sessionNo) {
        tableSessionService.validateSession(sessionNo);
        return redisCache.deleteObject(cartKey(sessionNo));
    }

    @Override
    public List<CartItemVO> getCartItems(String sessionNo) {
        tableSessionService.validateSession(sessionNo);
        Map<String, String> cart = redisCache.getCacheMap(cartKey(sessionNo));
        if (cart == null || cart.isEmpty()) {
            return new ArrayList<>();
        }
        return cart.values().stream()
                .map(value -> JSON.parseObject(value, CartItemVO.class))
                .sorted(Comparator.comparing(CartItemVO::getAddedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public int getCartItemCount(String sessionNo) {
        return getCartItems(sessionNo).stream().mapToInt(CartItemVO::getQuantity).sum();
    }

    @Override
    public BigDecimal getCartTotalAmount(String sessionNo) {
        return getCartItems(sessionNo).stream()
                .map(CartItemVO::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void validateAddRequest(AddCartItemRequest request) {
        if (request == null || request.getDishId() == null || request.getSessionNo() == null
                || request.getSessionNo().isBlank()) {
            throw new BusinessException("会话号和菜品ID不能为空");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new BusinessException("商品数量必须大于0");
        }
    }

    private String cartKey(String sessionNo) {
        if (sessionNo == null || sessionNo.isBlank()) {
            throw new BusinessException("会话号不能为空");
        }
        return CART_KEY_PREFIX + TenantContext.getTenantId() + ":" + sessionNo;
    }

    private CartItemVO getCartItem(String cartKey, String cartItemId) {
        if (cartItemId == null || cartItemId.isBlank()) {
            return null;
        }
        String value = redisCache.getCacheMapValue(cartKey, cartItemId);
        return value == null ? null : JSON.parseObject(value, CartItemVO.class);
    }

    private void saveCartItem(String cartKey, CartItemVO cartItem) {
        redisCache.setCacheMapValue(cartKey, cartItem.getCartItemId(), JSON.toJSONString(cartItem));
        refreshCartTtl(cartKey);
    }

    private void refreshCartTtl(String cartKey) {
        redisCache.expire(cartKey, CART_TTL_HOURS, TimeUnit.HOURS);
    }

    private <T> T withCartLock(String cartKey, Supplier<T> action) {
        String lockKey = CART_LOCK_PREFIX + cartKey.substring(CART_KEY_PREFIX.length());
        String lockToken = UUID.randomUUID().toString();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(LOCK_WAIT_MILLIS);
        boolean acquired = false;
        try {
            while (!(acquired = Boolean.TRUE.equals(redisCache.redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockToken, LOCK_TTL_SECONDS, TimeUnit.SECONDS)))) {
                if (System.nanoTime() >= deadline) {
                    throw new BusinessException("购物车操作繁忙，请稍后重试");
                }
                Thread.sleep(20);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("购物车操作已取消");
        } finally {
            if (acquired) {
                redisCache.redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockToken);
            }
        }
    }

    private String generateCartItemId(Long dishId, String specText) {
        if (specText == null || specText.trim().isEmpty()) {
            return dishId.toString();
        }
        return dishId + "-" + SecureUtil.md5(specText);
    }
}
