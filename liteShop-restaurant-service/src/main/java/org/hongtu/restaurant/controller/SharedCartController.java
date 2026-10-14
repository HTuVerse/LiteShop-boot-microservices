package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.VO.cart.AddCartItemRequest;
import org.hongtu.restaurant.VO.cart.CartItemVO;
import org.hongtu.restaurant.VO.cart.UpdateCartItemRequest;
import org.hongtu.restaurant.netty.NettyWebSocketPushService;
import org.hongtu.restaurant.service.SharedCartService;
import org.hongtu.restaurant.service.TableInfoService;
import org.hongtu.restaurant.service.TableSessionService;
import org.hongtu.restaurant.security.TableSessionTokenService;
import org.hongtu.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hongtu.enums.CodeMsg.PARAM_ERROR;

/**
 * 共享购物车控制器
 *
 * @author Ht
 * @since 2025-11-05
 */
@Slf4j
@RestController
@RequestMapping("/shared-cart")
public class SharedCartController {

    @Resource
    private SharedCartService sharedCartService;
    @Resource
    private NettyWebSocketPushService webSocketPushService;
    @Resource
    TableSessionService tableSessionService;
    @Resource
    private TableSessionTokenService tableSessionTokenService;
    /**
     * 添加商品到购物车
     */
    @PostMapping("/add")
    @SaIgnore
    public Result<CartItemVO> addItem(@RequestBody AddCartItemRequest request,
                                     @RequestHeader(TableSessionTokenService.HEADER_NAME) String sessionToken) {
        
            // 验证参数
            if (request.getSessionNo() == null || request.getDishId() == null || request.getQuantity() == null) {
                return Result.error(PARAM_ERROR);
            }
            if (request.getQuantity() <= 0) {
                throw new RuntimeException("数量必须大于0");
            }
            verifySessionToken(sessionToken, request.getSessionNo());
            CartItemVO cartItem = sharedCartService.addItem(request);
            // WebSocket 推送：通知同桌其他用户
            webSocketPushService.pushCartItemAdded(request.getSessionNo(), cartItem);

            return Result.success("添加成功", cartItem);
        
    }

    /**
     * 更新购物车商品数量
     */
    @PutMapping("/update")
    @SaIgnore
    public Result<CartItemVO> updateItem(@RequestBody UpdateCartItemRequest request,
                                        @RequestHeader(TableSessionTokenService.HEADER_NAME) String sessionToken) {
        
            if (request.getSessionNo() == null || request.getCartItemId() == null || request.getQuantity() == null) {
                return Result.error(PARAM_ERROR);
            }
            verifySessionToken(sessionToken, request.getSessionNo());
            if (request.getQuantity() <= 0) {
                boolean removed = sharedCartService.removeItem(request.getSessionNo(), request.getCartItemId());
                if (removed) {
                    webSocketPushService.pushCartItemRemoved(request.getSessionNo(), request.getCartItemId());
                }
                return Result.success("商品已移除", null);
            }
            CartItemVO cartItem = sharedCartService.updateItem(request);
            // 如果数量更新为0，商品已被移除，推送移除消息
            if (cartItem == null) {
                webSocketPushService.pushCartItemRemoved(request.getSessionNo(), request.getCartItemId());
                return Result.success("商品已移除", null);
            }

            // WebSocket 推送：通知同桌其他用户
            webSocketPushService.pushCartItemUpdated(request.getSessionNo(), cartItem);

            return Result.success("更新成功", cartItem);
        
    }

    /**
     * 移除购物车商品
     */
    @DeleteMapping("/remove")
    @SaIgnore
    public Result<Boolean> removeItem(
            @RequestParam String sessionNo,
            @RequestParam String cartItemId,
            @RequestHeader(TableSessionTokenService.HEADER_NAME) String sessionToken
    ) {
        
            verifySessionToken(sessionToken, sessionNo);
            tableSessionService.validateSession(sessionNo);
            boolean result = sharedCartService.removeItem(sessionNo, cartItemId);

            if (result) {
                // WebSocket 推送：通知同桌其他用户
                webSocketPushService.pushCartItemRemoved(sessionNo, cartItemId);
            }

            return Result.success("移除成功", result);
        
    }

    /**
     * 清空购物车
     */
    @DeleteMapping("/clear")
    @SaIgnore
    public Result<Boolean> clearCart(@RequestParam String sessionNo,
                                     @RequestHeader(TableSessionTokenService.HEADER_NAME) String sessionToken) {
        
            verifySessionToken(sessionToken, sessionNo);
            tableSessionService.validateSession(sessionNo);

            boolean result = sharedCartService.clearCart(sessionNo);

            if (result) {
                // WebSocket 推送：通知同桌其他用户
                webSocketPushService.pushCartCleared(sessionNo);
            }

            return Result.success("清空成功", result);
        
    }

    /**
     * 获取购物车列表
     */
    @GetMapping("/list")
    @SaIgnore
    public Result<List<CartItemVO>> getCartItems(@RequestParam String sessionNo,
                                                 @RequestHeader(TableSessionTokenService.HEADER_NAME) String sessionToken) {
        
            verifySessionToken(sessionToken, sessionNo);
            tableSessionService.validateSession(sessionNo);
            List<CartItemVO> items = sharedCartService.getCartItems(sessionNo);
            return Result.success("查询成功", items);
        
    }

    /**
     * 获取购物车统计信息
     */
    @GetMapping("/summary")
    @SaIgnore
    public Result<Map<String, Object>> getCartSummary(@RequestParam String sessionNo,
                                                      @RequestHeader(TableSessionTokenService.HEADER_NAME) String sessionToken) {
        
            verifySessionToken(sessionToken, sessionNo);
            tableSessionService.validateSession(sessionNo);
            int itemCount = sharedCartService.getCartItemCount(sessionNo);
            BigDecimal totalAmount = sharedCartService.getCartTotalAmount(sessionNo);

            Map<String, Object> summary = new HashMap<>();
            summary.put("sessionNo", sessionNo);
            summary.put("itemCount", itemCount);
            summary.put("totalAmount", totalAmount);

            return Result.success("查询成功", summary);
        
    }

    private void verifySessionToken(String sessionToken, String sessionNo) {
        tableSessionTokenService.verifyForSession(sessionToken, sessionNo, TenantContext.getTenantId());
    }
}

