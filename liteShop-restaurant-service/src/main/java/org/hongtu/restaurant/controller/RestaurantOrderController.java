package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.*;
import org.hongtu.restaurant.VO.PaymentStatusVO;
import org.hongtu.restaurant.VO.RefundDishVO;
import org.hongtu.restaurant.VO.RestaurantOrderVO;
import org.hongtu.restaurant.enums.OrderStatusEnum;
import org.hongtu.restaurant.enums.PaymentModeEnum;
import org.hongtu.restaurant.enums.PaymentTypeEnum;
import org.hongtu.restaurant.service.RestaurantOrderService;
import org.hongtu.restaurant.service.TableSessionService;
import org.hongtu.restaurant.security.TableSessionTokenService;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hongtu.Result.Result.CODE_UNAUTHORIZED;

/**
 * 餐厅订单表 前端控制器
 *
 * @author Ht
 * @since 2025-09-19
 */
@Slf4j
@RestController
@RequestMapping("/restaurant-order")
public class RestaurantOrderController {

    @Resource
    private RestaurantOrderService restaurantOrderService;
    @Resource
    private TableSessionService tableSessionService;
    @Resource
    private TableSessionTokenService tableSessionTokenService;
    @Resource
    private UserFeignClient userFeignClient;
    @Resource
    private HttpServletRequest currentRequest;
    @Resource private org.hongtu.restaurant.config.TenantRestaurantConfig tenantRestaurantConfig;

    /** 顾客端读取当前支付模式，用于显示正确的支付入口。 */
    @GetMapping("/paymentMode")
    public Result<Map<String, Boolean>> paymentMode() {
        return Result.success("获取支付模式成功", Map.of("sandbox", tenantRestaurantConfig.sandboxEnabled(org.hongtu.tenant.TenantContext.getTenantId())));
    }

    /** 沙箱确认仅在服务端明确开启时可用，并复用真实支付的订单入账规则。 */
    @PostMapping("/sandboxPay")
    public Result<Boolean> sandboxPay(@RequestParam Long orderId,
                                      @RequestParam(defaultValue = "1") Integer paymentType) {
        if (!tenantRestaurantConfig.sandboxEnabled(org.hongtu.tenant.TenantContext.getTenantId())) throw new org.hongtu.exception.BusinessException(403, "沙箱支付未启用");
        PaymentTypeEnum channel = PaymentTypeEnum.getByCode(paymentType);
        if (channel != PaymentTypeEnum.WECHAT && channel != PaymentTypeEnum.ALIPAY) {
            return Result.error("沙箱仅支持微信或支付宝支付");
        }
        requireOrderAccess(orderId, null, "settle");
        boolean paid = restaurantOrderService.confirmSandboxPayment(orderId, channel);
        return paid ? Result.success("沙箱支付成功", true) : Result.error("订单当前无法支付");
    }

    /**
     * 创建订单（从购物车生成订单）
     * @param request 创建订单请求（包含购物车商品和总价）
     * @return 创建的订单信息（包含订单明细）
     */
    @PostMapping("/create")
    public Result<RestaurantOrderVO> createOrder(
            @RequestBody CreateOrderRequest request,
            @RequestHeader(value = TableSessionTokenService.HEADER_NAME, required = false) String sessionToken) {
        try {
            // 参数校验
            if (StrUtil.isBlank(request.getIdempotencyKey())
                    || request.getIdempotencyKey().length() < 8
                    || request.getIdempotencyKey().length() > 64) {
                return Result.error("下单幂等键长度必须为8到64个字符");
            }
            if (request.getCartItems() == null || request.getCartItems().isEmpty()) {
                return Result.error("购物车不能为空");
            }
            if (request.getTotalPrice() == null || request.getTotalPrice().doubleValue() < 0) {
                return Result.error("订单金额不正确");
            }
            boolean cashierOperator = isCashierOperator();
            if (cashierOperator) org.hongtu.security.OperationPermission.order("add-items");
            // 收银员未指定会员时订单不归属操作员；操作员另存审计字段。
            request.setUserId(cashierOperator ? null : StpUtil.getLoginIdAsLong());
            if (!StrUtil.isBlank(request.getSessionNo())) {
                if (!cashierOperator) {
                    tableSessionTokenService.verifyForSession(
                            sessionToken, request.getSessionNo(), org.hongtu.tenant.TenantContext.getTenantId());
                }
                tableSessionService.validateSession(request.getSessionNo());
            }
            // 创建订单
            RestaurantOrderVO response = restaurantOrderService.createOrder(request);
            return Result.success("创建订单成功", response);
        } catch (DuplicateKeyException e) {
            RestaurantOrderVO existing = restaurantOrderService.getByRequestKey(request.getIdempotencyKey());
            return existing == null
                    ? Result.error("订单正在创建，请使用相同幂等键重试")
                    : Result.success("重复请求，返回已创建订单", existing);
        }
    }

    /**
     * 收银员代会员下单。会员归属、优惠券和会员价只使用这里指定的会员，不接受普通下单接口里的 userId。
     */
    @PostMapping("/cashier/proxyOrder")
    @SaCheckPermission(value = {"operations:direct-cashier", "operations:table-cashier", "operations:console"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<RestaurantOrderVO> proxyCreateOrder(@RequestBody CreateOrderRequest request) {
        try {
            if (StrUtil.isBlank(request.getIdempotencyKey())
                    || request.getIdempotencyKey().length() < 8
                    || request.getIdempotencyKey().length() > 64) {
                return Result.error("下单幂等键长度必须为8到64个字符");
            }
            if (request.getUserId() == null) {
                return Result.error("代客下单必须指定会员");
            }
            org.hongtu.security.OperationPermission.order("add-items");
            if (request.getCartItems() == null || request.getCartItems().isEmpty()) {
                return Result.error("购物车不能为空");
            }
            if (FeignResultUtil.unwrap(userFeignClient.getUserById(request.getUserId())) == null) {
                return Result.error("所选会员不存在");
            }
            if (!StrUtil.isBlank(request.getSessionNo())) {
                tableSessionService.validateSession(request.getSessionNo());
            }
            Long operatorId = StpUtil.getLoginIdAsLong();
            RestaurantOrderVO response = restaurantOrderService.createProxyOrder(request, request.getUserId(), operatorId);
            return Result.success("代客下单成功", response);
        } catch (DuplicateKeyException e) {
            RestaurantOrderVO existing = restaurantOrderService.getByRequestKey(request.getIdempotencyKey());
            return existing == null
                    ? Result.error("订单正在创建，请使用相同幂等键重试")
                    : Result.success("重复请求，返回已创建订单", existing);
        }
    }

    private boolean isCashierOperator() {
        return StpUtil.hasRole("SUPER_ADMIN") || List.of(
                "restaurant:order", "operations:direct-cashier",
                "operations:table-cashier", "operations:console")
                .stream().anyMatch(StpUtil::hasPermission);
    }

    /**
     * 追加菜品到现有订单（加菜功能）
     *
     * 使用场景：
     * - 会话模式下，用户在用餐过程中追加菜品
     * - 避免创建多个订单，只在一个订单中追加订单项
     * - 每次追加会生成新的批次号，便于厨房分批制作
     *
     * 接口说明：
     * - 只能追加到未完成、未取消的订单
     * - 会自动更新订单总金额
     * - 通过WebSocket通知同桌其他用户
     *
     * @param request 追加菜品请求
     * @return 更新后的订单信息
     */
    @PostMapping("/addItems")
    public Result<RestaurantOrderVO> addItemsToOrder(@RequestBody AddItemsRequest request) {
        
            // 参数校验
            if (request.getOrderId() == null) {
                return Result.error("订单ID不能为空");
            }
            if (request.getCartItems() == null || request.getCartItems().isEmpty()) {
                return Result.error("追加的菜品列表不能为空");
            }

            requireOrderAccess(request.getOrderId(), request.getSessionNo(), "add-items");

            // 验证会话（如果提供）
            if (request.getSessionNo() != null && !request.getSessionNo().isEmpty()) {
                tableSessionService.validateSession(request.getSessionNo());
            }

            // 追加菜品
            RestaurantOrderVO response = restaurantOrderService.addItemsToOrder(request);
            return Result.success("追加菜品成功", response);

        
    }

    // ================== 退菜功能 ==================

    /**
     * 退菜（管理员操作）
     *
     * 使用场景：
     * - 顾客点错菜品需要退菜
     * - 菜品质量问题需要退菜
     * - 等待时间过长顾客要求退菜
     * - 其他特殊情况需要退菜
     *
     * 功能说明：
     * 1. 支持部分退菜（退部分数量）或全部退菜
     * 2. 自动计算退款金额
     * 3. 更新订单总金额和订单项数量
     * 4. 记录退菜原因和操作人
     * 5. 如果订单已支付，标记需要退款（实际退款需要财务处理）
     * 6. 如果订单未支付，直接减少订单金额
     * 7. 退菜后如果订单金额为0，自动取消订单
     *
     * 业务规则：
     * - 只能退已下单的菜品
     * - 退菜数量不能超过订单项数量
     * - 已完成的订单不允许退菜
     * - 已取消的订单不允许退菜
     * - 必须填写退菜原因
     *
     * @param request 退菜请求
     * @return 退菜结果（包含退款金额、更新后的订单信息）
     */
    @PostMapping("/refundDish")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:order:refund", "operations:direct-cashier:refund", "operations:table-cashier:refund", "operations:console:refund"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<RefundDishVO> refundDish(@RequestBody RefundDishRequest request) {
        
            // 参数校验
            if (request.getOrderId() == null) {
                return Result.error("订单ID不能为空");
            }
            if (request.getRefundItems() == null || request.getRefundItems().isEmpty()) {
                return Result.error("退菜项列表不能为空");
            }
            if (request.getRefundReason() == null || request.getRefundReason().trim().isEmpty()) {
                return Result.error("退菜原因不能为空");
            }
            if (request.getIdempotencyKey() == null
                    || !request.getIdempotencyKey().matches("[A-Za-z0-9_-]{8,64}")) {
                return Result.error("退菜幂等键格式错误");
            }

            // 校验退菜项
            for (RefundDishRequest.RefundItem item : request.getRefundItems()) {
                if (item.getOrderItemId() == null) {
                    return Result.error("订单项ID不能为空");
                }
                if (item.getRefundQuantity() == null || item.getRefundQuantity() <= 0) {
                    return Result.error("退菜数量必须大于0");
                }
            }

            // 审计操作人只取当前登录账号，禁止客户端伪造 operatorId。
            request.setOperatorId(StpUtil.getLoginIdAsLong());

            // 调用业务层处理退菜
            RefundDishVO result = restaurantOrderService.refundDish(request);
            return Result.success("退菜成功", result);

        
    }

    // ================== 赠菜（标记已有订单项） ==================

    /**
     * 标记订单项为赠菜/取消赠菜（桌位模式）
     *
     * 使用场景：
     * - 收银台桌位模式下，从后端加载的订单在购物车中点击某行的「赠菜」按钮时调用
     * - 将已有订单项标记为赠菜：单价 price 不变，优惠金额 = 该行小计（不增加应付金额）
     *
     * 接口说明：
     * - 仅对已存在的订单项生效（传 orderItemIds）
     * - isGift=1：设为赠菜，discountPrice=该行 totalPrice，订单实付金额减少
     * - isGift=0：取消赠菜，discountPrice=0，订单实付金额增加
     *
     * @param request 标记赠菜请求
     * @return 更新后的订单信息
     */
    @PostMapping("/markOrderItemsGift")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:order:gift", "operations:direct-cashier:gift", "operations:table-cashier:gift", "operations:console:gift"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<RestaurantOrderVO> markOrderItemsGift(@RequestBody MarkOrderItemsGiftRequest request) {
        
            if (request.getOrderId() == null) {
                return Result.error("订单ID不能为空");
            }
            if (request.getOrderItemIds() == null || request.getOrderItemIds().isEmpty()) {
                return Result.error("订单项ID列表不能为空");
            }
            if (request.getIsGift() == null || (request.getIsGift() != 0 && request.getIsGift() != 1)) {
                return Result.error("isGift 必须为 0 或 1");
            }
            if (request.getSessionNo() != null && !request.getSessionNo().isEmpty()) {
                tableSessionService.validateSession(request.getSessionNo());
            }
            RestaurantOrderVO response = restaurantOrderService.markOrderItemsGift(request);
            return Result.success(request.getIsGift() == 1 ? "赠菜成功" : "已取消赠菜", response);
        
    }

    /**
     * 根据会话号查询订单
     * 用于前端判断会话是否已有订单，决定是创建新订单还是追加菜品
     *
     * @param sessionNo 会话编号
     * @return 订单信息（如果存在）
     */
    @GetMapping("/getBySessionNo")
    public Result<RestaurantOrderVO> getOrderBySessionNo(@RequestParam String sessionNo) {
        
            if (sessionNo == null || sessionNo.isEmpty()) {
                return Result.error("会话编号不能为空");
            }
            requireSessionAccess(sessionNo);
            tableSessionService.validateSession(sessionNo);

            RestaurantOrder order = restaurantOrderService.getOrderBySessionNo(sessionNo);
            if (order == null) {
                return Result.error("该会话暂无订单");
            }

            RestaurantOrderVO orderVO = restaurantOrderService.getOrderDetail(order.getId());
            return Result.success("查询成功", orderVO);

        
    }

    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param status 订单状态
     * @return 结果
     */
    @PutMapping("/updateStatus")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:order:status", "operations:direct-cashier:status", "operations:table-cashier:status", "operations:console:status"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updateOrderStatus(@RequestParam Long orderId, @RequestParam Integer status) {
        
            OrderStatusEnum statusEnum = OrderStatusEnum.getByCode(status);
            if (statusEnum == null) {
                return Result.error("无效的订单状态");
            }
            boolean success = restaurantOrderService.updateOrderStatus(orderId, statusEnum);
            return success ? Result.success("更新订单状态成功", true) : Result.error("更新订单状态失败");
        
    }

    /**
     * 取消订单
     * @param orderId 订单ID
     * @param cancelReason 取消原因
     * @return 结果
     */
    @PostMapping("/cancel")
    public Result<Boolean> cancelOrder(@RequestParam Long orderId,
                                       @RequestParam(required = false) String cancelReason) {
        
            requireOrderAccess(orderId, null, "cancel");
            boolean success = restaurantOrderService.cancelOrder(orderId, cancelReason);
            return success ? Result.success("取消订单成功", true) : Result.error("取消订单失败");
        
    }


    /**
     * 发起支付（创建支付订单）
     *
     * @param request 支付请求（订单ID、支付方式、openid等）
     * @return 支付参数（用于前端调起支付）
     */
    @PostMapping("/issuePay")
    public Result<PaymentResponse> createPayment(@RequestBody PaymentRequest request) {
        
            // 参数校验
            if (request.getOrderId() == null) {
                return Result.error("订单ID不能为空");
            }
            requireOrderAccess(request.getOrderId(), null, "settle");
            if (request.getPaymentType() == null) {
                return Result.error("支付方式不能为空");
            }

            // 余额支付：直接处理，无需返回支付参数
            if (request.getPaymentType() == PaymentTypeEnum.BANLANCE_PAY) {
                boolean success = restaurantOrderService.payByBalance(request.getOrderId());
                if (success) {
                    return Result.success("余额支付成功");
                } else {
                    return Result.error("余额支付失败");
                }
            }

            // 同一次支付请求只读取一次租户开关，避免配置修改时两个分支判断不一致。
            boolean sandboxPaymentEnabled = tenantRestaurantConfig.sandboxEnabled(org.hongtu.tenant.TenantContext.getTenantId());

            // 微信支付需要openid
            if (request.getPaymentType() == PaymentTypeEnum.WECHAT &&
                    !sandboxPaymentEnabled && (request.getOpenId() == null || request.getOpenId().isEmpty())) {
                return Result.error("微信支付需要提供openid");
            }

            if (sandboxPaymentEnabled && (request.getPaymentType() == PaymentTypeEnum.WECHAT
                    || request.getPaymentType() == PaymentTypeEnum.ALIPAY)) {
                RestaurantOrder order = restaurantOrderService.getById(request.getOrderId());
                if (order == null) return Result.error("订单不存在");
                if (order.getPaymentMode() != PaymentModeEnum.ONLINE) {
                    return Result.error("该订单采用线下支付，请到收银台结算");
                }
                return Result.success("沙箱支付待确认", PaymentResponse.builder()
                        .orderId(order.getId()).orderNo(order.getOrderNo())
                        .payAmount(order.getActualAmount()).paymentType(request.getPaymentType().getCode())
                        .sandbox(true).build());
            }

            // 调用业务层处理（第三方支付）
            PaymentResponse response = restaurantOrderService.createPayment(
                    request.getOrderId(),
                    request.getPaymentType().getCode(),
                    request.getOpenId(),
                    request.getPaymentType() == PaymentTypeEnum.ALIPAY
                            ? validatedPaymentReturnUrl(request.getReturnUrl()) : null
            );

            return Result.success("发起支付成功", response);

        
    }

    private String validatedPaymentReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) return null;
        try {
            URI target = URI.create(returnUrl);
            String requestOrigin = currentRequest.getHeader("Origin");
            URI origin = requestOrigin == null ? null : URI.create(requestOrigin);
            if (origin == null || target.getHost() == null || target.getUserInfo() != null
                    || !target.getScheme().equalsIgnoreCase(origin.getScheme())
                    || !target.getHost().equalsIgnoreCase(origin.getHost())
                    || target.getPort() != origin.getPort()
                    || (!"https".equalsIgnoreCase(target.getScheme())
                        && !("http".equalsIgnoreCase(target.getScheme())
                            && ("localhost".equalsIgnoreCase(target.getHost())
                                || "127.0.0.1".equals(target.getHost()))))) {
                throw new IllegalArgumentException("支付返回地址无效");
            }
            return target.toString();
        } catch (RuntimeException exception) {
            throw new org.hongtu.exception.BusinessException("支付返回地址无效");
        }
    }

    /**
     * 查询支付状态
     * 用于前端轮询查询订单是否支付成功
     *
     * @param orderId 订单ID
     * @return 支付状态信息
     */
    @GetMapping("/queryPayStatus")
    public Result<PaymentStatusVO> getPaymentStatus(@RequestParam Long orderId,
                                                    @RequestParam(required = false) String sessionNo) {
        
            // 参数校验
            if (orderId == null) {
                return Result.error("订单ID不能为空");
            }
            requireOrderAccess(orderId, sessionNo, "");

            // 调用业务层处理
            PaymentStatusVO statusInfo = restaurantOrderService.getPaymentStatus(orderId);
            return Result.success("查询支付状态成功", statusInfo);

        
    }


    /**
     * 主动查询支付结果（从支付平台查询）
     * 当异步回调失败或延迟时，可以主动查询支付平台
     *
     * @param orderId 订单ID
     * @return 支付结果
     */
    @PostMapping("/onlineQueryPayResult")
    public Result<Boolean> queryPaymentResult(@RequestParam Long orderId) {
        
            // 参数校验
            if (orderId == null) {
                return Result.error("订单ID不能为空");
            }
            requireOrderAccess(orderId, null, "settle");

            // 调用业务层处理
            boolean isPaid = restaurantOrderService.queryAndUpdatePaymentStatus(orderId);
            return Result.success(isPaid ? "订单支付成功" : "订单未支付", isPaid);

        
    }

    /**
     * 微信支付回调接口（由微信支付平台异步调用）
     *
     * 【重要】配置微信支付回调地址时，设置为：
     * https://your-domain.com/restaurant-order/wechatPay/notify/{tenantId}
     *
     * 接口说明：
     * 1. 此接口由微信支付平台在支付成功后异步调用
     * 2. 使用 @SaIgnore 注解跳过登录认证（支付平台无法携带token）
     * 3. 微信回调使用POST方式，Content-Type为application/json
     * 4. 需要验证签名、解密数据、更新订单状态
     *
     * 返回格式要求：
     * - 成功：{"code": "SUCCESS", "message": "成功"}
     * - 失败：{"code": "FAIL", "message": "错误描述"}
     *
     * 注意事项：
     * - 必须在15秒内返回响应，否则微信会重试（最多3次）
     * - 返回失败时，微信会在一段时间后重试回调
     * - 业务逻辑需要做幂等性处理，避免重复处理同一订单
     *
     * @param request 请求对象（包含签名相关的请求头）
     * @param requestBody 请求体（加密的回调数据）
     * @return 处理结果（JSON格式字符串）
     */
    @PostMapping("/wechatPay/notify")
    @SaIgnore
    public String handleWeChatPayNotifyLegacy() {
        return "{\"code\": \"FAIL\", \"message\": \"缺少租户\"}";
    }

    /**
     * 微信支付回调。路径中的租户只用于选择密钥。
     */
    @PostMapping("/wechatPay/notify/{tenantId}")
    @SaIgnore
    public String handleWeChatPayNotify(@PathVariable Long tenantId, HttpServletRequest request,
                                        @RequestBody String requestBody) {
        try {
            String signature = request.getHeader("Wechatpay-Signature");
            String timestamp = request.getHeader("Wechatpay-Timestamp");
            String nonce = request.getHeader("Wechatpay-Nonce");
            String serial = request.getHeader("Wechatpay-Serial");
            if (signature == null || signature.isEmpty()) {
                return "{\"code\": \"FAIL\", \"message\": \"签名缺失\"}";
            }
            if (timestamp == null || timestamp.isEmpty()) {
                return "{\"code\": \"FAIL\", \"message\": \"时间戳缺失\"}";
            }
            if (nonce == null || nonce.isEmpty()) {
                return "{\"code\": \"FAIL\", \"message\": \"随机串缺失\"}";
            }
            if (serial == null || serial.isEmpty()) {
                return "{\"code\": \"FAIL\", \"message\": \"证书序列号缺失\"}";
            }
            if (requestBody == null || requestBody.isEmpty()) {
                return "{\"code\": \"FAIL\", \"message\": \"请求体为空\"}";
            }
            return restaurantOrderService.handleWeChatPayNotify(
                    tenantId, requestBody, signature, timestamp, nonce, serial);
        } catch (Exception e) {
            return "{\"code\": \"FAIL\", \"message\": \"系统异常\"}";
        }
    }

    /**
     * 微信退款回调。核对退款单、原订单和金额后才入账。
     */
    @PostMapping("/wechatPay/refund-notify/{tenantId}")
    @SaIgnore
    public String handleWeChatRefundNotify(@PathVariable Long tenantId, HttpServletRequest request,
                                           @RequestBody String requestBody) {
        try {
            return restaurantOrderService.handleWeChatRefundNotify(tenantId, requestBody,
                    request.getHeader("Wechatpay-Signature"), request.getHeader("Wechatpay-Timestamp"),
                    request.getHeader("Wechatpay-Nonce"), request.getHeader("Wechatpay-Serial"));
        } catch (Exception e) {
            return "{\"code\": \"FAIL\", \"message\": \"系统异常\"}";
        }
    }

    /**
     * 支付宝支付回调接口（由支付宝平台异步调用）
     *
     * 【重要】配置支付宝回调地址时，设置为：
     * https://your-domain.com/restaurant-order/alipayPay/notify/{tenantId}
     *
     * @param request 请求对象
     * @return 处理结果（支付宝要求返回"success"或"failure"）
     */
    @PostMapping("/alipayPay/notify")
    @SaIgnore
    public String handleAlipayNotifyLegacy() {
        return "failure";
    }

    /**
     * 支付宝支付回调。路径中的租户只用于选择密钥。
     */
    @PostMapping("/alipayPay/notify/{tenantId}")
    @SaIgnore
    public String handleAlipayNotify(@PathVariable Long tenantId, HttpServletRequest request) {
        try {
            return restaurantOrderService.handleAlipayNotify(tenantId, readNotifyParams(request));
        } catch (Exception e) {
            return "failure";
        }
    }

    /**
     * 支付宝退款通知。核对退款单后再入账。
     */
    @PostMapping("/alipayPay/refund-notify/{tenantId}")
    @SaIgnore
    public String handleAlipayRefundNotify(@PathVariable Long tenantId, HttpServletRequest request) {
        try {
            return restaurantOrderService.handleAlipayRefundNotify(tenantId, readNotifyParams(request));
        } catch (Exception e) {
            return "failure";
        }
    }

    private Map<String, String> readNotifyParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    /**
     * 处理支付超时
     * 当用户在规定时间内未完成支付时调用
     *
     * @param orderId 订单ID
     * @return 处理结果
     */
    @PostMapping("/payTimeOut")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:order:cancel", "operations:direct-cashier:cancel", "operations:table-cashier:cancel", "operations:console:cancel"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> handlePaymentTimeout(@RequestParam Long orderId,
                                                @RequestParam(required = false) String reason) {
        
            // 参数校验
            if (orderId == null) {
                return Result.error("订单ID不能为空");
            }

            // 调用业务层处理
            boolean success = restaurantOrderService.handlePaymentTimeout(orderId, reason);
            return success ? Result.success("支付超时处理成功", true) : Result.error("支付超时处理失败");

        
    }

    /**
     * 处理支付失败
     * 当支付过程中发生错误时调用
     *
     * @param orderId 订单ID
     * @param reason 失败原因
     * @return 处理结果
     */
    @PostMapping("/payError")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:order:cancel", "operations:direct-cashier:cancel", "operations:table-cashier:cancel", "operations:console:cancel"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> handlePaymentError(@RequestParam Long orderId,
                                              @RequestParam(required = false) String reason) {
        
            // 参数校验
            if (orderId == null) {
                return Result.error("订单ID不能为空");
            }

            // 调用业务层处理
            boolean success = restaurantOrderService.handlePaymentError(orderId, reason);
            return success ? Result.success("支付失败处理成功", true) : Result.error("支付失败处理失败");

        
    }

    /**
     * 根据ID删除订单
     * @param id 订单ID
     * @return 结果
     */
    @DeleteMapping("/delete")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:order:delete", "operations:direct-cashier:delete", "operations:table-cashier:delete", "operations:console:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deleteOrder(@RequestParam Long id) {
        
            boolean success = restaurantOrderService.removeById(id);
            return success ? Result.success("删除订单成功", true) : Result.error("删除订单失败");
        
    }

    /**
     * 批量删除订单
     * @param ids 订单ID列表
     * @return 结果
     */
    @DeleteMapping("/batchDelete")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:order:delete", "operations:direct-cashier:delete", "operations:table-cashier:delete", "operations:console:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> batchDeleteOrder(@RequestBody List<Long> ids) {
        
            if (ids == null || ids.isEmpty()) {
                return Result.error("订单ID列表不能为空");
            }
            boolean success = restaurantOrderService.removeByIds(ids);
            return success ? Result.success("批量删除订单成功", true) : Result.error("批量删除订单失败");
        
    }

    /**
     * 分页查询订单列表(管理员) - 包含订单明细
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param storeId 门店ID
     * @param tableId 餐桌ID
     * @param userId 用户ID
     * @param orderType 订单类型
     * @param status 订单状态
     * @param paymentStatus 支付状态
     * @param phoneNumber 手机号
     * @return 分页订单列表（包含订单明细）
     */
    @GetMapping("/pageList")
    @SaCheckPermission(value = {"restaurant:order", "operations:direct-cashier", "operations:table-cashier", "operations:console"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<RestaurantOrderVO>> getOrderPage(
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long tableId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer orderType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer paymentStatus,
            @RequestParam(required = false) String phoneNumber
    ) {
        
            Page<RestaurantOrderVO> page = restaurantOrderService.getOrderPage(
                    currentPage, pageSize, storeId, tableId, userId,
                    orderType, status, paymentStatus, phoneNumber
            );
            return Result.success("查询订单列表成功", page);
        
    }

    /**
     * 根据用户ID或会话号分页查询订单列表 - 包含订单明细
     *
     * 查询逻辑：
     * 1. 如果提供了sessionNo，优先按会话号查询（会话进行中，所有参与者可见）
     * 2. 如果没有sessionNo，按userId查询（历史订单，只有开桌者可见）
     *
     * @param sessionNo 会话编号（可选，用于查询会话中的共享订单）
     * @param SceneType 场景类型
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @return 分页订单列表（包含订单明细）
     */
    @GetMapping("/userOrders")
    public Result<Page<RestaurantOrderVO>> getOrdersByUserId(
            @RequestParam(required = false) String sessionNo,
            @RequestParam(required = false) Integer SceneType,
            @RequestParam(defaultValue = "1") Integer currentPage,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        
            // 如果没有传userId，使用当前登录用户ID
            Long userId = StpUtil.getLoginIdAsLong();
            if(userId==null){
                return Result.error(CODE_UNAUTHORIZED,"请先登录");
            }
            if (StrUtil.isNotBlank(sessionNo)) {
                requireSessionAccess(sessionNo);
                tableSessionService.validateSession(sessionNo);
            }
            Page<RestaurantOrderVO> orders = restaurantOrderService.getOrdersByUserId(
                    userId, sessionNo, SceneType, currentPage, pageSize);
            return Result.success("查询用户订单成功", orders);
        
    }


    /**
     * 获取订单详情（包含订单明细）
     * @param orderId 订单ID
     * @return 订单详情
     */
    @GetMapping("/getOrderDetail")
    public Result<RestaurantOrderVO> getOrderDetail(@RequestParam Long orderId,
                                                     @RequestParam(required = false) String sessionNo) {
        if (orderId == null){
            return Result.error("订单ID不能为空");
        }
        
            requireOrderAccess(orderId, sessionNo, "");
            RestaurantOrderVO orderDetail = restaurantOrderService.getOrderDetail(orderId);
            return Result.success("查询订单详情成功", orderDetail);
        
    }

    // ================== 优惠券计算接口 ==================

    /**
     * 计算优惠券的优惠金额（支持单张或多张）
     * 后端自动校验：有效性、是否属于用户、叠加规则、互斥性
     *
     * 返回数据：
     * - usable: 是否全部可用
     * - reason: 不可用原因（usable=false时）
     * - totalDiscountAmount: 总优惠金额
     * - couponDetails: 各优惠券明细列表
     *   - userCouponId/couponId/couponName/couponType/couponTypeDesc
     *   - discountAmount: 优惠金额（满减券返回面值，菜品免单券返回关联菜品总价，整单折扣券返回0需前端根据value和订单金额计算）
     *   - value: 券面值/折扣率
     *   - minAmount: 最低消费金额
     *   - dishItems: 关联菜品列表（仅菜品免单券）
     *
     * @param userCouponIds 用户优惠券ID列表（支持单个或多个）
     * @return 计算结果
     */
    @GetMapping("/calc-coupon-discount")
    public Result calcCouponDiscount(@RequestParam List<Long> userCouponIds,
                                     @RequestParam BigDecimal orderTotalAmount,
                                     @RequestParam(required = false) Long storeId) {
        
            Long userId = StpUtil.getLoginIdAsLong();
            return restaurantOrderService.calcCouponDiscount(userId, userCouponIds, orderTotalAmount, storeId);
        
    }

    /**
     * 普通用户只能操作自己的订单；桌台共享操作必须同时提供并匹配有效会话号。
     * 管理员仍需先通过 MyBatis 的 tenant_id / store_id 数据范围过滤。
     */
    private void requireOrderAccess(Long orderId, String sessionNo, String operation) {
        RestaurantOrder order = restaurantOrderService.getById(orderId);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在或无权访问");
        }
        if (StpUtil.hasRole("SUPER_ADMIN") || java.util.List.of(
                "restaurant:order", "operations:direct-cashier", "operations:table-cashier", "operations:console")
                .stream().anyMatch(code -> StpUtil.hasPermission(operation.isEmpty() ? code : code + ":" + operation))) {
            return;
        }

        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (Objects.equals(currentUserId, order.getUserId())) {
            return;
        }
        if (StrUtil.isNotBlank(sessionNo) && Objects.equals(sessionNo, order.getSessionNo())) {
            requireSessionAccess(sessionNo);
            tableSessionService.validateSession(sessionNo);
            return;
        }
        throw new SecurityException("无权访问该订单");
    }

    private void requireSessionAccess(String sessionNo) {
        if (isCashierOperator()) {
            return;
        }
        tableSessionTokenService.verifyForSession(
                currentRequest.getHeader(TableSessionTokenService.HEADER_NAME),
                sessionNo,
                org.hongtu.tenant.TenantContext.getTenantId());
    }

    // ================== 收银台专用接口 ==================

    /**
     * 收银台-扫码支付下单（预留接口，暂未开放）
     *
     * 使用场景：
     * - 管理员在收银台让顾客扫码支付
     * - 支持微信、支付宝扫码支付
     * - 需要对接收银台的扫码枪设备
     *
     * 接口流程：
     * 1. 创建订单（履约状态为已下单、支付状态为未支付）
     * 2. 调用支付平台的扫码付接口
     * 3. 等待支付回调
     * 4. 回调成功后更新订单状态
     *
     * @param request 收银台下单请求
     * @return 支付参数（用于扫码或轮询支付状态）
     */
    @PostMapping("/cashier/scanPay")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:order:settle", "operations:direct-cashier:settle", "operations:table-cashier:settle", "operations:console:settle"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> cashierScanPay(@RequestBody CashierOrderRequest request) {
        
            // 参数校验
            if (request.getCartItems() == null || request.getCartItems().isEmpty()) {
                return Result.error("购物车不能为空");
            }
            if (request.getTotalPrice() == null || request.getTotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return Result.error("订单金额不正确");
            }
            if (request.getPaymentType() == null) {
                return Result.error("请选择支付方式");
            }
            if (request.getAuthCode() == null || request.getAuthCode().isBlank()) {
                return Result.error("请扫描顾客付款码");
            }

            // 扫码支付（被扫）：发起付款码扣款，成功后返回已支付订单。
            RestaurantOrderVO orderVO = restaurantOrderService.cashierScanPay(request);
            Map<String, Object> result = new HashMap<>();
            result.put("order", orderVO);
            return Result.success("扫码支付成功", result);
    }

    /**
     * 收银台-直接支付（管理员操作，统一入口）
     *
     * 使用场景：
     * - 管理员在收银台直接创建订单并完成支付（现金/银行卡/线下/余额等）
     * - 传 userId 且按余额支付时：扣会员余额，支付方式记为余额支付
     * - 不传 userId 或非余额：按现金/线下等直接下单，不扣余额
     *
     * 请求参数：
     * - cartItems、totalPrice 必填
     * - userId：选填；传则可为余额支付（业务层校验余额充足）
     * - paymentType：选填；不传时余额支付为 BANLANCE_PAY，否则默认 CASH
     *
     * @param request 收银台下单请求
     * @return 订单信息（包含订单明细）
     */
    @PostMapping("/cashier/directOrder")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:order:settle", "operations:direct-cashier:settle", "operations:table-cashier:settle", "operations:console:settle"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<RestaurantOrderVO> cashierDirectOrder(@RequestBody CashierOrderRequest request) {
        try {
            if (StrUtil.isBlank(request.getIdempotencyKey())
                    || request.getIdempotencyKey().length() < 8
                    || request.getIdempotencyKey().length() > 64) {
                return Result.error("收银幂等键长度必须为8到64个字符");
            }
            if (request.getCartItems() == null || request.getCartItems().isEmpty()) {
                return Result.error("购物车不能为空");
            }
            if (request.getTotalPrice() == null || request.getTotalPrice().compareTo(BigDecimal.ZERO) <= 0) {
                return Result.error("订单金额不正确");
            }

            RestaurantOrderVO orderVO = restaurantOrderService.cashierDirectOrder(request);
            return Result.success("订单创建成功", orderVO);

        } catch (DuplicateKeyException e) {
            RestaurantOrderVO existing = restaurantOrderService.getByRequestKey(request.getIdempotencyKey());
            return existing == null
                    ? Result.error("订单正在创建，请使用相同幂等键重试")
                    : Result.success("重复请求，返回已创建订单", existing);
        }
    }

    /**
     * 收银台-桌位结算（管理员操作）
     *
     * 使用场景：
     * - 桌位会话已通过「提交订单」或「加菜」产生订单后，前台点击结算时调用
     * - 对会话当前订单做线下支付（现金/余额），并结束会话、释放桌位
     *
     * 接口流程：
     * 1. 校验会话存在且进行中
     * 2. 取会话当前订单，无订单则提示先提交订单
     * 3. 校验订单未支付且属于该会话
     * 4. 按支付方式更新订单为已支付（余额时扣减用户余额）
     * 5. 结束会话、更新桌位为空闲
     * 6. 可选打印小票
     *
     * @param request 桌位结算请求（sessionNo、支付方式、实收金额等）
     * @return 订单信息（包含订单明细）
     */
    @PostMapping("/cashier/settleSession")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:order:settle", "operations:direct-cashier:settle", "operations:table-cashier:settle", "operations:console:settle"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<RestaurantOrderVO> cashierSettleSession(@RequestBody CashierSettleSessionRequest request) {
        
            if (StrUtil.isBlank(request.getSessionNo())) {
                return Result.error("会话编号不能为空");
            }
            if (request.getActualAmount() != null && request.getActualAmount().compareTo(BigDecimal.ZERO) < 0) {
                return Result.error("实收金额不正确");
            }

            RestaurantOrderVO orderVO = restaurantOrderService.cashierSettleSession(request);
            return Result.success("结算成功", orderVO);

        
    }

}
