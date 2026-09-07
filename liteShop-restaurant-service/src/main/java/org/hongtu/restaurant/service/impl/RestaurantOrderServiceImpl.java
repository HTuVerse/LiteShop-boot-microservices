package org.hongtu.restaurant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.DTO.SysUser;
import org.hongtu.enums.BalanceResourceEnum;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.exception.BusinessException;
import org.hongtu.params.AlipayTradeWapPayRequestParams;
import org.hongtu.params.WeChatPayTradeParam;
import org.hongtu.restaurant.DAO.*;
import org.hongtu.restaurant.DTO.*;
import org.hongtu.restaurant.VO.*;
import org.hongtu.restaurant.convert.RestaurantOrderConverter;
import org.hongtu.restaurant.enums.*;
import org.hongtu.restaurant.netty.NettyWebSocketPushService;
import org.hongtu.restaurant.service.*;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.hongtu.DTO.Coupon;
import org.hongtu.DTO.CouponDishItem;
import org.hongtu.DTO.UserCoupon;
import org.hongtu.enums.CouponStatusEnum;
import org.hongtu.enums.CouponTypeEnum;
import org.hongtu.api.client.CouponFeignClient;
import org.hongtu.api.client.PayFeignClient;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.BalanceChangeDTO;
import org.hongtu.api.dto.WeChatNotifyParseDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hongtu.enums.CodeMsg.ORDER_CANCEL_FAIL;
import static org.hongtu.enums.CodeMsg.ORDER_NOT_EXIST;

/**
 * <p>
 * 餐厅订单表 服务实现类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Service
@Slf4j
public class RestaurantOrderServiceImpl extends ServiceImpl<RestaurantOrderMapper, RestaurantOrder> implements RestaurantOrderService {

    @Resource
    private RestaurantOrderItemService restaurantOrderItemService;
    @Resource
    StoreInfoMapper storeInfoMapper;
    /**
     * 微服务改造：pay/user/coupons 模块的直连注入统一替换为 Feign 客户端
     */
    @Resource
    private PayFeignClient payFeignClient;
    @Resource
    RestaurantOrderConverter restaurantOrderConverter;
    @Resource
    TableSessionService tableSessionService;
    @Resource
    NettyWebSocketPushService webSocketPushService;
    @Resource
    UserFeignClient userFeignClient;
    @Resource
    OrderPrintService orderPrintService;
    @Resource
    private org.hongtu.restaurant.service.DishService dishService;
    @Resource
    private DishSpecOptionMapper dishSpecOptionMapper;
    @Resource
    private PrinterInfoService printerInfoService;
    @Resource
    private OrderTimeoutQueueService orderTimeoutQueueService;
    @Resource
    private CouponFeignClient couponFeignClient;
    @Resource
    private StoreDataScopeService storeDataScopeService;

    /**
     * 创建订单（用户端）
     * @param request 创建订单请求
     * @return 创建的订单（包含订单明细）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestaurantOrderVO createOrder(CreateOrderRequest request) {
        storeDataScopeService.checkStoreAccess(request.getStoreId());
        // 【重要】检查会话是否已有订单，避免重复创建
        if (request.getSessionNo() != null && !request.getSessionNo().isEmpty()) {
            RestaurantOrder existOrder = getOrderBySessionNo(request.getSessionNo());
            if (existOrder != null) {
                throw new BusinessException("该会话已存在订单（订单号：" + existOrder.getOrderNo() +
                        "），请使用追加菜品功能，避免创建重复订单");
            }
        }

        // 1. 验证前端传来的菜品数据（包括原价、规格等，前端始终传原价）
        validateCreateOrderCartItems(request.getCartItems());

        // 2. 后端重新计算原价总金额（防止前端篡改数据）
        BigDecimal calculatedTotalAmount = BigDecimal.ZERO;
        for (CreateOrderRequest.CartItem cartItem : request.getCartItems()) {
            if (cartItem.getIsGift() != null && cartItem.getIsGift() == 1) {
                org.hongtu.security.OperationPermission.order("gift");
                continue; // 赠送菜品不计入总价
            }
            BigDecimal itemPrice = cartItem.getPrice() != null ?
                    cartItem.getPrice() : cartItem.getBasePrice();
            BigDecimal itemTotal = itemPrice.multiply(new BigDecimal(cartItem.getQuantity()));
            calculatedTotalAmount = calculatedTotalAmount.add(itemTotal);
        }
        log.info("后端计算原价总金额：{}", calculatedTotalAmount);

        // 3.【优惠券处理】前端已调用 calcCouponDiscount 接口计算优惠金额
        // 后端直接调用 calcCouponDiscount 方法重新计算，该方法内部已包含所有校验逻辑
        BigDecimal couponDiscountAmount = BigDecimal.ZERO;
        List<Coupon> usedCoupons = new ArrayList<>();
        CouponDiscountResultVO calcData = null; // 用于后续将菜品免单券优惠分摊到订单项

        List<Long> userCouponIds = request.getUserCouponIds();
        if (userCouponIds != null && !userCouponIds.isEmpty()) {
            // 调用优惠券计算方法，内部已包含所有校验（有效性、叠加规则、互斥性等）
            Result calcResult = calcCouponDiscount(request.getUserId(), userCouponIds, calculatedTotalAmount);
            calcData = (CouponDiscountResultVO) calcResult.getData();
            if (!Boolean.TRUE.equals(calcData.getUsable())) {
                throw new BusinessException("优惠券不可用：" + calcData.getReason());
            }
            couponDiscountAmount = calcData.getTotalDiscountAmount();

            // 查询优惠券详情（用于保存订单的优惠券类型信息）
            List<CouponDiscountResultVO.CouponDetailVO> couponDetailsList = calcData.getCouponDetails();
            if (couponDetailsList != null) {
                for (CouponDiscountResultVO.CouponDetailVO detail : couponDetailsList) {
                    Coupon coupon = FeignResultUtil.unwrap(couponFeignClient.getCouponById(detail.getCouponId()));
                    if (coupon != null) {
                        usedCoupons.add(coupon);
                    }
                }
            }

            log.info("优惠券计算完成: couponCount={}, totalDiscount={}", userCouponIds.size(), couponDiscountAmount);
        }

        // 4. 计算实付金额
        BigDecimal actualAmount = calculatedTotalAmount.subtract(couponDiscountAmount);
        if (actualAmount.compareTo(BigDecimal.ZERO) < 0) {
            actualAmount = BigDecimal.ZERO;
        }

        // 5. 验证前端传入的 totalPrice 是否等于后端算出的实付金额（前端传的是优惠后的价格）
        if (request.getTotalPrice() != null &&
                request.getTotalPrice().subtract(actualAmount).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BusinessException(String.format(
                    "订单金额验证失败，前端传入(优惠后)：%s，后端计算实付：%s，原价总计：%s，优惠券抵扣：%s",
                    request.getTotalPrice(), actualAmount,
                    calculatedTotalAmount, couponDiscountAmount
            ));
        }
        log.info("订单金额验证通过，原价：{}，优惠：{}，实付：{}", calculatedTotalAmount, couponDiscountAmount, actualAmount);

        // 6. 生成订单编号
        String orderNo = generateOrderNo();

        // 7. 创建订单主表
        RestaurantOrder order = new RestaurantOrder();
        StoreInfo storeInfo = storeInfoMapper.selectById(request.getStoreId());
        order.setOrderNo(orderNo);
        order.setStoreId(request.getStoreId());
        order.setTableId(request.getTableId());
        order.setUserId(request.getUserId());
        order.setSessionNo(request.getSessionNo());
        order.setOrderType(request.getOrderType());
        order.setTotalAmount(calculatedTotalAmount); // 原价总计
        order.setDiscountAmount(couponDiscountAmount); // 优惠券抵扣金额
        order.setDiningCount(request.getPeopleCount());
        order.setActualAmount(actualAmount); // 实付金额 = 原价 - 优惠券抵扣

        // 设置优惠券信息（多券以逗号分隔存储）
        if (userCouponIds != null && !userCouponIds.isEmpty()) {
            String couponIdStr = userCouponIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            List<CouponTypeEnum> couponTypeList = usedCoupons.stream()
                    .map(Coupon::getType)
                    .collect(Collectors.toList());
            order.setUserCouponIds(couponIdStr);
            order.setCouponTypes(couponTypeList);
        }
        order.setPaymentMode(storeInfo.getPaymentMode());
        order.setNote(request.getNote());
        order.setServiceFee(BigDecimal.ZERO);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 根据支付模式和实付金额设置订单状态
        if (actualAmount.compareTo(BigDecimal.ZERO) == 0) {
            // 实付金额为0（优惠券全额抵扣）：订单直接标记为已支付
            order.setPaymentStatus(PaymentStatusEnum.PAID);
            order.setStatus(OrderStatusEnum.PLACED); // 已下单
            order.setPaymentType(PaymentTypeEnum.COUPON_PAY); // 优惠券全额抵扣
            order.setPaymentTime(LocalDateTime.now());
            log.info("订单实付金额为0，优惠券全额抵扣，自动标记为已支付: orderNo={}", order.getOrderNo());
            // 叫号自取模式：直接生成取餐码
            if (storeInfo.getPickupMode() == pickupModeEnum.NUMBERCALLING) {
                order.setPickupCode(generatePickupCode());
            }
        } else if (storeInfo.getPaymentMode() == PaymentModeEnum.OFFLINE && !StrUtil.isBlank(request.getSessionNo())) {
            // 线下支付+有会话：订单创建后直接标记为已下单状态（待线下结算）
            order.setPaymentStatus(PaymentStatusEnum.UNPAID);
            order.setStatus(OrderStatusEnum.PLACED); // 已下单
            order.setPaymentType(PaymentTypeEnum.OFFLINE_PAY); // 线下支付
            order.setPaymentTime(LocalDateTime.now());

            // 线下支付+叫号自取：直接生成取餐码
            if (storeInfo.getPickupMode() == pickupModeEnum.NUMBERCALLING) {
                order.setPickupCode(generatePickupCode());
            }
        } else {
            // 线上支付 或 线下支付无会话：订单创建后为未支付状态
            order.setPaymentStatus(PaymentStatusEnum.UNPAID);
            order.setStatus(OrderStatusEnum.NOPAID); // 未支付
            // 线上支付的取餐码在支付成功后生成，此处不生成
        }

        // 保存订单主表
        this.save(order);

        // 【优惠券核销】创建订单时立即核销所有优惠券（任一核销失败直接抛异常阻止下单，事务回滚）
        if (userCouponIds != null && !userCouponIds.isEmpty()) {
            for (Long userCouponId : userCouponIds) {
                FeignResultUtil.check(couponFeignClient.verifyCouponOnOrder(userCouponId, order.getId()));
                log.info("优惠券已核销: orderId={}, userCouponId={}", order.getId(), userCouponId);
            }
        }

        // 8. 创建订单明细（首次下单，批次号为1）
        List<RestaurantOrderItem> orderItems = new ArrayList<>();
        if (request.getCartItems() != null && !request.getCartItems().isEmpty()) {
            for (CreateOrderRequest.CartItem cartItem : request.getCartItems()) {
                RestaurantOrderItem orderItem = new RestaurantOrderItem();
                orderItem.setOrderId(order.getId());
                orderItem.setDishId(cartItem.getDishId());
                orderItem.setCategoryId(cartItem.getCategoryId());
                orderItem.setPrice(cartItem.getPrice() != null ? cartItem.getPrice() : cartItem.getBasePrice());
                orderItem.setQuantity(cartItem.getQuantity());

                // 设置赠送标识
                orderItem.setIsGift(cartItem.getIsGift() != null ? cartItem.getIsGift() : 0);

                // 计算原价小计
                BigDecimal itemOriginalTotal = orderItem.getPrice().multiply(new BigDecimal(cartItem.getQuantity()));

                // 如果是赠送菜品：单价不变，小计保留，优惠金额=小计
                if (orderItem.getIsGift() == 1) {
                    orderItem.setDiscountPrice(itemOriginalTotal); // 优惠金额=该行原价
                    orderItem.setTotalPrice(BigDecimal.ZERO);
                } else {
                    orderItem.setTotalPrice(itemOriginalTotal);
                    orderItem.setDiscountPrice(BigDecimal.ZERO);
                }

                // 保存规格信息（作为文本存储）
                orderItem.setSpecOptionsText(cartItem.getSpecText());
                orderItem.setCreatedAt(LocalDateTime.now());
                orderItem.setUpdatedAt(LocalDateTime.now());
                // 设置批次号和加菜时间
                orderItem.setBatchNo(1); // 首次下单，批次号为1
                orderItem.setAddTime(LocalDateTime.now());
                orderItems.add(orderItem);
            }

            // 8.1 【创建订单-菜品优惠券】将菜品免单券等优惠分摊到订单项（设置每项的 discountPrice / totalPrice）
            if (calcData != null && calcData.getCouponDetails() != null && !orderItems.isEmpty()) {
                Map<Long, BigDecimal> freeAmountByDishId = new HashMap<>();
                for (CouponDiscountResultVO.CouponDetailVO detail : calcData.getCouponDetails()) {
                    if (detail.getCouponType() == CouponTypeEnum.DISH_FREE && detail.getDishItems() != null) {
                        for (CouponDiscountResultVO.DishItemVO di : detail.getDishItems()) {
                            if (di.getDishId() != null && di.getPrice() != null) {
                                freeAmountByDishId.merge(di.getDishId(), di.getPrice(), BigDecimal::add);
                            }
                        }
                    }
                }
                for (RestaurantOrderItem item : orderItems) {
                    if (item.getIsGift() != null && item.getIsGift() == 1) {
                        continue;
                    }
                    Long dishId = item.getDishId();
                    BigDecimal remaining = freeAmountByDishId.get(dishId);
                    if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal lineTotal = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
                    BigDecimal deduct = lineTotal.min(remaining);
                    item.setDiscountPrice(deduct);
                    item.setTotalPrice(lineTotal.subtract(deduct));
                    freeAmountByDishId.put(dishId, remaining.subtract(deduct));
                }
                log.info("创建订单-优惠券已分摊到订单项: orderId={}, 优惠金额={}", order.getId(), couponDiscountAmount);
            }

            // 批量保存订单明细
            restaurantOrderItemService.saveBatch(orderItems);
        }
        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(order);

        // 7. 【新增】更新会话的当前订单ID（仅线下支付+会话模式）
        if (!StrUtil.isBlank(order.getSessionNo())) {
            try {
                TableSession tableSession = tableSessionService.getActiveSessionByTableId(order.getTableId());
                if (tableSession != null) {
                    tableSession.setCurrentOrderId(order.getId());
                    tableSession.setUpdatedAt(LocalDateTime.now());
                    tableSessionService.updateById(tableSession);
                    log.info("会话订单ID已更新: sessionNo={}, orderId={}", tableSession.getSessionNo(), order.getId());
                }
            } catch (Exception e) {
                log.warn("更新会话订单ID失败: sessionNo={}, orderId={}, error={}",
                        order.getSessionNo(), order.getId(), e.getMessage());
            }
        }

        // 8. WebSocket 推送：通知同桌其他用户有新订单
        if (order.getTableId() != null&&order.getSessionNo()!=null) {
            try {
                // 获取当前桌位的会话
                var tableSession = tableSessionService.getActiveSessionByTableId(order.getTableId());
                if (tableSession != null) {
                    // 推送新订单创建消息
                    webSocketPushService.pushOrderCreated(tableSession.getSessionNo(), orderVO);
                    // 更新会话统计信息
                    tableSessionService.updateSessionStatistics(tableSession.getSessionNo());
                }
            } catch (Exception e) {
                // WebSocket 推送失败不影响订单创建
                log.error("WebSocket 推送失败: orderId={}, error={}", order.getId(), e.getMessage(), e);
            }
        }
        // 9. 【修正】小票打印逻辑
        // 只有线下支付且有会话的情况下，才在创建订单时打印小票
        // 线上支付需要等支付成功后才打印（在 handlePaymentSuccess 方法中处理）
        if (storeInfo != null && storeInfo.getIsPrint() == BooleanEnum.YES) {
            if (actualAmount.compareTo(BigDecimal.ZERO) == 0) {
                // 实付金额为0（优惠券全额抵扣）：订单已自动标记为已支付，直接打印小票
                printOrderWithStorePrinters(order.getId(), storeInfo.getId());
                log.info("优惠券全额抵扣订单，已打印小票: orderNo={}", order.getOrderNo());
            } else if (storeInfo.getPaymentMode() == PaymentModeEnum.OFFLINE && !StrUtil.isBlank(request.getSessionNo())) {
                // 线下支付 + 有会话：订单创建后直接打印小票
                printOrderWithStorePrinters(order.getId(), storeInfo.getId());
                log.info("线下支付订单创建成功，已打印小票: orderNo={}", order.getOrderNo());
            } else {
                // 线上支付 或 线下支付无会话：不在此处打印，等支付成功后再打印
                log.info("线上支付订单创建成功，等待支付完成后打印小票: orderNo={}", order.getOrderNo());
            }
        }

        // 10. 【新增】将需要支付的订单加入超时队列（30分钟未支付自动取消）
        // 加入超时队列的条件：
        // - 实付金额为0：不需要加入（已自动标记为已支付）
        // - 线上支付模式：所有订单都需要加入（需要用户支付后才生效）
        // - 线下支付模式 + 无会话：需要加入（自助点餐，需要去前台支付）
        // - 线下支付模式 + 有会话：不需要加入（堂食扫码点餐，直接下单成功）
        boolean needTimeoutQueue = false;
        if (actualAmount.compareTo(BigDecimal.ZERO) == 0) {
            // 实付金额为0：不需要加入超时队列（已自动标记为已支付）
            needTimeoutQueue = false;
        } else if (storeInfo.getPaymentMode() == PaymentModeEnum.ONLINE) {
            // 线上支付：所有订单都需要加入超时队列
            needTimeoutQueue = true;
        } else if (storeInfo.getPaymentMode() == PaymentModeEnum.OFFLINE && StrUtil.isBlank(request.getSessionNo())) {
            // 线下支付 + 无会话（自助点餐）：需要加入超时队列
            needTimeoutQueue = true;
        }
        // 线下支付 + 有会话：不需要加入超时队列（已在上面设置为已下单状态）

        if (needTimeoutQueue) {
            try {
                orderTimeoutQueueService.addToTimeoutQueue(order.getId());
                log.info("订单已加入超时队列: orderId={}, orderNo={}, paymentMode={}, hasSession={}",
                        order.getId(), order.getOrderNo(), storeInfo.getPaymentMode(),
                        !StrUtil.isBlank(request.getSessionNo()));
            } catch (Exception e) {
                // 加入队列失败不影响订单创建，仅记录日志
                log.error("订单加入超时队列失败: orderId={}, error={}", order.getId(), e.getMessage());
            }
        } else {
            log.info("订单无需加入超时队列: orderId={}, orderNo={}, paymentMode={}, hasSession={}",
                    order.getId(), order.getOrderNo(), storeInfo.getPaymentMode(),
                    !StrUtil.isBlank(request.getSessionNo()));
        }

        // 11. 使用转换器构建返回对象
        return orderVO;
    }

    /**
     * 分页查询订单列表（包含订单明细）
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
    @Override
    public Page<RestaurantOrderVO> getOrderPage(Integer currentPage, Integer pageSize,
                                                Long storeId, Long tableId, Long userId,
                                                Integer orderType, Integer status,
                                                Integer paymentStatus, String phoneNumber) {
        // 1. 分页查询订单主表
        Page<RestaurantOrder> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<RestaurantOrder> wrapper = new LambdaQueryWrapper<>();

        // 条件查询
        wrapper.eq(storeId != null, RestaurantOrder::getStoreId, storeId)
                .eq(tableId != null, RestaurantOrder::getTableId, tableId)
                .eq(userId != null, RestaurantOrder::getUserId, userId)
                .eq(orderType != null, RestaurantOrder::getOrderType, SceneTypeEnum.getByCode(orderType))
                .eq(status != null, RestaurantOrder::getStatus, OrderStatusEnum.getByCode(status))
                .eq(paymentStatus != null, RestaurantOrder::getPaymentStatus, PaymentStatusEnum.getByCode(paymentStatus))
                .orderByDesc(RestaurantOrder::getCreatedAt);

        Page<RestaurantOrder> orderPage = this.page(page, wrapper);

        // 2. 转换为VO并查询订单明细
        Page<RestaurantOrderVO> voPage = new Page<>(currentPage, pageSize);
        voPage.setTotal(orderPage.getTotal());
        voPage.setRecords(restaurantOrderConverter.toVOList(orderPage.getRecords()));
        return voPage;
    }


    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param status 订单状态
     * @return 是否成功
     */
    @Override
    public boolean updateOrderStatus(Long orderId, OrderStatusEnum status) {
        RestaurantOrder order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException(ORDER_NOT_EXIST.getCode(),"订单不存在");
        }
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());

        // 如果订单完成，设置结束用餐时间
        if (status == OrderStatusEnum.COMPLETED) {
            order.setDiningEndTime(LocalDateTime.now());
        }

        return this.updateById(order);
    }

    /**
     * 取消订单
     * @param orderId 订单ID
     * @param cancelReason 取消原因
     * @return 是否成功
     */
    @Override
    public boolean cancelOrder(Long orderId, String cancelReason) {
        RestaurantOrder order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException(ORDER_NOT_EXIST.getCode(),"订单不存在");
        }

        // 已支付的订单不能取消，需要先退款
        if (order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            throw new BusinessException(ORDER_CANCEL_FAIL.getCode(),"已支付的订单不能取消，请先退款");
        }

        // 已完成或已取消的订单不能再次取消
        if (order.getStatus() == OrderStatusEnum.COMPLETED || order.getStatus() == OrderStatusEnum.CANCELLED) {
            throw new BusinessException(ORDER_CANCEL_FAIL.getCode(),"订单已完成或已取消，无法再次取消");
        }
        order.setCancelReason(cancelReason);
        order.setCancelTime(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        // 关闭支付平台的订单
        if (order.getPaymentStatus() == PaymentStatusEnum.UNPAID && order.getPaymentNo() != null) {
            closePaymentPlatformOrder(order.getOrderNo(), order.getPaymentType());
            order.setCancelReason("用户主动取消订单");
        }

        order.setStatus(OrderStatusEnum.CANCELLED);

        boolean result = this.updateById(order);

        if (result) {
            // 【优惠券返还】取消订单时返还已核销的所有优惠券
            if (StrUtil.isNotBlank(order.getUserCouponIds())) {
                String[] couponIdArr = order.getUserCouponIds().split(",");
                for (String idStr : couponIdArr) {
                    if (StrUtil.isNotBlank(idStr)) {
                        try {
                            Long ucId = Long.parseLong(idStr.trim());
                            FeignResultUtil.check(couponFeignClient.restoreCouponOnCancel(ucId, orderId));
                            log.info("优惠券已返还: orderId={}, userCouponId={}", orderId, ucId);
                        } catch (NumberFormatException e) {
                            log.warn("优惠券ID解析失败: orderId={}, idStr={}", orderId, idStr);
                        }
                    }
                }
            }

            // 取消订单后从超时队列中移除
            try {
                orderTimeoutQueueService.removeFromTimeoutQueue(orderId);
                log.info("订单取消成功，已从超时队列移除: orderId={}, orderNo={}", orderId, order.getOrderNo());
            } catch (Exception e) {
                log.error("从超时队列移除订单失败: orderId={}, error={}", orderId, e.getMessage());
            }
        }

        // WebSocket 推送：通知同桌其他用户订单已取消
        if (result && order.getTableId() != null) {
            try {
                var tableSession = tableSessionService.getActiveSessionByTableId(order.getTableId());
                if (tableSession != null) {
                    // 推送订单取消消息
                    webSocketPushService.pushOrderCancelled(
                            tableSession.getSessionNo(),
                            order.getId(),
                            order.getOrderNo(),
                            cancelReason
                    );
                    // 更新会话统计信息
                    tableSessionService.updateSessionStatistics(tableSession.getSessionNo());
                }
            } catch (Exception e) {
                log.error("WebSocket 推送失败: orderId={}, error={}", orderId, e.getMessage(), e);
            }
        }
        return result;
    }

    /**
     * 创建支付订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentResponse createPayment(Long orderId, Integer paymentType, String openid) {

        // 1. 查询订单
        RestaurantOrder order = this.getById(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在");
        }

        // 2. 验证订单状态
        if (order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            throw new IllegalStateException("订单已支付，请勿重复支付");
        }
        if (order.getStatus() == OrderStatusEnum.CANCELLED) {
            throw new IllegalStateException("订单已取消，无法支付");
        }

        // 3. 构建响应
        PaymentResponse.PaymentResponseBuilder responseBuilder = PaymentResponse.builder()
                .orderId(orderId)
                .orderNo(order.getOrderNo())
                .payAmount(order.getTotalAmount())
                .paymentType(paymentType)
                .expireSeconds(1800);

        // 5. 根据支付方式调用对应的支付服务
        PaymentTypeEnum paymentTypeEnum = PaymentTypeEnum.getByCode(paymentType);

        if (paymentTypeEnum == PaymentTypeEnum.WECHAT) {
            // 调用微信支付
            createWeChatPayment(order, openid, responseBuilder);

        } else if (paymentTypeEnum == PaymentTypeEnum.ALIPAY) {
            // 调用支付宝支付
            createAlipayPayment(order, responseBuilder);

        } else {
            throw new IllegalStateException("不支持的支付方式");
        }

        return responseBuilder.build();
    }

    /**
     * 主动查询支付结果并更新订单状态
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean queryAndUpdatePaymentStatus(Long orderId) {

        RestaurantOrder order = this.getById(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在");
        }

        // 如果已支付，直接返回
        if (order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            return true;
        }

        // 调用支付平台查询
        if (order.getPaymentType() != null && order.getOrderNo() != null) {
            boolean isPaid = false;

            if (order.getPaymentType() == PaymentTypeEnum.WECHAT) {
                // 查询微信支付
                isPaid = queryWeChatPaymentStatus(order.getOrderNo());
            } else if (order.getPaymentType() == PaymentTypeEnum.ALIPAY) {
                // 查询支付宝
                isPaid = queryAlipayPaymentStatus(order.getOrderNo());
            }

            // 更新订单状态
            if (isPaid) {
                order.setPaymentStatus(PaymentStatusEnum.PAID);
                order.setPaymentTime(LocalDateTime.now());
                order.setStatus(OrderStatusEnum.PLACED);
                this.updateById(order);
                return true;
            }
        }

        return false;
    }

    /**
     * 查询支付状态（增强版）
     * 返回更详细的支付状态信息，用于前端轮询查询
     */
    @Override
    public PaymentStatusVO getPaymentStatus(Long orderId) {
        RestaurantOrder order = this.getById(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在");
        }

        // 计算等待时长（秒）
        Long waitingSeconds = null;
        Boolean isExpired = false;
        if (order.getCreatedAt() != null) {
            waitingSeconds = java.time.Duration.between(order.getCreatedAt(), LocalDateTime.now()).getSeconds();
            // 判断是否超过30分钟（1800秒）
            isExpired = waitingSeconds > 1800;
        }
        if(isExpired){
            handlePaymentTimeout(orderId, "订单已超时");
        }
        if(order.getStatus()==OrderStatusEnum.CANCELLED){
            cancelOrder(orderId, "订单已取消");
        }

        // 使用 Builder 构建 VO
        return PaymentStatusVO.builder()
                // 基本订单信息
                .orderId(orderId)
                .orderNo(order.getOrderNo())
                .totalAmount(order.getTotalAmount())
                .actualAmount(order.getActualAmount())

                // 订单状态
                .orderStatus(order.getStatus())
                .orderStatusCode(order.getStatus() != null ? order.getStatus().getCode() : null)
                .orderStatusDesc(order.getStatus() != null ? order.getStatus().getDesc() : null)

                // 支付状态
                .paymentStatus(order.getPaymentStatus())
                .paymentStatusCode(order.getPaymentStatus() != null ? order.getPaymentStatus().getCode() : null)
                .paymentStatusDesc(order.getPaymentStatus() != null ? order.getPaymentStatus().getDesc() : null)

                // 支付方式
                .paymentType(order.getPaymentType())
                .paymentTypeCode(order.getPaymentType() != null ? order.getPaymentType().getCode() : null)
                .paymentTypeDesc(order.getPaymentType() != null ? order.getPaymentType().getDesc() : null)

                // 支付交易号
                .paymentNo(order.getPaymentNo())

                // 时间信息
                .createdAt(order.getCreatedAt())
                .paymentTime(order.getPaymentTime())
                .cancelTime(order.getCancelTime())

                // 取消原因
                .cancelReason(order.getCancelReason())

                // 简化的状态标识（用于前端快速判断）
                .isPaid(order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.PAID)
                .isCancelled(order.getStatus() != null && order.getStatus() == OrderStatusEnum.CANCELLED)
                .isTimeout(order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.TIMEOUT)
                .isFailed(order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.FAILED)
                .isClosed(order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.CLOSED)

                // 操作权限标识
                .canPay(order.getPaymentStatus() == PaymentStatusEnum.UNPAID &&
                        order.getStatus() != OrderStatusEnum.CANCELLED &&
                        order.getStatus() != OrderStatusEnum.COMPLETED)
                .canCancel(order.getPaymentStatus() != PaymentStatusEnum.PAID &&
                        order.getStatus() != OrderStatusEnum.CANCELLED &&
                        order.getStatus() != OrderStatusEnum.COMPLETED)

                // 扩展信息
                .waitingSeconds(waitingSeconds)
                .isExpired(isExpired)
                .build();
    }


    /**
     * 根据用户ID分页查询订单列表（包含订单明细）
     * @param userId 用户ID
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @return 分页订单列表（包含订单明细）
     */
    @Override
    public Page<RestaurantOrderVO> getOrdersByUserId(Long userId,String sessionNo,Integer SceneType, Integer currentPage, Integer pageSize) {
        // 1. 分页查询订单列表
        Page<RestaurantOrder> page = new Page<>(currentPage, pageSize);
        LambdaQueryWrapper<RestaurantOrder> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(RestaurantOrder::getUserId, userId);

        // 【核心逻辑】OR查询：userId的订单 或 sessionNo的订单
        // 1. 会话进行中（有sessionNo）：可以看到会话中的共享订单
        // 2. 历史订单：可以看到自己作为开桌者的订单
        if (!StrUtil.isBlank(sessionNo)) {
            // 有sessionNo：查询 (userId的订单) OR (sessionNo的订单)
            wrapper.and(w -> w
                    .eq(RestaurantOrder::getUserId, userId)
                    .or()
                    .eq(RestaurantOrder::getSessionNo, sessionNo)
                    .ge(RestaurantOrder::getCreatedAt, LocalDateTime.now().minusMonths(1))
            );
        } else {
            // 无sessionNo：只查询userId的订单（历史订单），默认仅查询近一个月的订单
            wrapper.eq(RestaurantOrder::getUserId, userId);
            wrapper.ge(RestaurantOrder::getCreatedAt, LocalDateTime.now().minusMonths(1));
        }
        // 根据 sceneType 设置查询条件
        if (SceneType != null) {
            if (SceneType == 2) {
                // 当 sceneType 为 2 时，只查询 orderType 为 2 的数据
                wrapper.eq(RestaurantOrder::getOrderType, SceneTypeEnum.getByCode(2));
            } else if (SceneType == 0 || SceneType == 1) {
                // 当 sceneType 为 0 或 1 时，查询 orderType 为 0 和 1 的数据
                wrapper.in(RestaurantOrder::getOrderType,
                        SceneTypeEnum.getByCode(0),
                        SceneTypeEnum.getByCode(1));
            }
        }

        wrapper.orderByDesc(RestaurantOrder::getCreatedAt);
        Page<RestaurantOrder> orderPage = this.page(page, wrapper);

        // 2. 转换为VO并查询订单明细
        Page<RestaurantOrderVO> voPage = new Page<>(currentPage, pageSize);
        voPage.setTotal(orderPage.getTotal());
        voPage.setRecords(restaurantOrderConverter.toVOList(orderPage.getRecords()));
        return voPage;
    }

    /**
     * 余额支付
     * @param orderId 订单ID
     * @return 支付是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean payByBalance(Long orderId) {
        // 1. 查询订单
        RestaurantOrder order = this.getById(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在");
        }

        // 2. 验证订单状态
        if (order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            throw new IllegalStateException("订单已支付，请勿重复支付");
        }
        if (order.getStatus() == OrderStatusEnum.CANCELLED) {
            throw new IllegalStateException("订单已取消，无法支付");
        }

        // 3. 获取用户信息（Feign 调用 user-service）
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = FeignResultUtil.unwrap(userFeignClient.getUserById(userId));
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }

        // 4. 检查余额是否足够（user-service 扣款时会再次校验，这里提前给出友好提示）
        BigDecimal payAmount = order.getTotalAmount();
        if (user.getSurplusMoney().compareTo(payAmount) < 0) {
            throw new IllegalStateException("余额不足，请充值后再试");
        }

        // 5. 扣除余额 + 记录流水（user-service 本地事务内原子完成，替代原跨模块裸操作）
        FeignResultUtil.check(userFeignClient.changeBalance(BalanceChangeDTO.builder()
                .userId(userId)
                .amount(payAmount)
                .type(4) // 4-消费
                .orderNo(order.getOrderNo())
                .operateUserId(userId)
                .build()));

        // 6. 调用统一的支付成功处理逻辑
        // 生成一个余额支付的流水号（使用订单号作为交易号）
        String transactionId = "BALANCE_" + order.getOrderNo();
        boolean success;
        try {
            success = handlePaymentSuccess(order.getOrderNo(), transactionId, PaymentTypeEnum.BANLANCE_PAY);
        } catch (Exception e) {
            // 本地后续处理失败时补偿：退回已远程扣除的余额（3-退款）
            refundBalanceCompensate(userId, payAmount, order.getOrderNo());
            throw e;
        }

        if (!success) {
            refundBalanceCompensate(userId, payAmount, order.getOrderNo());
            throw new IllegalStateException("处理支付成功逻辑失败");
        }

        log.info("余额支付成功, 订单ID: {}, 用户ID: {}, 支付金额: {}", orderId, userId, payAmount);
        return true;
    }

    /**
     * 余额扣款补偿：远程扣款成功但本地后续处理失败时，按退款类型退回余额。
     * 补偿本身失败只记录日志（依赖 orderNo 幂等键，可人工或对账任务重试）。
     */
    private void refundBalanceCompensate(Long userId, BigDecimal amount, String orderNo) {
        try {
            FeignResultUtil.check(userFeignClient.changeBalance(BalanceChangeDTO.builder()
                    .userId(userId)
                    .amount(amount)
                    .type(3) // 3-退款
                    .orderNo(orderNo + "_COMPENSATE")
                    .operateUserId(userId)
                    .build()));
            log.info("余额扣款补偿成功: userId={}, amount={}, orderNo={}", userId, amount, orderNo);
        } catch (Exception ex) {
            log.error("余额扣款补偿失败，需人工核对: userId={}, amount={}, orderNo={}", userId, amount, orderNo, ex);
        }
    }

    /**
     * 获取订单详情（包含订单明细）
     *
     * @param orderId 订单ID
     * @return 订单详情
     */
    @Override
    public RestaurantOrderVO getOrderDetail(Long orderId) {
        RestaurantOrder order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException(ORDER_NOT_EXIST.getCode(),"订单不存在");
        }

        // 使用转换器转换为VO（自动填充门店、餐桌、订单明细）
        return restaurantOrderConverter.toVO(order);
    }

    /**
     * 处理支付超时
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handlePaymentTimeout(Long orderId, String reason) {
        try {
            RestaurantOrder order = this.getById(orderId);
            if (order == null) {
                throw new IllegalStateException("订单不存在");
            }

            // 如果已支付，不能设置为超时
            if (order.getPaymentStatus() == PaymentStatusEnum.PAID) {
                return false;
            }

            // 关闭支付平台的订单
            if (order.getOrderNo() != null && order.getPaymentType() != null) {
                closePaymentPlatformOrder(order.getOrderNo(), order.getPaymentType());
            }

            // 更新订单状态
            order.setStatus(OrderStatusEnum.CANCELLED);
            order.setPaymentStatus(PaymentStatusEnum.UNPAID);
            order.setCancelReason(reason != null ? reason : "支付超时");
            order.setCancelTime(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());

            return this.updateById(order);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 处理支付失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handlePaymentError(Long orderId, String reason) {
        try {
            RestaurantOrder order = this.getById(orderId);
            if (order == null) {
                throw new IllegalStateException("订单不存在");
            }

            // 如果已支付，不能设置为失败
            if (order.getPaymentStatus() == PaymentStatusEnum.PAID) {
                return false;
            }

            // 更新订单状态
            order.setPaymentStatus(PaymentStatusEnum.UNPAID);
            order.setCancelReason(reason != null ? reason : "支付失败");
            order.setUpdatedAt(LocalDateTime.now());

            return this.updateById(order);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 关闭支付平台的订单
     */
    private void closePaymentPlatformOrder(String outTradeNo, PaymentTypeEnum paymentType) {
        try {
            if (paymentType == PaymentTypeEnum.WECHAT) {
                // 关闭微信支付订单（Feign 调用 pay-service）
                payFeignClient.closeWeChatTrade(outTradeNo);
            } else if (paymentType == PaymentTypeEnum.ALIPAY) {
                // 关闭支付宝订单（Feign 调用 pay-service）
                payFeignClient.closeAlipayTrade(outTradeNo);
            }
        } catch (Exception e) {
            // 不抛出异常，允许订单关闭继续
        }
    }

    /**
     * 处理支付成功回调
     * 1. 根据商户订单号更新订单支付状态
     * 2. 自动打印订单（如果启用了飞鹅打印机）
     *
     * @param outTradeNo 商户订单号
     * @param transactionId 支付平台交易号（微信支付单号或支付宝交易号）
     * @param paymentType 支付方式
     * @return 是否处理成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handlePaymentSuccess(String outTradeNo, String transactionId, PaymentTypeEnum paymentType) {
        try {
            // 1. 根据订单号查询订单
            LambdaQueryWrapper<RestaurantOrder> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RestaurantOrder::getOrderNo, outTradeNo);
            RestaurantOrder order = this.getOne(wrapper);

            if (order == null) {
                return false;
            }

            // 支付回调是匿名入口：先在受控旁路中按全局唯一订单号定位订单，
            // 定位成功后立即切回该订单的租户上下文，后续库存、会话、打印、Feign
            // 调用全部恢复正常租户过滤与租户头透传。
            boolean callbackTenantScope = !TenantContext.hasTenant();
            if (callbackTenantScope) {
                TenantIgnore.close();
                TenantContext.setTenantId(order.getTenantId());
            }
            try {

            // 2. 检查订单是否已支付（幂等性处理）
            if (order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.PAID) {
                return true; // 返回成功，避免支付平台重复回调
            }

            // 3. 更新订单支付状态
            order.setPaymentStatus(PaymentStatusEnum.PAID);
            order.setPaymentType(paymentType);
            order.setPaymentNo(transactionId);
            order.setPaymentTime(LocalDateTime.now());
            order.setStatus(OrderStatusEnum.PLACED); // 订单状态更新为已下单
            // 实际支付金额 = 总金额 - 优惠券抵扣
            BigDecimal discount = order.getDiscountAmount() != null ? order.getDiscountAmount() : BigDecimal.ZERO;
            order.setActualAmount(order.getTotalAmount().subtract(discount));
            order.setUpdatedAt(LocalDateTime.now());

            // 优惠券已在创建订单时核销，此处无需再核销

            // 4. 线上支付成功后，如果是叫号自取模式且没有取餐码，生成取餐码
            StoreInfo storeInfo = storeInfoMapper.selectById(order.getStoreId());
            if (storeInfo != null &&
                    storeInfo.getPickupMode() == pickupModeEnum.NUMBERCALLING &&
                    StrUtil.isBlank(order.getPickupCode())) {
                order.setPickupCode(generatePickupCode());
            }

            boolean updateSuccess = this.updateById(order);
            if (!updateSuccess) {
                return false;
            }

            // 4.5 减少菜品库存并增加销量
            try {
                decreaseOrderDishStock(order.getId());
            } catch (Exception e) {
                log.error("订单【{}】减少库存失败: {}", order.getOrderNo(), e.getMessage(), e);
                // 库存减少失败时，回滚事务
                throw new BusinessException("库存不足或减少失败：" + e.getMessage());
            }

            // 5. WebSocket 推送：通知用户订单支付成功
            if (order.getTableId() != null && order.getSessionNo() != null) {
                try {
                    // 获取当前桌位的会话
                    var tableSession = tableSessionService.getActiveSessionByTableId(order.getTableId());
                    if (tableSession != null) {
                        // 转换为VO并推送订单支付成功通知（包含完整订单信息，含取餐码）
                        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(order);
                        webSocketPushService.pushOrderPaid(tableSession.getSessionNo(), orderVO);
                        // 更新会话统计信息
                        tableSessionService.updateSessionStatistics(tableSession.getSessionNo());
                    }
                } catch (Exception e) {
                    // WebSocket 推送失败不影响支付处理
                    log.error("WebSocket 推送支付成功通知失败: orderId={}, error={}", order.getId(), e.getMessage(), e);
                }
            }

            // 6. 打印订单（如果门店开启了小票打印）
            if (storeInfo != null && storeInfo.getIsPrint() == BooleanEnum.YES) {
                printOrderWithStorePrinters(order.getId(), storeInfo.getId());
            }

            // 6.5 线上支付+会话模式：支付成功后结束会话，一单一结，不允许再加菜；继续点单需重新扫码
            if (StrUtil.isNotBlank(order.getSessionNo()) && order.getPaymentMode() == PaymentModeEnum.ONLINE) {
                try {
                    tableSessionService.endSession(order.getSessionNo(), order.getActualAmount() != null ? order.getActualAmount() : BigDecimal.ZERO);
                    log.info("线上支付成功，已结束会话: sessionNo={}, orderId={}", order.getSessionNo(), order.getId());
                } catch (Exception e) {
                    log.warn("结束会话失败，不影响支付结果: sessionNo={}, error={}", order.getSessionNo(), e.getMessage());
                }
            }

            // 7. 【新增】支付成功后从超时队列中移除订单
            try {
                orderTimeoutQueueService.removeFromTimeoutQueue(order.getId());
                log.info("订单支付成功，已从超时队列移除: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
            } catch (Exception e) {
                // 移除失败不影响支付处理，仅记录日志
                log.error("从超时队列移除订单失败: orderId={}, error={}", order.getId(), e.getMessage());
            }

                return true;
            } finally {
                if (callbackTenantScope) {
                    TenantContext.clear();
                    // 恢复由 HTTP 入口打开的受控旁路，交给 afterCompletion 成对关闭。
                    TenantIgnore.open();
                }
            }

        } catch (Exception e) {
            throw new BusinessException(e.getMessage());
        }
    }

    /**
     * 查询微信支付状态
     */
    private boolean queryWeChatPaymentStatus(String outTradeNo) {
        try {
            Map<String, Object> result = FeignResultUtil.unwrap(payFeignClient.queryWeChatTrade(outTradeNo));
            if (result.get("success") != null && (Boolean) result.get("success")) {
                String tradeState = String.valueOf(result.get("tradeState"));
                return "SUCCESS".equals(tradeState);
            }
        } catch (Exception e) {
            log.error("查询微信支付状态失败", e);
        }
        return false;
    }

    /**
     * 生成订单编号
     * 格式：RO + 年月日时分秒 + 4位随机数
     * 例如：RO202510271430001234
     */
    private String generateOrderNo() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timeStr = LocalDateTime.now().format(formatter);
        int random = (int) ((Math.random() * 9000) + 1000); // 生成1000-9999的随机数
        return "RO" + timeStr + random;
    }

    /**
     * 查询支付宝支付状态
     */
    private boolean queryAlipayPaymentStatus(String outTradeNo) {
        try {
            Map<String, Object> result = FeignResultUtil.unwrap(payFeignClient.queryAlipayTrade(outTradeNo));
            if (result.get("success") != null && (Boolean) result.get("success")) {
                String tradeStatus = (String) result.get("tradeStatus");
                return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
            }
        } catch (Exception e) {
            log.error("查询支付宝支付状态失败", e);
        }
        return false;
    }

    /**
     * 创建微信支付
     */
    private void createWeChatPayment(RestaurantOrder order, String openid, PaymentResponse.PaymentResponseBuilder responseBuilder) {
        if (openid == null || openid.isEmpty()) {
            throw new IllegalArgumentException("微信支付需要提供openid");
        }

        // 构建微信支付参数
        // 注意：notifyUrl 应该配置为餐厅模块专用的回调地址
        // 示例：https://your-domain.com/restaurant-order/pay/wechat/notify
        // 在生产环境需要在配置文件中配置实际的域名
        WeChatPayTradeParam param = WeChatPayTradeParam.builder()
                .outTradeNo(order.getOrderNo())
                .description("餐饮订单")
                .totalAmount(order.getTotalAmount())
                .openid(openid)
                .timeExpire(30)
                .notifyUrl("https://your-domain.com/restaurant-order/pay/wechat/notify")
                .build();

        // 调用微信支付服务（Feign 调用 pay-service）
        Map<String, Object> payParams = FeignResultUtil.unwrap(payFeignClient.createWeChatJsapiPay(param));

        // 设置小程序支付参数
        responseBuilder.miniPayParams(
                PaymentResponse.MiniPayParams.builder()
                        .timeStamp((String) payParams.get("timeStamp"))
                        .nonceStr((String) payParams.get("nonceStr"))
                        .packageValue((String) payParams.get("package"))
                        .signType((String) payParams.get("signType"))
                        .paySign((String) payParams.get("paySign"))
                        .build()
        );
    }

    /**
     * 创建支付宝支付
     */
    private void createAlipayPayment(RestaurantOrder order, PaymentResponse.PaymentResponseBuilder responseBuilder) {
        // 构建支付宝参数
        // 注意：notifyUrl 应该配置为餐厅模块专用的回调地址
        // 示例：https://your-domain.com/restaurant-order/pay/alipay/notify
        // 计算超时时间：当前时间 + 30分钟
        LocalDateTime expireTime = LocalDateTime.now().plusMinutes(30);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timeExpireStr = expireTime.format(formatter);

        AlipayTradeWapPayRequestParams param = AlipayTradeWapPayRequestParams.builder()
                .outTradeNo(order.getOrderNo())
                .subject("餐厅订单-"+order.getOrderNo())
                .totalAmount(order.getTotalAmount())
                .goodsDetail(JSONUtil.toJsonPrettyStr(order))
                .timeExpire(timeExpireStr)
                // TODO: 这里可以从配置文件读取餐厅模块专用的回调URL
                // .notifyUrl("https://your-domain.com/restaurant-order/pay/alipay/notify")
                .build();

        // 调用支付宝服务（Feign 调用 pay-service）
        String payFormHtml = FeignResultUtil.unwrap(payFeignClient.createAlipayWapPay(param));
        responseBuilder.payUrl(payFormHtml);

    }


    /**
     * 处理微信支付回调通知
     * 餐厅模块专用的回调处理逻辑
     */
    @Override
    public String handleWeChatPayNotify(String requestBody, String signature, String timestamp, String nonce, String serial) {
        try {
            PaymentNotifyResult notifyResult = FeignResultUtil.unwrap(payFeignClient.parseWeChatNotify(
                    WeChatNotifyParseDTO.builder()
                            .requestBody(requestBody)
                            .signature(signature)
                            .timestamp(timestamp)
                            .nonce(nonce)
                            .serial(serial)
                            .build()));
            if (notifyResult.getIsSuccess()) {
                // 支付成功，更新订单状态
                boolean success = handlePaymentSuccess(
                        notifyResult.getOutTradeNo(),
                        notifyResult.getTransactionId(),
                        PaymentTypeEnum.WECHAT
                );
                if (success) {
                    return "{\"code\": \"SUCCESS\", \"message\": \"成功\"}";
                } else {
                    return "{\"code\": \"SUCCESS\", \"message\": \"成功\"}";
                }
            } else {
                return "{\"code\": \"SUCCESS\", \"message\": \"成功\"}";
            }

        } catch (Exception e) {
            log.error("餐厅订单模块：处理微信支付回调失败", e);
            return "{\"code\": \"FAIL\", \"message\": \"" + e.getMessage() + "\"}";
        }
    }

    /**
     * 处理支付宝支付回调通知
     * 餐厅模块专用的回调处理逻辑
     */
    @Override
    public String handleAlipayNotify(Map<String, String> params) {
        try {
            if (!Boolean.TRUE.equals(FeignResultUtil.unwrap(payFeignClient.verifyAlipayNotify(params)))) {
                log.warn("餐厅订单模块：支付宝回调验签失败");
                return "failure";
            }
            // 1. 获取回调参数
            String outTradeNo = params.get("out_trade_no");  // 商户订单号
            String tradeNo = params.get("trade_no");         // 支付宝交易号
            String tradeStatus = params.get("trade_status"); // 交易状态

            // 2. 验签通过后处理支付成功
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                boolean success = handlePaymentSuccess(outTradeNo, tradeNo, PaymentTypeEnum.ALIPAY);

                if (success) {
                    return "success";
                } else {
                    // 即使业务处理失败，也返回成功，避免支付宝重复回调
                    return "success";
                }
            }
            return "success";

        } catch (Exception e) {
            log.error("餐厅订单模块：处理支付宝回调失败", e);
            return "failure";
        }
    }

    /**
     * 根据会话号查询订单
     * 用于检查会话是否已有订单
     *
     * @param sessionNo 会话编号
     * @return 订单信息，如果不存在则返回null
     */
    @Override
    public RestaurantOrder getOrderBySessionNo(String sessionNo) {
        if (sessionNo == null || sessionNo.isEmpty()) {
            return null;
        }

        LambdaQueryWrapper<RestaurantOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RestaurantOrder::getSessionNo, sessionNo)
                .orderByDesc(RestaurantOrder::getCreatedAt)
                .last("LIMIT 1");

        return this.getOne(wrapper);
    }

    /**
     * 追加菜品到现有订单（加菜功能）
     * 会话期间，用户可以多次追加菜品到同一个订单中
     * 每次追加会生成新的批次号，便于厨房分批制作
     *
     * @param request 追加菜品请求
     * @return 更新后的订单信息（包含所有订单明细）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestaurantOrderVO addItemsToOrder(AddItemsRequest request) {
        // 1. 查询订单是否存在
        RestaurantOrder order = this.getById(request.getOrderId());
        if (order == null) {
            throw new BusinessException(ORDER_NOT_EXIST.getCode(), "订单不存在");
        }

        // 2. 验证订单状态（只有未完成、未取消的订单才能追加菜品）
        if (order.getStatus() == OrderStatusEnum.COMPLETED) {
            throw new BusinessException("订单已完成，无法追加菜品");
        }
        if (order.getStatus() == OrderStatusEnum.CANCELLED) {
            throw new BusinessException("订单已取消，无法追加菜品");
        }

        // 2.5 加菜仅支持线下支付+会话模式；线上支付一单一结，不允许加菜
        if (order.getPaymentMode() != null && order.getPaymentMode() == PaymentModeEnum.ONLINE) {
            throw new BusinessException("加菜仅支持线下支付会话模式，线上支付一单一结，如需继续点单请重新扫码进入新会话");
        }
        if (StrUtil.isBlank(order.getSessionNo())) {
            throw new BusinessException("加菜仅支持桌位会话模式，请使用扫码桌位点餐");
        }

        // 3. 验证会话号（必填）
        if (StrUtil.isBlank(request.getSessionNo()) || !request.getSessionNo().equals(order.getSessionNo())) {
            throw new BusinessException("会话号不匹配或未传，无法追加菜品");
        }

        // 4. 【新增】验证购物车商品数据（包括价格、规格等）
        validateCreateOrderCartItems(request.getCartItems());

        // 5. 计算新批次号
        Integer newBatchNo = restaurantOrderItemService.getMaxBatchNo(request.getOrderId()) + 1;
        log.info("追加菜品到订单: orderId={}, 新批次号={}", request.getOrderId(), newBatchNo);

        // 6. 创建新的订单项
        List<RestaurantOrderItem> newItems = new ArrayList<>();
        BigDecimal addAmount = BigDecimal.ZERO; // 追加的总金额（不含赠送菜品）

        if (request.getCartItems() != null && !request.getCartItems().isEmpty()) {
            LocalDateTime addTime = LocalDateTime.now();

            for (CreateOrderRequest.CartItem cartItem : request.getCartItems()) {
                RestaurantOrderItem orderItem = new RestaurantOrderItem();
                orderItem.setOrderId(request.getOrderId());
                orderItem.setDishId(cartItem.getDishId());
                orderItem.setCategoryId(cartItem.getCategoryId());

                BigDecimal itemPrice = cartItem.getPrice() != null ?
                        cartItem.getPrice() : cartItem.getBasePrice();
                orderItem.setPrice(itemPrice);
                orderItem.setQuantity(cartItem.getQuantity());

                // 设置赠送标识
                orderItem.setIsGift(cartItem.getIsGift() != null ? cartItem.getIsGift() : 0);

                // 计算原价小计
                BigDecimal itemOriginalTotal = itemPrice.multiply(new BigDecimal(cartItem.getQuantity()));

                // 赠菜：优惠金额=该行原价，小计 totalPrice=原价-优惠=0（该行实付）；非赠菜：小计=原价
                if (orderItem.getIsGift() == 1) {
                    orderItem.setDiscountPrice(itemOriginalTotal);
                    orderItem.setTotalPrice(BigDecimal.ZERO);
                } else {
                    orderItem.setTotalPrice(itemOriginalTotal);
                    orderItem.setDiscountPrice(BigDecimal.ZERO);
                    addAmount = addAmount.add(itemOriginalTotal);
                }

                // 保存规格信息
                orderItem.setSpecOptionsText(cartItem.getSpecText());
                orderItem.setCreatedAt(LocalDateTime.now());
                orderItem.setUpdatedAt(LocalDateTime.now());

                // 【核心】设置批次号和加菜时间
                orderItem.setBatchNo(newBatchNo);
                orderItem.setAddTime(addTime);

                newItems.add(orderItem);
            }

            // 批量保存新订单项
            restaurantOrderItemService.saveBatch(newItems);
        }

        // 6.5 【加菜优惠券】仅支持菜品免单券，处理优惠并分摊到本批订单项的优惠金额
        List<Long> addItemUserCouponIds = request.getUserCouponIds();
        List<Coupon> addItemUsedCoupons = new ArrayList<>();
        if (addItemUserCouponIds != null && !addItemUserCouponIds.isEmpty() && !newItems.isEmpty()) {
            Long userId = order.getUserId();
            if (userId == null) {
                throw new BusinessException("加菜使用优惠券需登录");
            }
            // 校验：加菜只能用菜品免单券（Feign 批量查询 coupon-service）
            List<UserCoupon> addItemUserCoupons = FeignResultUtil.unwrap(couponFeignClient.getUserCouponsByIds(addItemUserCouponIds));
            List<Long> addItemCouponIds = addItemUserCoupons.stream()
                    .map(UserCoupon::getCouponId)
                    .collect(Collectors.toList());
            List<Coupon> addItemCoupons = FeignResultUtil.unwrap(couponFeignClient.getCouponsByIds(addItemCouponIds));
            for (Coupon c : addItemCoupons) {
                if (c.getType() != CouponTypeEnum.DISH_FREE) {
                    throw new BusinessException("加菜仅支持使用菜品免单券，无法使用其它类型优惠券");
                }
                addItemUsedCoupons.add(c);
            }
            // 按本批加菜金额计算优惠（仅菜品免单券会产生折扣）
            Result calcResult = calcCouponDiscount(userId, addItemUserCouponIds, addAmount);
            CouponDiscountResultVO calcData = (CouponDiscountResultVO) (calcResult != null ? calcResult.getData() : null);
            if (calcData == null || !Boolean.TRUE.equals(calcData.getUsable())) {
                throw new BusinessException("优惠券不可用：" + (calcData != null ? calcData.getReason() : "未知"));
            }
            // 按菜品ID汇总可免单金额（多张券同一菜品可累加）
            Map<Long, BigDecimal> freeAmountByDishId = new HashMap<>();
            if (calcData.getCouponDetails() != null) {
                for (CouponDiscountResultVO.CouponDetailVO detail : calcData.getCouponDetails()) {
                    if (detail.getCouponType() == CouponTypeEnum.DISH_FREE && detail.getDishItems() != null) {
                        for (CouponDiscountResultVO.DishItemVO di : detail.getDishItems()) {
                            if (di.getDishId() != null && di.getPrice() != null) {
                                freeAmountByDishId.merge(di.getDishId(), di.getPrice(), BigDecimal::add);
                            }
                        }
                    }
                }
            }
            // 将优惠分摊到本批订单项：设置每项的 discountPrice 与 totalPrice
            boolean appliedAnyDiscount = false;
            for (RestaurantOrderItem item : newItems) {
                if (item.getIsGift() != null && item.getIsGift() == 1) {
                    continue;
                }
                Long dishId = item.getDishId();
                BigDecimal remaining = freeAmountByDishId.get(dishId);
                if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal lineTotal = item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO;
                BigDecimal deduct = lineTotal.min(remaining);
                item.setDiscountPrice(deduct);
                item.setTotalPrice(lineTotal.subtract(deduct));
                freeAmountByDishId.put(dishId, remaining.subtract(deduct));
                if (deduct.compareTo(BigDecimal.ZERO) > 0) {
                    appliedAnyDiscount = true;
                }
            }
            restaurantOrderItemService.updateBatchById(newItems);
            // 仅当实际对本批订单项做了优惠分摊时才保留用于合并与核销，否则视为未使用
            if (!appliedAnyDiscount) {
                addItemUsedCoupons.clear();
            }
            log.info("加菜优惠券处理完成: orderId={}, 本批优惠券数={}, 本批加菜原价={}, 已分摊={}", request.getOrderId(), addItemUserCouponIds.size(), addAmount, appliedAnyDiscount);
        }

        // 7. 更新订单总金额
        // 加菜只扣减本批新增订单项，不能复用整单扣库存逻辑，否则会重复扣除历史批次。
        if (!newItems.isEmpty()) {
            Map<Long, Integer> addedDishStock = new HashMap<>();
            for (RestaurantOrderItem item : newItems) {
                addedDishStock.merge(item.getDishId(), item.getQuantity(), Integer::sum);
            }
            dishService.batchDecreaseStock(addedDishStock);
            log.info("加菜批次已扣减库存: orderId={}, batchNo={}, items={}",
                    request.getOrderId(), newBatchNo, addedDishStock);
        }

        // 7. 更新订单总金额
        // 按订单项重算订单总金额，保证订单总价与菜品项总价一致
        order = recalculateOrderAmountsFromItems(request.getOrderId());

        // 7.5 若本批使用了优惠券（已参与分摊），合并到订单的 userCouponIds/couponTypes 并核销
        if (addItemUserCouponIds != null && !addItemUserCouponIds.isEmpty() && !addItemUsedCoupons.isEmpty()) {
            order = this.getById(request.getOrderId());
            String existingIds = order.getUserCouponIds();
            String newIdsStr = addItemUserCouponIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            order.setUserCouponIds(StrUtil.isBlank(existingIds) ? newIdsStr : existingIds + "," + newIdsStr);
            List<CouponTypeEnum> existingTypes = order.getCouponTypes();
            List<CouponTypeEnum> mergedTypes = existingTypes != null ? new ArrayList<>(existingTypes) : new ArrayList<>();
            for (Coupon c : addItemUsedCoupons) {
                if (c.getType() != null && !mergedTypes.contains(c.getType())) {
                    mergedTypes.add(c.getType());
                }
            }
            order.setCouponTypes(mergedTypes);
            order.setUpdatedAt(LocalDateTime.now());
            this.updateById(order);
            for (Long ucId : addItemUserCouponIds) {
                FeignResultUtil.check(couponFeignClient.verifyCouponOnOrder(ucId, order.getId()));
                log.info("加菜优惠券已核销: orderId={}, userCouponId={}", order.getId(), ucId);
            }
        }

        log.info("成功追加菜品: orderId={}, 批次号={}, 追加金额={}, 新总金额={}",
                request.getOrderId(), newBatchNo, addAmount, order.getTotalAmount());

        // 8. WebSocket 推送：通知同桌其他用户有新菜品追加
        if (order.getTableId() != null && order.getSessionNo() != null) {
            try {
                TableSession tableSession = tableSessionService.getActiveSessionByTableId(order.getTableId());
                if (tableSession != null) {
                    // 转换为VO并推送
                    RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(order);
                    webSocketPushService.pushOrderCreated(tableSession.getSessionNo(), orderVO);

                    // 更新会话统计信息
                    tableSessionService.updateSessionStatistics(tableSession.getSessionNo());
                }
            } catch (Exception e) {
                log.error("WebSocket 推送追加菜品失败: orderId={}, error={}", request.getOrderId(), e.getMessage(), e);
            }
        }

        // 9. 仅打印本批加菜项（加菜单），不重打整单
        StoreInfo storeInfo = storeInfoMapper.selectById(order.getStoreId());
        if (storeInfo != null && storeInfo.getIsPrint() == BooleanEnum.YES && !newItems.isEmpty()) {
            List<Long> newItemIds = newItems.stream()
                    .map(RestaurantOrderItem::getId)
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
            if (!newItemIds.isEmpty()) {
                printAddItemsWithStorePrinters(order.getId(), newItemIds, storeInfo.getId());
            }
        }

        // 10. 返回更新后的订单信息
        return restaurantOrderConverter.toVO(order);

    }

    // ================== 收银台专用方法实现 ==================

    /**
     * 收银台-扫码支付下单（预留方法）
     *
     * 业务流程：
     * 1. 创建订单（状态为待支付）
     * 2. 调用支付平台的扫码付接口
     * 3. 返回支付二维码或等待扫码结果
     * 4. 前端轮询或通过WebSocket等待支付回调
     *
     * @param request 收银台下单请求
     * @return 订单信息（包含订单明细和支付参数）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestaurantOrderVO cashierScanPay(CashierOrderRequest request) {
        throw new BusinessException("扫码支付功能暂未开放");

        /* 完整实现示例（需要对接扫码支付后才能使用）：

        // 1. 创建订单
        String orderNo = generateOrderNo();
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderNo(orderNo);
        order.setStoreId(request.getStoreId());
        order.setTableId(request.getTableId());
        order.setUserId(request.getUserId());
        order.setOrderType(request.getOrderType() != null ? request.getOrderType() : SceneTypeEnum.ONLINE);
        order.setTotalAmount(request.getTotalPrice());
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setActualAmount(request.getTotalPrice());
        order.setDiningCount(request.getPeopleCount());
        order.setPaymentMode(PaymentModeEnum.ONLINE);
        order.setNote(request.getNote());
        order.setServiceFee(BigDecimal.ZERO);
        order.setPaymentStatus(PaymentStatusEnum.UNPAID);
        order.setStatus(OrderStatusEnum.NOPAID);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 保存订单
        this.save(order);

        // 2. 创建订单明细
        List<RestaurantOrderItem> orderItems = new ArrayList<>();
        if (request.getCartItems() != null && !request.getCartItems().isEmpty()) {
            for (CashierOrderRequest.CartItem cartItem : request.getCartItems()) {
                RestaurantOrderItem orderItem = new RestaurantOrderItem();
                orderItem.setOrderId(order.getId());
                orderItem.setDishId(cartItem.getDishId());
                orderItem.setCategoryId(cartItem.getCategoryId());
                orderItem.setPrice(cartItem.getPrice());
                orderItem.setQuantity(cartItem.getQuantity());
                orderItem.setDiscountPrice(BigDecimal.ZERO);

                BigDecimal itemTotal = cartItem.getPrice() != null
                    ? cartItem.getPrice().multiply(new BigDecimal(cartItem.getQuantity()))
                    : cartItem.getBasePrice().multiply(new BigDecimal(cartItem.getQuantity()));
                orderItem.setTotalPrice(itemTotal);
                orderItem.setSpecOptionsText(cartItem.getSpecText());
                orderItem.setCreatedAt(LocalDateTime.now());
                orderItem.setUpdatedAt(LocalDateTime.now());
                orderItem.setBatchNo(1);
                orderItem.setAddTime(LocalDateTime.now());

                orderItems.add(orderItem);
            }
            restaurantOrderItemService.saveBatch(orderItems);
        }

        // 3. 调用扫码支付接口（根据支付方式选择微信或支付宝）
        // TODO: 调用扫码支付服务
        // String qrCodeUrl = scanPayService.createQrCode(orderNo, request.getTotalPrice(), request.getPaymentType());

        // 4. 返回订单信息（包含二维码URL）
        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(order);
        // orderVO.setQrCodeUrl(qrCodeUrl);

        return orderVO;
        */
    }

    /**
     * 收银台-直接下单（管理员操作）
     *
     * 业务流程：
     * 1. 创建订单
     * 2. 根据前端传入的折扣率和赠送标识，后端重新计算并校验金额
     * 3. 直接设置订单状态为已支付（现金或线下支付）
     * 4. 记录支付方式
     * 5. 自动打印小票（如果启用）
     *
     * @param request 收银台下单请求
     * @return 订单信息（包含订单明细）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestaurantOrderVO cashierDirectOrder(CashierOrderRequest request) {
        storeDataScopeService.checkStoreAccess(request.getStoreId());
        if (request.getPaymentType() == PaymentTypeEnum.BANLANCE_PAY && request.getUserId() == null) {
            throw new BusinessException("余额支付请先选择会员");
        }
        // 1. 验证前端传来的菜品数据（包括价格、规格等）
        validateCartItems(request.getCartItems());

        // 1.5 若传了 userId 则为余额支付，先校验用户存在（Feign 调用 user-service）
        SysUser user = null;
        if (request.getPaymentType()==PaymentTypeEnum.BANLANCE_PAY && request.getUserId()!=null) {
            user = FeignResultUtil.unwrap(userFeignClient.getUserById(request.getUserId()));
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
        }

        // 2. 后端重新计算订单金额（防止前端篡改数据）
        // 总金额 = 所有订单项金额（含赠送菜品原价）；优惠金额 = 订单项优惠（赠送等）+ 整单折扣
        BigDecimal calculatedTotalAmount = BigDecimal.ZERO;   // 总金额：所有项原价之和
        BigDecimal itemDiscountSum = BigDecimal.ZERO;        // 订单项优惠合计（如赠送行原价）

        for (CashierOrderRequest.CartItem cartItem : request.getCartItems()) {
            BigDecimal itemPrice = cartItem.getPrice() != null ?
                    cartItem.getPrice() : cartItem.getBasePrice();
            BigDecimal itemTotal = itemPrice.multiply(new BigDecimal(cartItem.getQuantity()));

            calculatedTotalAmount = calculatedTotalAmount.add(itemTotal);
            if (cartItem.getIsGift() != null && cartItem.getIsGift() == 1) {
                org.hongtu.security.OperationPermission.order("gift");
                itemDiscountSum = itemDiscountSum.add(itemTotal); // 赠送：该项优惠=原价
            }
        }

        // 折前应付 = 总金额 - 订单项优惠
        BigDecimal payableBeforeRate = calculatedTotalAmount.subtract(itemDiscountSum);
        if (request.getDiscountRate() != null && request.getDiscountRate().compareTo(BigDecimal.ONE) != 0)
            org.hongtu.security.OperationPermission.order("discount");
        BigDecimal discountRate = request.getDiscountRate() != null ?
                request.getDiscountRate() : BigDecimal.ONE;
        BigDecimal rateDiscountAmount = BigDecimal.ZERO;

        if (discountRate.compareTo(BigDecimal.ONE) < 0) {
            rateDiscountAmount = payableBeforeRate.multiply(BigDecimal.ONE.subtract(discountRate)).setScale(2, RoundingMode.HALF_UP);
        }
        // 实付 = 折前应付 * 折扣率；订单总优惠 = 订单项优惠 + 折扣优惠
        BigDecimal calculatedActualAmount = payableBeforeRate.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalDiscountAmount = itemDiscountSum.add(rateDiscountAmount);

        // 3. 验证前端传入的订单总金额是否正确（允许小数点后2位的误差）
        // 前端传入的 totalPrice 为折后实付，需与 calculatedActualAmount 一致
        if (request.getTotalPrice() != null &&
                request.getTotalPrice().subtract(calculatedActualAmount).abs().compareTo(new BigDecimal("0.01")) > 0) {
            throw new BusinessException(String.format(
                    "订单金额验证失败，前端传入（实付）：%s，后端计算（实付）：%s，总金额：%s，优惠：%s",
                    request.getTotalPrice(),
                    calculatedActualAmount,
                    calculatedTotalAmount,
                    totalDiscountAmount
            ));
        }

        log.info("订单金额验证通过，总金额：{}，订单项优惠：{}，折扣优惠：{}，总优惠：{}，实付：{}",
                calculatedTotalAmount, itemDiscountSum, rateDiscountAmount, totalDiscountAmount, calculatedActualAmount);

        // 3.5 余额支付时校验余额充足
        if (user != null) {
            if (user.getSurplusMoney() == null || user.getSurplusMoney().compareTo(calculatedActualAmount) < 0) {
                throw new BusinessException("用户余额不足，当前余额：" +
                        (user.getSurplusMoney() != null ? user.getSurplusMoney() : "0.00") +
                        " 元，需支付：" + calculatedActualAmount + " 元");
            }
        }

        // 4. 创建订单
        String orderNo = generateOrderNo();
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderNo(orderNo);
        order.setStoreId(request.getStoreId());
        order.setTableId(request.getTableId());
        order.setUserId(request.getUserId());
        order.setOrderType(request.getOrderType() != null ? request.getOrderType() : SceneTypeEnum.ADMIN_ORDER);
        order.setTotalAmount(calculatedTotalAmount);
        order.setDiscountAmount(totalDiscountAmount);
        order.setDiscountRate(discountRate);
        order.setActualAmount(calculatedActualAmount);
        order.setDiningCount(request.getPeopleCount());
        order.setPaymentMode(user != null ? PaymentModeEnum.ONLINE : PaymentModeEnum.OFFLINE);
        order.setNote(request.getNote());
        order.setServiceFee(BigDecimal.ZERO);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 直接设置为已支付状态（余额支付 or 现金/线下）
        order.setPaymentStatus(PaymentStatusEnum.PAID);
        order.setPaymentType(user != null ? PaymentTypeEnum.BANLANCE_PAY :
                (request.getPaymentType() != null ? request.getPaymentType() : PaymentTypeEnum.CASH));
        order.setPaymentTime(LocalDateTime.now());
        order.setStatus(OrderStatusEnum.PLACED); // 已下单状态

        // 如果是叫号自取模式，生成取餐码
        if (request.getStoreId() != null) {
            StoreInfo storeInfo = storeInfoMapper.selectById(request.getStoreId());
            if (storeInfo != null && storeInfo.getPickupMode() == pickupModeEnum.NUMBERCALLING) {
                order.setPickupCode(generatePickupCode());
            }
        }

        // 保存订单
        this.save(order);
        log.info("收银台直接下单成功: orderId={}, orderNo={}, totalAmount={}, discountRate={}, actualAmount={}, paymentType={}",
                order.getId(), orderNo, calculatedTotalAmount, discountRate, calculatedActualAmount, order.getPaymentType().getDesc());

        // 5. 创建订单明细
        List<RestaurantOrderItem> orderItems = new ArrayList<>();
        if (request.getCartItems() != null && !request.getCartItems().isEmpty()) {
            for (CashierOrderRequest.CartItem cartItem : request.getCartItems()) {
                RestaurantOrderItem orderItem = new RestaurantOrderItem();
                orderItem.setOrderId(order.getId());
                orderItem.setDishId(cartItem.getDishId());
                orderItem.setCategoryId(cartItem.getCategoryId());
                orderItem.setPrice(cartItem.getPrice());
                orderItem.setQuantity(cartItem.getQuantity());

                // 设置赠送标识
                orderItem.setIsGift(cartItem.getIsGift() != null ? cartItem.getIsGift() : 0);

                // 原价小计 = 单价*数量；优惠金额 = 赠菜时为该行原价，否则为 0
                BigDecimal itemTotal = cartItem.getPrice() != null
                        ? cartItem.getPrice().multiply(new BigDecimal(cartItem.getQuantity()))
                        : cartItem.getBasePrice().multiply(new BigDecimal(cartItem.getQuantity()));

                if (orderItem.getIsGift() == 1) {
                    // 赠菜：单价保留，优惠金额=该行原价；小计 totalPrice = 原价小计 - 优惠 = 0（该行实付）
                    orderItem.setDiscountPrice(itemTotal);
                    orderItem.setTotalPrice(BigDecimal.ZERO);
                } else {
                    orderItem.setTotalPrice(itemTotal);
                    orderItem.setDiscountPrice(BigDecimal.ZERO);
                }

                orderItem.setSpecOptionsText(cartItem.getSpecText());
                orderItem.setCreatedAt(LocalDateTime.now());
                orderItem.setUpdatedAt(LocalDateTime.now());
                orderItem.setBatchNo(1); // 首次下单，批次号为1
                orderItem.setAddTime(LocalDateTime.now());

                orderItems.add(orderItem);
            }

            // 批量保存订单明细
            restaurantOrderItemService.saveBatch(orderItems);
        }

        // 6. 减少菜品库存并增加销量
        try {
            decreaseOrderDishStock(order.getId());
        } catch (Exception e) {
            log.error("订单【{}】减少库存失败: {}", order.getOrderNo(), e.getMessage(), e);
            throw new BusinessException("库存不足或减少失败：" + e.getMessage());
        }

        // 6.5 余额支付：扣减用户余额并记录流水（user-service 本地事务原子完成）
        // 【微服务改造】刻意放在库存扣减之后：远程扣款失败会抛异常，本地事务（订单/明细/库存）整体回滚，无需补偿
        if (user != null) {
            BigDecimal newBalance = FeignResultUtil.unwrap(userFeignClient.changeBalance(BalanceChangeDTO.builder()
                    .userId(request.getUserId())
                    .amount(calculatedActualAmount)
                    .type(BalanceResourceEnum.EXPENSE.getCode())
                    .orderNo(orderNo)
                    .operateUserId(StpUtil.getLoginIdAsLong())
                    .build()));
            log.info("收银台余额支付扣款: orderId={}, userId={}, actualAmount={}, newBalance={}",
                    order.getId(), request.getUserId(), calculatedActualAmount, newBalance);
        }

        // 7. 打印小票：根据门店打印配置 + 请求的是否打印一起判断，使用门店配置的打印机批量打印（与普通下单逻辑一致）
        if (request.getStoreId() != null) {
            StoreInfo storeInfoForPrint = storeInfoMapper.selectById(request.getStoreId());
            if (storeInfoForPrint != null && storeInfoForPrint.getIsPrint() == BooleanEnum.YES
                    && Boolean.TRUE.equals(request.getPrintReceipt())) {
                try {
                    printOrderWithStorePrinters(order.getId(), storeInfoForPrint.getId());
                    log.info("收银台订单已按门店打印机配置打印小票: orderId={}, orderNo={}", order.getId(), orderNo);
                } catch (Exception e) {
                    log.error("打印小票失败: orderId={}, error={}", order.getId(), e.getMessage(), e);
                    // 打印失败不影响订单创建
                }
            }
        }

        // 8. 返回订单信息
        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(order);

        log.info("收银台直接下单完成: orderId={}, orderNo={}, totalAmount={}, actualAmount={}",
                order.getId(), orderNo, order.getTotalAmount(), order.getActualAmount());

        return orderVO;
    }

    /**
     * 收银台-桌位结算：对会话当前订单做线下支付，并结束会话、释放桌位。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestaurantOrderVO cashierSettleSession(CashierSettleSessionRequest request) {
        String sessionNo = request.getSessionNo();
        if (StrUtil.isBlank(sessionNo)) {
            throw new BusinessException("会话编号不能为空");
        }

        // 1. 查询会话
        LambdaQueryWrapper<TableSession> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.eq(TableSession::getSessionNo, sessionNo);
        TableSession session = tableSessionService.getOne(sessionWrapper);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        if (session.getStatus() == SessionStatusEnum.ENDED) {
            throw new BusinessException("会话已结束");
        }

        Long orderId = session.getCurrentOrderId();
        if (orderId == null) {
            throw new BusinessException("请先提交订单再结算");
        }

        // 2. 查询订单并校验
        RestaurantOrder order = this.getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!sessionNo.equals(order.getSessionNo())) {
            throw new BusinessException("订单与会话不匹配");
        }
        if (order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            throw new BusinessException("订单已支付，无需重复结算");
        }

        // 2.5 收银传入的备注、折扣：更新订单（与 CashierOrderRequest 一致）
        if (request.getNote() != null) {
            order.setNote(request.getNote());
        }
        if (request.getDiscountRate() != null && request.getDiscountRate().compareTo(BigDecimal.ONE) != 0)
            org.hongtu.security.OperationPermission.order("discount");
        if (request.getDiscountRate() != null && request.getDiscountRate().compareTo(BigDecimal.ZERO) >= 0
                && request.getDiscountRate().compareTo(BigDecimal.ONE) <= 0) {
            BigDecimal currentPayable = order.getActualAmount() != null ? order.getActualAmount() : order.getTotalAmount();
            if (currentPayable == null) {
                currentPayable = BigDecimal.ZERO;
            }
            BigDecimal newActualAmount = currentPayable.multiply(request.getDiscountRate()).setScale(2, RoundingMode.HALF_UP);
            order.setDiscountRate(request.getDiscountRate());
            order.setActualAmount(newActualAmount);
        }
        // 优惠金额（菜品优惠+折扣优惠）始终 = 总价 - 实付，保证无折扣时也正确显示
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal actual = order.getActualAmount() != null ? order.getActualAmount() : total;
        order.setDiscountAmount(total.subtract(actual).max(BigDecimal.ZERO));

        BigDecimal payAmount = request.getActualAmount() != null ? request.getActualAmount() : order.getActualAmount();
        if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) < 0) {
            payAmount = order.getActualAmount();
        }
        if (payAmount == null || payAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("实收金额不正确");
        }

        PaymentTypeEnum paymentType = request.getPaymentType();
        if (paymentType == null) {
            paymentType = PaymentTypeEnum.CASH;
        }
        BigDecimal deductedBalanceAmount = null;
        if (paymentType == PaymentTypeEnum.BANLANCE_PAY) {
            if (request.getUserId() == null) {
                throw new BusinessException("余额支付请先选择会员");
            }
            SysUser user = FeignResultUtil.unwrap(userFeignClient.getUserById(request.getUserId()));
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            BigDecimal needPay = order.getActualAmount() != null ? order.getActualAmount() : order.getTotalAmount();
            if (needPay == null) needPay = BigDecimal.ZERO;
            if (user.getSurplusMoney() == null || user.getSurplusMoney().compareTo(needPay) < 0) {
                throw new BusinessException("用户余额不足，当前余额：" +
                        (user.getSurplusMoney() != null ? user.getSurplusMoney() : "0") + " 元");
            }
            // 扣减余额并记录流水（user-service 本地事务原子完成；失败抛异常，本地事务回滚）
            FeignResultUtil.check(userFeignClient.changeBalance(BalanceChangeDTO.builder()
                    .userId(request.getUserId())
                    .amount(needPay)
                    .type(BalanceResourceEnum.EXPENSE.getCode())
                    .orderNo(order.getOrderNo())
                    .operateUserId(StpUtil.getLoginIdAsLong())
                    .build()));
            deductedBalanceAmount = needPay;
        }

        try {
            // 3. 更新订单为已支付
            order.setPaymentStatus(PaymentStatusEnum.PAID);
            order.setPaymentType(paymentType);
            order.setPaymentTime(LocalDateTime.now());
            order.setActualAmount(payAmount);
            order.setStatus(OrderStatusEnum.COMPLETED);
            order.setUpdatedAt(LocalDateTime.now());
            this.updateById(order);

            // 4. 结束会话并更新桌位状态为空闲
            tableSessionService.endSession(sessionNo, payAmount);

            // 5. 打印小票（可选）
            if (Boolean.TRUE.equals(request.getPrintReceipt())) {
                try {
                    orderPrintService.printOrder(order.getId());
                } catch (Exception e) {
                    log.error("桌位结算打印小票失败: orderId={}", order.getId(), e);
                }
            }

            log.info("桌位结算成功: sessionNo={}, orderId={}, orderNo={}, payAmount={}, paymentType={}",
                    sessionNo, order.getId(), order.getOrderNo(), payAmount, paymentType);

            return restaurantOrderConverter.toVO(order);
        } catch (RuntimeException exception) {
            // 用户服务已完成远程扣款时，本地订单/会话失败无法靠本地事务回滚，必须补偿退款。
            if (deductedBalanceAmount != null) {
                refundBalanceCompensate(request.getUserId(), deductedBalanceAmount, order.getOrderNo());
            }
            throw exception;
        }
    }

    /**
     * 减少订单中所有菜品的库存并增加销量
     * @param orderId 订单ID
     * @throws BusinessException 如果库存不足
     */
    private void decreaseOrderDishStock(Long orderId) {
        // 查询该订单的所有订单明细
        LambdaQueryWrapper<RestaurantOrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(RestaurantOrderItem::getOrderId, orderId);
        List<RestaurantOrderItem> orderItems = restaurantOrderItemService.list(itemWrapper);

        if (orderItems != null && !orderItems.isEmpty()) {
            // 构建菜品ID和数量的映射
            Map<Long, Integer> dishStockMap = new HashMap<>();
            for (RestaurantOrderItem item : orderItems) {
                Long dishId = item.getDishId();
                Integer quantity = item.getQuantity();

                // 累加相同菜品的数量（防止同一个菜品多次出现）
                dishStockMap.put(dishId, dishStockMap.getOrDefault(dishId, 0) + quantity);
            }

            // 批量减少库存
            dishService.batchDecreaseStock(dishStockMap);
            log.info("订单【orderId={}】已减少菜品库存: {}", orderId, dishStockMap);
        }
    }

    /**
     * 生成取餐码（叫号自取模式使用）
     * 策略：每天按顺序从001开始递增，第二天重新从001开始
     * 格式：少于3位数字时前面补0（001、002...999、1000、1001...）
     */
    private synchronized String generatePickupCode() {
        try {
            // 获取今天的日期（格式：yyyy-MM-dd）
            String today = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // 查询今天最大的取餐码（按数值大小排序）
            LambdaQueryWrapper<RestaurantOrder> wrapper = new LambdaQueryWrapper<>();
            wrapper.apply("DATE_FORMAT(created_at, '%Y-%m-%d') = {0}", today)
                    .isNotNull(RestaurantOrder::getPickupCode)
                    .orderByDesc(RestaurantOrder::getPickupCode)
                    .last("LIMIT 1");

            RestaurantOrder lastOrder = this.getOne(wrapper);

            int nextCode = 1; // 默认从001开始

            if (lastOrder != null && lastOrder.getPickupCode() != null) {
                try {
                    // 获取最大取餐码并+1
                    int lastCode = Integer.parseInt(lastOrder.getPickupCode());
                    nextCode = lastCode + 1;
                } catch (NumberFormatException e) {
                    log.warn("解析取餐码失败，使用默认值001: {}", lastOrder.getPickupCode());
                }
            }

            // 少于3位数字时前面补0，超过3位数字直接返回
            if (nextCode < 1000) {
                return String.format("%03d", nextCode);
            } else {
                return String.valueOf(nextCode);
            }

        } catch (Exception e) {
            log.error("生成取餐码失败，使用随机码", e);
            // 降级方案：生成随机取餐码
            int code = (int) ((Math.random() * 999) + 1);
            return String.format("%03d", code);
        }
    }

    /**
     * 验证购物车商品数据（CreateOrderRequest）
     * 验证内容：
     * 1. 菜品是否存在
     * 2. 菜品基础价格（basePrice）是否正确
     * 3. 规格额外价格（extraPrice）是否正确
     * 4. 菜品总价（price = basePrice + extraPrice）是否正确
     *
     * @param cartItems 购物车商品列表
     * @throws BusinessException 验证失败时抛出异常
     */
    private void validateCreateOrderCartItems(List<CreateOrderRequest.CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BusinessException("购物车为空，无法创建订单");
        }

        log.info("开始验证购物车商品数据，共{}个商品", cartItems.size());

        for (CreateOrderRequest.CartItem cartItem : cartItems) {
            // 跳过赠送菜品的验证（赠送菜品价格为0）
            if (cartItem.getIsGift() != null && cartItem.getIsGift() == 1) {
                org.hongtu.security.OperationPermission.order("gift");
                log.info("菜品【{}】为赠送菜品，跳过价格验证", cartItem.getDishName());
                continue;
            }

            // 1. 查询菜品信息
            Dish dish = dishService.getById(cartItem.getDishId());
            if (dish == null) {
                throw new BusinessException("菜品【" + cartItem.getDishName() + "】不存在");
            }

            // 2. 验证基础价格（basePrice）
            // basePrice应该等于菜品的price（优惠价）或originalPrice（原价）
            BigDecimal dbBasePrice = dish.getPrice() != null ? dish.getPrice() : dish.getOriginalPrice();
            if (dbBasePrice == null) {
                throw new BusinessException("菜品【" + dish.getDishName() + "】价格未设置");
            }

            // 允许小数点后2位的误差
            if (cartItem.getBasePrice() == null ||
                    cartItem.getBasePrice().subtract(dbBasePrice).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new BusinessException(String.format(
                        "菜品【%s】基础价格不正确，数据库价格：%s，前端传入：%s",
                        dish.getDishName(),
                        dbBasePrice,
                        cartItem.getBasePrice()
                ));
            }

            // 3. 验证规格额外价格（extraPrice）
            BigDecimal calculatedExtraPrice = BigDecimal.ZERO;

            // 如果有规格文本，说明选择了规格
            if (StrUtil.isNotBlank(cartItem.getSpecText())) {
                // 查询该菜品的所有规格选项
                LambdaQueryWrapper<DishSpecOption> specWrapper = new LambdaQueryWrapper<>();
                specWrapper.eq(DishSpecOption::getDishId, cartItem.getDishId());
                List<DishSpecOption> specOptions = dishSpecOptionMapper.selectList(specWrapper);

                if (specOptions != null && !specOptions.isEmpty()) {
                    // 解析前端传来的规格文本，例如："1桶" 或 "1桶,微辣"
                    String[] specTexts = cartItem.getSpecText().split(",");

                    for (DishSpecOption specOption : specOptions) {
                        // 解析数据库中的规格值JSON数组
                        List<org.hongtu.restaurant.VO.SpecValue> specValues = specOption.getSpecValueList();

                        if (specValues != null && !specValues.isEmpty()) {
                            // 遍历规格文本，查找匹配的规格值
                            for (String specText : specTexts) {
                                specText = specText.trim();

                                for (org.hongtu.restaurant.VO.SpecValue specValue : specValues) {
                                    if (specValue.getValue().equals(specText)) {
                                        // 找到匹配的规格，累加额外价格
                                        if (specValue.getExtraPrice() != null) {
                                            calculatedExtraPrice = calculatedExtraPrice.add(specValue.getExtraPrice());
                                        }
                                        log.debug("找到规格【{}】，额外价格：{}", specText, specValue.getExtraPrice());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 验证前端传来的extraPrice是否正确
            if (cartItem.getExtraPrice() == null) {
                cartItem.setExtraPrice(BigDecimal.ZERO);
            }

            if (cartItem.getExtraPrice().subtract(calculatedExtraPrice).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new BusinessException(String.format(
                        "菜品【%s】规格额外价格不正确，规格：%s，计算价格：%s，前端传入：%s",
                        dish.getDishName(),
                        cartItem.getSpecText(),
                        calculatedExtraPrice,
                        cartItem.getExtraPrice()
                ));
            }

            // 4. 验证菜品总价（price = basePrice + extraPrice）
            BigDecimal calculatedPrice = cartItem.getBasePrice().add(cartItem.getExtraPrice());

            if (cartItem.getPrice() == null ||
                    cartItem.getPrice().subtract(calculatedPrice).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new BusinessException(String.format(
                        "菜品【%s】总价不正确，基础价格：%s，规格价格：%s，计算总价：%s，前端传入：%s",
                        dish.getDishName(),
                        cartItem.getBasePrice(),
                        cartItem.getExtraPrice(),
                        calculatedPrice,
                        cartItem.getPrice()
                ));
            }

            log.info("菜品【{}】验证通过，基础价格：{}，规格价格：{}，总价：{}",
                    dish.getDishName(), cartItem.getBasePrice(), cartItem.getExtraPrice(), cartItem.getPrice());
        }

        log.info("购物车商品数据验证完成");
    }

    /**
     * 验证购物车商品数据（CashierOrderRequest）
     * 验证内容：
     * 1. 菜品是否存在
     * 2. 菜品基础价格（basePrice）是否正确
     * 3. 规格额外价格（extraPrice）是否正确
     * 4. 菜品总价（price = basePrice + extraPrice）是否正确
     *
     * @param cartItems 购物车商品列表
     * @throws BusinessException 验证失败时抛出异常
     */
    private void validateCartItems(List<CashierOrderRequest.CartItem> cartItems) {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new BusinessException("购物车为空，无法创建订单");
        }

        log.info("开始验证购物车商品数据，共{}个商品", cartItems.size());

        for (CashierOrderRequest.CartItem cartItem : cartItems) {
            // 跳过赠送菜品的验证（赠送菜品价格为0）
            if (cartItem.getIsGift() != null && cartItem.getIsGift() == 1) {
                org.hongtu.security.OperationPermission.order("gift");
                log.info("菜品【{}】为赠送菜品，跳过价格验证", cartItem.getDishName());
                continue;
            }

            // 1. 查询菜品信息
            Dish dish = dishService.getById(cartItem.getDishId());
            if (dish == null) {
                throw new BusinessException("菜品【" + cartItem.getDishName() + "】不存在");
            }

            // 2. 验证基础价格（basePrice）
            // basePrice应该等于菜品的price（优惠价）或originalPrice（原价）
            BigDecimal dbBasePrice = dish.getPrice() != null ? dish.getPrice() : dish.getOriginalPrice();
            if (dbBasePrice == null) {
                throw new BusinessException("菜品【" + dish.getDishName() + "】价格未设置");
            }

            // 允许小数点后2位的误差
            if (cartItem.getBasePrice() == null ||
                    cartItem.getBasePrice().subtract(dbBasePrice).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new BusinessException(String.format(
                        "菜品【%s】基础价格不正确，数据库价格：%s，前端传入：%s",
                        dish.getDishName(),
                        dbBasePrice,
                        cartItem.getBasePrice()
                ));
            }

            // 3. 验证规格额外价格（extraPrice）
            BigDecimal calculatedExtraPrice = BigDecimal.ZERO;

            // 如果有规格文本，说明选择了规格
            if (StrUtil.isNotBlank(cartItem.getSpecText())) {
                // 查询该菜品的所有规格选项
                LambdaQueryWrapper<DishSpecOption> specWrapper = new LambdaQueryWrapper<>();
                specWrapper.eq(DishSpecOption::getDishId, cartItem.getDishId());
                List<DishSpecOption> specOptions = dishSpecOptionMapper.selectList(specWrapper);

                if (specOptions != null && !specOptions.isEmpty()) {
                    // 解析前端传来的规格文本，例如："1桶" 或 "1桶,微辣"
                    String[] specTexts = cartItem.getSpecText().split(",");

                    for (DishSpecOption specOption : specOptions) {
                        // 解析数据库中的规格值JSON数组
                        List<org.hongtu.restaurant.VO.SpecValue> specValues = specOption.getSpecValueList();

                        if (specValues != null && !specValues.isEmpty()) {
                            // 遍历规格文本，查找匹配的规格值
                            for (String specText : specTexts) {
                                specText = specText.trim();

                                for (org.hongtu.restaurant.VO.SpecValue specValue : specValues) {
                                    if (specValue.getValue().equals(specText)) {
                                        // 找到匹配的规格，累加额外价格
                                        if (specValue.getExtraPrice() != null) {
                                            calculatedExtraPrice = calculatedExtraPrice.add(specValue.getExtraPrice());
                                        }
                                        log.debug("找到规格【{}】，额外价格：{}", specText, specValue.getExtraPrice());
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 验证前端传来的extraPrice是否正确
            if (cartItem.getExtraPrice() == null) {
                cartItem.setExtraPrice(BigDecimal.ZERO);
            }

            if (cartItem.getExtraPrice().subtract(calculatedExtraPrice).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new BusinessException(String.format(
                        "菜品【%s】规格额外价格不正确，规格：%s，计算价格：%s，前端传入：%s",
                        dish.getDishName(),
                        cartItem.getSpecText(),
                        calculatedExtraPrice,
                        cartItem.getExtraPrice()
                ));
            }

            // 4. 验证菜品总价（price = basePrice + extraPrice）
            BigDecimal calculatedPrice = cartItem.getBasePrice().add(cartItem.getExtraPrice());

            if (cartItem.getPrice() == null ||
                    cartItem.getPrice().subtract(calculatedPrice).abs().compareTo(new BigDecimal("0.01")) > 0) {
                throw new BusinessException(String.format(
                        "菜品【%s】总价不正确，基础价格：%s，规格价格：%s，计算总价：%s，前端传入：%s",
                        dish.getDishName(),
                        cartItem.getBasePrice(),
                        cartItem.getExtraPrice(),
                        calculatedPrice,
                        cartItem.getPrice()
                ));
            }

            log.info("菜品【{}】验证通过，基础价格：{}，规格价格：{}，总价：{}",
                    dish.getDishName(), cartItem.getBasePrice(), cartItem.getExtraPrice(), cartItem.getPrice());
        }

        log.info("购物车商品数据验证完成");
    }

    /**
     * 根据门店配置打印订单
     * 当门店开启小票打印后，查询该门店启用的打印机，
     * 调用每台启用的打印机及其配置的模板进行打印
     *
     * @param orderId 订单ID
     * @param storeId 门店ID
     */
    private void printOrderWithStorePrinters(Long orderId, Long storeId) {
        try {
            // 1. 查询该门店所有启用的打印机
            List<PrinterInfo> enabledPrinters = printerInfoService.getEnabledPrintersByStore(storeId);

            if (enabledPrinters == null || enabledPrinters.isEmpty()) {
                log.warn("门店【{}】开启了打印功能，但未配置启用的打印机", storeId);
                return;
            }

            log.info("门店【{}】开始打印订单【{}】，共有{}台启用的打印机", storeId, orderId, enabledPrinters.size());

            // 2. 遍历所有启用的打印机，分别打印
            for (PrinterInfo printer : enabledPrinters) {
                try {
                    // 获取打印机配置的模板ID（可能为null，打印服务会使用默认模板）
                    Long templateId = printer.getTemplateId();

                    // 调用打印服务
                    Map<String, Object> printResult = orderPrintService.printOrder(orderId, printer.getId(), templateId);

                    boolean success = (boolean) printResult.getOrDefault("success", false);
                    if (success) {
                        log.info("✅ 订单【{}】在打印机【{}】（{}）打印成功",
                                orderId, printer.getId(), printer.getRemark());
                    } else {
                        log.warn("❌ 订单【{}】在打印机【{}】（{}）打印失败：{}",
                                orderId, printer.getId(), printer.getRemark(), printResult.get("msg"));
                    }
                } catch (Exception e) {
                    // 单台打印机打印失败不影响其他打印机
                    log.error("订单【{}】在打印机【{}】（{}）打印异常：{}",
                            orderId, printer.getId(), printer.getRemark(), e.getMessage(), e);
                }
            }

            log.info("门店【{}】订单【{}】打印任务完成", storeId, orderId);

        } catch (Exception e) {
            // 打印失败不影响订单创建
            log.error("订单【{}】打印任务执行异常：{}", orderId, e.getMessage(), e);
        }
    }

    /**
     * 仅打印本批加菜项（加菜单）到门店所有启用打印机，不打印整单
     */
    private void printAddItemsWithStorePrinters(Long orderId, List<Long> orderItemIds, Long storeId) {
        try {
            List<PrinterInfo> enabledPrinters = printerInfoService.getEnabledPrintersByStore(storeId);
            if (enabledPrinters == null || enabledPrinters.isEmpty()) {
                log.warn("门店【{}】开启了打印功能，但未配置启用的打印机", storeId);
                return;
            }
            log.info("门店【{}】开始打印加菜单 orderId={}，本批{}项，共{}台打印机", storeId, orderId, orderItemIds.size(), enabledPrinters.size());
            for (PrinterInfo printer : enabledPrinters) {
                try {
                    Long templateId = printer.getTemplateId();
                    Map<String, Object> printResult = orderPrintService.printAddItems(orderId, orderItemIds, printer.getId(), templateId);
                    boolean success = (boolean) printResult.getOrDefault("success", false);
                    if (success) {
                        log.info("✅ 加菜单 orderId={} 在打印机【{}】打印成功", orderId, printer.getRemark());
                    } else {
                        log.warn("❌ 加菜单 orderId={} 在打印机【{}】打印失败：{}", orderId, printer.getRemark(), printResult.get("msg"));
                    }
                } catch (Exception e) {
                    log.error("加菜单 orderId={} 在打印机【{}】打印异常", orderId, printer.getRemark(), e);
                }
            }
        } catch (Exception e) {
            log.error("加菜单打印任务执行异常: orderId={}", orderId, e);
        }
    }

    // ==================== 优惠券计算（下单专用） ====================

    /**
     * 获取优惠券计算优先级（数值越小越先计算）
     * 计算顺序：菜品免单券(1) > 满减券(2) > 整单折扣券(3)
     */
    private int getCouponPriority(CouponTypeEnum type) {
        if (type == null) return 99;
        return switch (type) {
            case DISH_FREE -> 1;       // 菜品免单最先
            case FULL_REDUCTION -> 2;  // 满减
            case ORDER_DISCOUNT -> 3;  // 整单折扣最后
        };
    }

    // ==================== 优惠券计算接口 ====================

    /**
     * 计算优惠券的优惠金额（支持单张或多张）
     * 后端自动校验：有效性、是否属于用户、叠加规则、互斥性
     * 菜品免单券需要查询菜品实时价格
     *
     * @param userId 用户ID
     * @param userCouponIds 用户优惠券ID列表
     * @param orderTotalAmount 订单总金额（用于计算整单折扣和满减门槛）
     */
    @Override
    public Result calcCouponDiscount(Long userId, List<Long> userCouponIds, BigDecimal orderTotalAmount) {
        if (userId == null) {
            return Result.error("用户ID不能为空");
        }

        if (orderTotalAmount == null) {
            orderTotalAmount = BigDecimal.ZERO;
        }

        // 没有选择优惠券
        if (userCouponIds == null || userCouponIds.isEmpty()) {
            CouponDiscountResultVO resultVO = CouponDiscountResultVO.builder()
                    .usable(true)
                    .totalDiscountAmount(BigDecimal.ZERO)
                    .couponDetails(new ArrayList<>())
                    .build();
            return Result.success(resultVO);
        }

        try {
            // 1. 多券时校验叠加规则（可叠加性、互斥性、同类型互斥，Feign 调用 coupon-service）
            if (userCouponIds.size() > 1) {
                FeignResultUtil.check(couponFeignClient.validateCouponStackRules(userCouponIds));
            }

            // 2. 批量查询用户优惠券
            List<UserCoupon> userCoupons = FeignResultUtil.unwrap(couponFeignClient.getUserCouponsByIds(userCouponIds));
            if (userCoupons.size() != userCouponIds.size()) {
                CouponDiscountResultVO resultVO = CouponDiscountResultVO.builder()
                        .usable(false)
                        .reason("部分优惠券不存在")
                        .build();
                return Result.success(resultVO);
            }

            // 3. 逐个校验优惠券有效性
            LocalDateTime now = LocalDateTime.now();
            for (UserCoupon uc : userCoupons) {
                // 校验是否属于当前用户
                if (!userId.equals(uc.getUserId())) {
                    CouponDiscountResultVO resultVO = CouponDiscountResultVO.builder()
                            .usable(false)
                            .reason("优惠券不属于当前用户")
                            .build();
                    return Result.success(resultVO);
                }
                // 校验状态
                if (uc.getStatus() != CouponStatusEnum.NOT_USE) {
                    CouponDiscountResultVO resultVO = CouponDiscountResultVO.builder()
                            .usable(false)
                            .reason("优惠券" + uc.getStatus().getDesc())
                            .build();
                    return Result.success(resultVO);
                }
                // 校验有效期
                if (uc.getStartTime() != null && uc.getStartTime().isAfter(now)) {
                    CouponDiscountResultVO resultVO = CouponDiscountResultVO.builder()
                            .usable(false)
                            .reason("优惠券尚未生效")
                            .build();
                    return Result.success(resultVO);
                }
                if (uc.getExpireTime() != null && uc.getExpireTime().isBefore(now)) {
                    CouponDiscountResultVO resultVO = CouponDiscountResultVO.builder()
                            .usable(false)
                            .reason("优惠券已过期")
                            .build();
                    return Result.success(resultVO);
                }
            }

            // 4. 批量查询优惠券详情
            List<Long> couponIds = userCoupons.stream()
                    .map(UserCoupon::getCouponId).collect(Collectors.toList());
            List<Coupon> coupons = FeignResultUtil.unwrap(couponFeignClient.getCouponsByIds(couponIds));
            Map<Long, Coupon> couponMap = coupons.stream()
                    .collect(Collectors.toMap(Coupon::getId, c -> c));

            // 5. 按优先级排序：菜品免单券 > 满减券 > 整单折扣券
            List<UserCoupon> sortedUserCoupons = new ArrayList<>(userCoupons);
            sortedUserCoupons.sort((a, b) -> {
                Coupon ca = couponMap.get(a.getCouponId());
                Coupon cb = couponMap.get(b.getCouponId());
                return getCouponPriority(ca.getType()) - getCouponPriority(cb.getType());
            });

            // 6. 递减计算各优惠券的优惠金额（整单折扣基于前面券折扣后的剩余金额）
            BigDecimal remainingAmount = orderTotalAmount;
            BigDecimal totalDiscount = BigDecimal.ZERO;
            List<CouponDiscountResultVO.CouponDetailVO> couponDetails = new ArrayList<>();

            for (UserCoupon uc : sortedUserCoupons) {
                Coupon coupon = couponMap.get(uc.getCouponId());
                if (coupon == null) {
                    continue;
                }

                // 计算优惠金额（根据券类型和剩余金额）
                BigDecimal discountAmount = calculateCouponDiscountAmount(coupon, remainingAmount);

                // 构建优惠券明细VO
                CouponDiscountResultVO.CouponDetailVO.CouponDetailVOBuilder detailBuilder = CouponDiscountResultVO.CouponDetailVO.builder()
                        .userCouponId(uc.getId())
                        .couponId(coupon.getId())
                        .couponName(coupon.getName())
                        .couponType(coupon.getType())
                        .couponTypeDesc(coupon.getType().getDesc())
                        .discountAmount(discountAmount)
                        .value(coupon.getValue())
                        .minAmount(coupon.getMinAmount())
                        .startTime(uc.getStartTime())
                        .expireTime(uc.getExpireTime());

                // 菜品免单券附加关联菜品信息（包含实时价格）
                if (coupon.getType() == CouponTypeEnum.DISH_FREE) {
                    List<CouponDishItem> dishItems = FeignResultUtil.unwrap(couponFeignClient.getItemsByCouponId(coupon.getId()));
                    List<CouponDiscountResultVO.DishItemVO> dishItemVOList = new ArrayList<>();
                    for (CouponDishItem item : dishItems) {
                        Dish dish = dishService.getById(item.getDishId());
                        if (dish != null) {
                            // 优先使用优惠价，没有则用原价
                            BigDecimal price = dish.getPrice() != null ? dish.getPrice() : dish.getOriginalPrice();
                            CouponDiscountResultVO.DishItemVO dishItemVO = CouponDiscountResultVO.DishItemVO.builder()
                                    .dishId(dish.getId())
                                    .dishName(dish.getDishName())
                                    .dishPic(dish.getDishPics())
                                    .price(price)
                                    .originalPrice(dish.getOriginalPrice())
                                    .build();
                            dishItemVOList.add(dishItemVO);
                        }
                    }
                    detailBuilder.dishItems(dishItemVOList);
                }

                couponDetails.add(detailBuilder.build());
                totalDiscount = totalDiscount.add(discountAmount);

                // 更新剩余金额（用于下一张整单折扣券计算）
                remainingAmount = remainingAmount.subtract(discountAmount);
                if (remainingAmount.compareTo(BigDecimal.ZERO) < 0) {
                    remainingAmount = BigDecimal.ZERO;
                }

                log.info("优惠券计算: userCouponId={}, type={}, discountAmount={}, remainingAmount={}",
                        uc.getId(), coupon.getType(), discountAmount, remainingAmount);
            }

            CouponDiscountResultVO resultVO = CouponDiscountResultVO.builder()
                    .usable(true)
                    .totalDiscountAmount(totalDiscount)
                    .couponDetails(couponDetails)
                    .build();

            log.info("计算优惠金额成功 - userId: {}, orderTotalAmount: {}, couponCount: {}, totalDiscount: {}",
                    userId, orderTotalAmount, userCouponIds.size(), totalDiscount);
            return Result.success(resultVO);

        } catch (BusinessException e) {
            CouponDiscountResultVO resultVO = CouponDiscountResultVO.builder()
                    .usable(false)
                    .reason(e.getMessage())
                    .build();
            return Result.success(resultVO);
        }
    }

    /**
     * 计算单张优惠券的优惠金额
     *
     * @param coupon 优惠券信息
     * @param orderAmount 订单金额（用于计算整单折扣和满减门槛）
     * @return 优惠金额
     */
    private BigDecimal calculateCouponDiscountAmount(Coupon coupon, BigDecimal orderAmount) {
        CouponTypeEnum type = coupon.getType();

        return switch (type) {
            case FULL_REDUCTION -> {
                // 满减券：检查是否满足最低消费门槛
                BigDecimal minAmount = coupon.getMinAmount() != null ? coupon.getMinAmount() : BigDecimal.ZERO;
                if (orderAmount.compareTo(minAmount) >= 0) {
                    // 满足门槛，返回券面值
                    yield coupon.getValue() != null ? coupon.getValue() : BigDecimal.ZERO;
                } else {
                    // 不满足门槛，返回0
                    yield BigDecimal.ZERO;
                }
            }

            case ORDER_DISCOUNT -> {
                // 整单折扣券：订单金额 * (1 - 折扣率)
                // value 为折扣率，例如 0.8 表示8折，优惠金额 = orderAmount * (1 - 0.8) = orderAmount * 0.2
                BigDecimal discountRate = coupon.getValue() != null ? coupon.getValue() : BigDecimal.ONE;
                BigDecimal discountAmount = orderAmount.multiply(BigDecimal.ONE.subtract(discountRate));
                // 保留两位小数，向下取整
                yield discountAmount.setScale(2, RoundingMode.DOWN);
            }

            case DISH_FREE -> {
                // 菜品免单券：查询关联菜品的实时价格计算总免单金额（Feign 调用 coupon-service）
                List<CouponDishItem> dishItems = FeignResultUtil.unwrap(couponFeignClient.getItemsByCouponId(coupon.getId()));
                if (dishItems == null || dishItems.isEmpty()) {
                    yield BigDecimal.ZERO;
                }
                BigDecimal total = BigDecimal.ZERO;
                for (CouponDishItem item : dishItems) {
                    Dish dish = dishService.getById(item.getDishId());
                    if (dish != null) {
                        // 优先使用优惠价，没有则用原价
                        BigDecimal price = dish.getPrice() != null ? dish.getPrice() : dish.getOriginalPrice();
                        if (price != null) {
                            total = total.add(price);
                        }
                    }
                }
                yield total;
            }
        };
    }

    // ================== 退菜功能实现 ==================

    /**
     * 退菜（管理员操作）
     *
     * @param request 退菜请求
     * @return 退菜结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundDishVO refundDish(RefundDishRequest request) {
        log.info("开始处理退菜请求: orderId={}, refundItemsCount={}, reason={}",
                request.getOrderId(), request.getRefundItems().size(), request.getRefundReason());

        // 1. 查询订单信息
        RestaurantOrder order = this.getById(request.getOrderId());
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }

        // 2. 校验订单状态
        validateOrderStatusForRefund(order);

        // 3. 查询订单项信息
        LambdaQueryWrapper<RestaurantOrderItem> itemQueryWrapper = new LambdaQueryWrapper<>();
        itemQueryWrapper.eq(RestaurantOrderItem::getOrderId, request.getOrderId());
        List<RestaurantOrderItem> orderItems = restaurantOrderItemService.list(itemQueryWrapper);

        if (orderItems == null || orderItems.isEmpty()) {
            throw new IllegalArgumentException("订单明细不存在");
        }

        // 4. 构建订单项ID到订单项的映射
        Map<Long, RestaurantOrderItem> orderItemMap = orderItems.stream()
                .collect(Collectors.toMap(RestaurantOrderItem::getId, item -> item));

        // 5. 处理每个退菜项（并汇总退菜数量用于已支付订单的库存/销量回滚）
        BigDecimal totalRefundAmount = BigDecimal.ZERO;
        List<RefundDishVO.RefundItemDetail> refundDetails = new ArrayList<>();
        List<RestaurantOrderItem> itemsToUpdate = new ArrayList<>();
        List<Long> itemsToDelete = new ArrayList<>();
        Map<Long, Integer> refundDishQuantityMap = new HashMap<>(); // dishId -> 退菜数量合计

        for (RefundDishRequest.RefundItem refundItem : request.getRefundItems()) {
            RestaurantOrderItem orderItem = orderItemMap.get(refundItem.getOrderItemId());

            if (orderItem == null) {
                throw new IllegalArgumentException("订单项不存在: " + refundItem.getOrderItemId());
            }

            // 校验退菜数量
            if (refundItem.getRefundQuantity() > orderItem.getQuantity()) {
                throw new IllegalArgumentException(
                        String.format("退菜数量不能超过订单数量，订单项ID: %d, 订单数量: %d, 退菜数量: %d",
                                orderItem.getId(), orderItem.getQuantity(), refundItem.getRefundQuantity())
                );
            }

            // 计算退款金额
            BigDecimal refundAmount;
            if (refundItem.getRefundAmount() != null) {
                // 使用指定的退款金额
                refundAmount = refundItem.getRefundAmount();
            } else {
                // 自动计算：单价 * 退菜数量
                BigDecimal unitPrice = orderItem.getTotalPrice().divide(
                        BigDecimal.valueOf(orderItem.getQuantity()), 2, RoundingMode.HALF_UP);
                refundAmount = unitPrice.multiply(BigDecimal.valueOf(refundItem.getRefundQuantity()));
            }

            totalRefundAmount = totalRefundAmount.add(refundAmount);

            // 更新订单项
            int remainingQuantity = orderItem.getQuantity() - refundItem.getRefundQuantity();
            if (remainingQuantity == 0) {
                // 全部退菜，删除订单项
                itemsToDelete.add(orderItem.getId());
            } else {
                // 部分退菜，更新数量和金额
                orderItem.setQuantity(remainingQuantity);
                BigDecimal newTotalPrice = orderItem.getTotalPrice().subtract(refundAmount);
                orderItem.setTotalPrice(newTotalPrice);
                orderItem.setUpdatedAt(LocalDateTime.now());
                itemsToUpdate.add(orderItem);
            }

            // 构建退菜明细
            RefundDishVO.RefundItemDetail detail = new RefundDishVO.RefundItemDetail();
            detail.setOrderItemId(orderItem.getId());
            detail.setDishId(orderItem.getDishId());
            detail.setDishName(getDishName(orderItem.getDishId()));
            detail.setOriginalQuantity(orderItem.getQuantity() + refundItem.getRefundQuantity());
            detail.setRefundQuantity(refundItem.getRefundQuantity());
            detail.setRemainingQuantity(remainingQuantity);
            detail.setPrice(orderItem.getPrice());
            detail.setRefundAmount(refundAmount);
            detail.setSpecOptionsText(orderItem.getSpecOptionsText());
            refundDetails.add(detail);

            refundDishQuantityMap.merge(orderItem.getDishId(), refundItem.getRefundQuantity(), Integer::sum);

            log.info("处理退菜项: orderItemId={}, dishId={}, originalQty={}, refundQty={}, refundAmount={}",
                    orderItem.getId(), orderItem.getDishId(),
                    orderItem.getQuantity() + refundItem.getRefundQuantity(),
                    refundItem.getRefundQuantity(), refundAmount);
        }

        // 6. 批量更新/删除订单项
        if (!itemsToUpdate.isEmpty()) {
            restaurantOrderItemService.updateBatchById(itemsToUpdate);
            log.info("更新订单项数量: count={}", itemsToUpdate.size());
        }
        if (!itemsToDelete.isEmpty()) {
            restaurantOrderItemService.removeByIds(itemsToDelete);
            log.info("删除订单项: count={}", itemsToDelete.size());
        }

        // 6.5 若订单已支付，退菜需加回库存并减少销量（与结算时减库存/加销量对应）
        if (order.getPaymentStatus() == PaymentStatusEnum.PAID && !refundDishQuantityMap.isEmpty()) {
            try {
                dishService.batchIncreaseStockAndDecreaseSales(refundDishQuantityMap);
                log.info("退菜已加回库存并减少销量: orderId={}, refundDishMap={}", order.getId(), refundDishQuantityMap);
            } catch (Exception e) {
                log.error("退菜加回库存/减销量失败: orderId={}", order.getId(), e);
                throw new BusinessException("退菜回滚库存/销量失败：" + e.getMessage());
            }
        }

        // 7. 更新订单总金额
        // 7. 按订单项重算订单总金额，保证与菜品项一致
        order = recalculateOrderAmountsFromItems(order.getId());
        BigDecimal newTotalPrice = order != null ? order.getActualAmount() : BigDecimal.ZERO;

        // 8. 如果订单金额为0或负数，取消订单
        if (newTotalPrice.compareTo(BigDecimal.ZERO) <= 0) {
            order.setStatus(OrderStatusEnum.CANCELLED);
            order.setCancelReason("退菜后订单金额为0，自动取消");
            order.setCancelTime(LocalDateTime.now());
            log.info("订单金额为0，自动取消订单: orderId={}", order.getId());
        }

        // 9. 如果订单已支付，更新支付状态为部分退款或全额退款
        if (order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            if (newTotalPrice.compareTo(BigDecimal.ZERO) <= 0) {
                order.setPaymentStatus(PaymentStatusEnum.FULL_REFUND);
                log.info("订单全额退款: orderId={}, refundAmount={}", order.getId(), totalRefundAmount);
            } else {
                order.setPaymentStatus(PaymentStatusEnum.PARTIAL_REFUND);
                log.info("订单部分退款: orderId={}, refundAmount={}, remainingAmount={}",
                        order.getId(), totalRefundAmount, newTotalPrice);
            }
        }

        // 10. 保存订单
        this.updateById(order);

        // 11. 记录退菜日志（可选：如果有退菜日志表）
        // saveRefundLog(order, request, totalRefundAmount);

        // 13. 构建返回结果
        RefundDishVO result = new RefundDishVO();
        result.setOrderId(order.getId());
        result.setOrderNo(order.getOrderNo());
        result.setTotalRefundAmount(totalRefundAmount);
        result.setRefundItems(refundDetails);
        result.setNewOrderTotalPrice(newTotalPrice);
        result.setRefundReason(request.getRefundReason());
        result.setRefundTime(LocalDateTime.now());

        log.info("退菜处理完成: orderId={}, totalRefundAmount={}, newTotalPrice={}",
                order.getId(), totalRefundAmount, newTotalPrice);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestaurantOrderVO markOrderItemsGift(MarkOrderItemsGiftRequest request) {
        RestaurantOrder order = this.getById(request.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() == OrderStatusEnum.COMPLETED || order.getStatus() == OrderStatusEnum.CANCELLED) {
            throw new BusinessException("当前订单状态不允许修改赠菜");
        }
        if (request.getSessionNo() != null && !request.getSessionNo().isEmpty()
                && !request.getSessionNo().equals(order.getSessionNo())) {
            throw new BusinessException("会话号与订单不匹配");
        }
        LambdaQueryWrapper<RestaurantOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RestaurantOrderItem::getOrderId, request.getOrderId());
        wrapper.in(RestaurantOrderItem::getId, request.getOrderItemIds());
        List<RestaurantOrderItem> items = restaurantOrderItemService.list(wrapper);
        if (items.isEmpty()) {
            throw new BusinessException("未找到对应的订单项");
        }
        BigDecimal deltaAmount = BigDecimal.ZERO; // 订单实付金额变化（赠菜时减少为正，取消赠菜时增加为负）
        for (RestaurantOrderItem item : items) {
            BigDecimal lineOriginalTotal = item.getPrice() != null
                    ? item.getPrice().multiply(new BigDecimal(item.getQuantity()))
                    : (item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO);
            if (request.getIsGift() == 1) {
                item.setIsGift(1);
                item.setDiscountPrice(lineOriginalTotal);
                item.setTotalPrice(BigDecimal.ZERO); // 该行实付=0
                deltaAmount = deltaAmount.add(lineOriginalTotal);
            } else {
                item.setIsGift(0);
                item.setDiscountPrice(BigDecimal.ZERO);
                item.setTotalPrice(lineOriginalTotal); // 恢复该行原价小计
                deltaAmount = deltaAmount.subtract(lineOriginalTotal);
            }
            item.setUpdatedAt(LocalDateTime.now());
        }
        restaurantOrderItemService.updateBatchById(items);
        order = recalculateOrderAmountsFromItems(request.getOrderId());
        log.info("标记赠菜: orderId={}, isGift={}, orderItemIds={}, deltaAmount={}",
                request.getOrderId(), request.getIsGift(), request.getOrderItemIds(), deltaAmount);
        return restaurantOrderConverter.toVO(order);
    }
    /**
     * 校验订单状态是否允许退菜
     *
     * @param order 订单信息
     */
    private void validateOrderStatusForRefund(RestaurantOrder order) {

        if (order.getStatus() == null) {
            throw new IllegalArgumentException("订单状态异常");
        }

        // 已完成的订单不允许退菜
        if (order.getStatus() == OrderStatusEnum.COMPLETED) {
            throw new IllegalStateException("订单已完成，不允许退菜");
        }

        // 已取消的订单不允许退菜
        if (order.getStatus() == OrderStatusEnum.CANCELLED) {
            throw new IllegalStateException("订单已取消，不允许退菜");
        }

        // 可以退菜的状态：待支付、已支付、制作中、已出餐
        if (order.getStatus() == OrderStatusEnum.COMPLETED ||
                order.getStatus() == OrderStatusEnum.CANCELLED ||
                order.getStatus() == OrderStatusEnum.NOPAID
        ) {
            throw new IllegalStateException("当前订单状态不允许退菜: " + order.getStatus().getDesc());
        }
    }

    /**
     * 获取菜品名称
     *
     * @param dishId 菜品ID
     * @return 菜品名称
     */
    private String getDishName(Long dishId) {
        if (dishId == null) {
            return "未知菜品";
        }

        try {
            Dish dish = dishService.getById(dishId);
            return dish != null ? dish.getDishName() : "菜品ID:" + dishId;
        } catch (Exception e) {
            log.warn("获取菜品名称失败: dishId={}", dishId, e);
            return "菜品ID:" + dishId;
        }
    }

    /**
     * 按订单项重算订单总金额并写回订单表。
     * - totalAmount = 原价总计（非赠品用 totalPrice，赠品用 discountPrice 表示该行原价，因 totalPrice=0）
     * - actualAmount = 应付 = 所有订单项 totalPrice 之和（每行实付，赠品行为 0）
     */
    private RestaurantOrder recalculateOrderAmountsFromItems(Long orderId) {
        RestaurantOrder order = this.getById(orderId);
        if (order == null) {
            return null;
        }
        LambdaQueryWrapper<RestaurantOrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RestaurantOrderItem::getOrderId, orderId);
        List<RestaurantOrderItem> allItems = restaurantOrderItemService.list(wrapper);
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal actualAmount = BigDecimal.ZERO;
        for (RestaurantOrderItem item : allItems) {
            BigDecimal tp = item.getTotalPrice() != null ? item.getTotalPrice()
                    : (item.getPrice() != null ? item.getPrice().multiply(new BigDecimal(item.getQuantity() != null ? item.getQuantity() : 0)) : BigDecimal.ZERO);
            BigDecimal dp = item.getDiscountPrice() != null ? item.getDiscountPrice() : BigDecimal.ZERO;
            totalAmount = totalAmount.add(tp.add(dp)); // 原价总计：非赠品 tp+0，赠品 0+dp
            actualAmount = actualAmount.add(tp);       // 应付：每行实付
        }
        if (actualAmount.compareTo(BigDecimal.ZERO) < 0) {
            actualAmount = BigDecimal.ZERO;
        }
        order.setTotalAmount(totalAmount);
        order.setActualAmount(actualAmount);
        order.setDiscountAmount(totalAmount.subtract(actualAmount).max(BigDecimal.ZERO));
        order.setUpdatedAt(LocalDateTime.now());
        this.updateById(order);
        log.debug("按订单项重算订单金额: orderId={}, totalAmount={}, actualAmount={}, discountAmount={}", orderId, totalAmount, actualAmount, order.getDiscountAmount());
        return order;
    }

}
