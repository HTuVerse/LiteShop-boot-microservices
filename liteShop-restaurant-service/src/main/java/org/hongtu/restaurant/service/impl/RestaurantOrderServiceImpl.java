package org.hongtu.restaurant.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.DTO.SysUser;
import org.hongtu.enums.BalanceResourceEnum;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DAO.*;
import org.hongtu.restaurant.DTO.*;
import org.hongtu.restaurant.VO.*;
import org.hongtu.restaurant.convert.RestaurantOrderConverter;
import org.hongtu.restaurant.enums.*;
import org.hongtu.restaurant.enums.PricingEnums.AdjustmentType;
import org.hongtu.restaurant.netty.NettyWebSocketPushService;
import org.hongtu.restaurant.service.*;
import org.hongtu.restaurant.utils.CashierAmountGuard;
import org.hongtu.restaurant.utils.ChannelPayment;
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
import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.hongtu.api.dto.PaymentCreateCommand;
import org.hongtu.api.dto.PaymentCreateResult;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.restaurant.payment.ScanPayOutcome;
import org.hongtu.restaurant.payment.PaymentTransitionPolicy;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.hongtu.tenant.TenantScope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

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

    /** 仅代客下单入口写入，普通下单必须忽略请求体里的 userId。 */
    private static final ThreadLocal<Long> PROXY_MEMBER_ID = new ThreadLocal<>();

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
    private org.hongtu.restaurant.payment.ScanPayCharger scanPayCharger;
    @Resource
    RestaurantOrderConverter restaurantOrderConverter;
    @Resource
    TableSessionService tableSessionService;
    @Resource
    SharedCartService sharedCartService;
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
    @Resource
    private PricingService pricingService;
    @Resource
    private OrderPriceAdjustmentMapper orderPriceAdjustmentMapper;
    @Resource
    private OrderDishChangeLogMapper orderDishChangeLogMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private LocalOutboxService localOutboxService;
    @Resource
    private OrderRefundMapper orderRefundMapper;
    @Resource
    private OrderRefundItemMapper orderRefundItemMapper;
    @Resource
    private PlatformTransactionManager transactionManager;
    private TransactionTemplate independentTx;
    @Resource private org.hongtu.restaurant.config.TenantRestaurantConfig tenantRestaurantConfig;

    /** 支付和退款使用独立事务，避免渠道调用还在进行时占着订单行锁。 */
    @PostConstruct
    public void initPaymentTransaction() {
        independentTx = new TransactionTemplate(transactionManager);
        independentTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 创建订单（用户端）
     * @param request 创建订单请求
     * @return 创建的订单（包含订单明细）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestaurantOrderVO createProxyOrder(CreateOrderRequest request, Long memberUserId, Long operatorId) {
        if (memberUserId == null || operatorId == null) {
            throw new BusinessException("代客下单必须指定会员和操作人");
        }
        PROXY_MEMBER_ID.set(memberUserId);
        try {
            RestaurantOrderVO created = createOrder(request);
            log.info("代客下单审计: operatorId={}, memberUserId={}, storeId={}, requestKey={}, orderNo={}",
                    operatorId, memberUserId, request.getStoreId(), request.getIdempotencyKey(), created.getOrderNo());
            return created;
        } finally {
            PROXY_MEMBER_ID.remove();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestaurantOrderVO createOrder(CreateOrderRequest request) {
        Long proxyMemberId = PROXY_MEMBER_ID.get();
        // 控制器已区分顾客与收银员；代客下单只替换明确选择的会员。
        if (proxyMemberId != null) request.setUserId(proxyMemberId);
        storeDataScopeService.checkStoreAccess(request.getStoreId());
        RestaurantOrder existingByKey = findByRequestKey(request.getIdempotencyKey());
        if (existingByKey != null) {
            validateIdempotentReplay(existingByKey, request);
            return restaurantOrderConverter.toVO(existingByKey);
        }
        // 同一会话只允许一笔未取消订单。行锁之后再用条件更新绑定 current_order_id。
        TableSession lockedSession = null;
        if (request.getSessionNo() != null && !request.getSessionNo().isEmpty()) {
            lockedSession = tableSessionService.getOne(new LambdaQueryWrapper<TableSession>()
                    .eq(TableSession::getSessionNo, request.getSessionNo())
                    .last("FOR UPDATE"));
            if (lockedSession == null || lockedSession.getStatus() != SessionStatusEnum.IN_PROGRESS) {
                throw new BusinessException("桌台会话不存在或已结束");
            }
            if (!java.util.Objects.equals(request.getStoreId(), lockedSession.getStoreId())
                    || !java.util.Objects.equals(request.getTableId(), lockedSession.getTableId())) {
                throw new BusinessException("订单门店或桌台与会话不匹配");
            }
            // 获得会话行锁后再次检查幂等键，串行化同一桌台的并发下单。
            existingByKey = findByRequestKey(request.getIdempotencyKey());
            if (existingByKey != null) {
                validateIdempotentReplay(existingByKey, request);
                return restaurantOrderConverter.toVO(existingByKey);
            }
            RestaurantOrder existOrder = findOpenOrderBySession(lockedSession);
            if (existOrder != null) {
                throw new BusinessException("该会话已存在订单（订单号：" + existOrder.getOrderNo() +
                        "），请使用追加菜品功能，避免创建重复订单");
            }
        }

        StoreInfo storeInfo = storeInfoMapper.selectById(request.getStoreId());
        if (storeInfo == null) {
            throw new BusinessException("门店不存在");
        }
        // 桌位线下支付由收银台按最终订单金额选券；顾客下单和代客下单都不能提前占用优惠券。
        if (lockedSession != null && storeInfo.getPaymentMode() == PaymentModeEnum.OFFLINE
                && request.getUserCouponIds() != null && !request.getUserCouponIds().isEmpty()) {
            throw new BusinessException("桌位线下支付的优惠券请在收银台结账时使用");
        }

        // 1. 仅校验菜品及规格；所有价格均由服务端统一试价覆盖。
        validateCreateOrderCartItems(request.getStoreId(), request.getCartItems());
        PricingQuoteVO pricingQuote = quoteCreateItems(request.getStoreId(), request.getUserId(), request.getCartItems(),
                proxyMemberId != null || request.getUserId() == null);

        // 2. 后端计算正常售价、会员/活动优惠及赠送金额。
        BigDecimal calculatedTotalAmount = pricingQuote.getListAmount();
        BigDecimal pricingPayableAmount = BigDecimal.ZERO;
        for (int i = 0; i < request.getCartItems().size(); i++) {
            CreateOrderRequest.CartItem cartItem = request.getCartItems().get(i);
            if (cartItem.getIsGift() != null && cartItem.getIsGift() == 1) {
                org.hongtu.security.OperationPermission.order("gift");
                continue;
            }
            pricingPayableAmount = pricingPayableAmount.add(pricingQuote.getLines().get(i).getLinePayableAmount());
        }
        BigDecimal pricingDiscountAmount = calculatedTotalAmount.subtract(pricingPayableAmount).max(BigDecimal.ZERO);
        log.info("统一定价完成：正常售价={}，活动/会员/赠送优惠={}，活动后应付={}",
                calculatedTotalAmount, pricingDiscountAmount, pricingPayableAmount);

        // 3.【优惠券处理】前端已调用 calcCouponDiscount 接口计算优惠金额
        // 后端直接调用 calcCouponDiscount 方法重新计算，该方法内部已包含所有校验逻辑
        BigDecimal couponDiscountAmount = BigDecimal.ZERO;
        List<Coupon> usedCoupons = new ArrayList<>();
        CouponDiscountResultVO calcData = null; // 用于后续将菜品免单券优惠分摊到订单项

        List<Long> userCouponIds = request.getUserCouponIds();
        if (userCouponIds != null && !userCouponIds.isEmpty()) {
            // 调用优惠券计算方法，内部已包含所有校验（有效性、叠加规则、互斥性等）
            Result calcResult = calcCouponDiscountWithLines(request.getUserId(), userCouponIds,
                    pricingPayableAmount, request.getStoreId(), couponLines(request.getCartItems(), pricingQuote));
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
        BigDecimal actualAmount = pricingPayableAmount.subtract(couponDiscountAmount);
        if (actualAmount.compareTo(BigDecimal.ZERO) < 0) {
            actualAmount = BigDecimal.ZERO;
        }

        // 5. 客户端金额仅用于一致性检查，订单落库金额始终取服务端重新计价结果。
        CashierAmountGuard.requireClientAmountMatches(request.getTotalPrice(), actualAmount);
        log.info("订单金额验证通过，原价：{}，优惠：{}，实付：{}", calculatedTotalAmount, couponDiscountAmount, actualAmount);

        // 6. 生成订单编号
        String orderNo = generateOrderNo();

        // 7. 创建订单主表
        RestaurantOrder order = new RestaurantOrder();
        // 自取统一线上支付；扫码堂食才采用门店配置的线上或线下支付模式。
        PaymentModeEnum orderPaymentMode = request.getOrderType() == SceneTypeEnum.DELIVERY
                ? PaymentModeEnum.ONLINE : storeInfo.getPaymentMode();
        if (orderPaymentMode == null) {
            throw new BusinessException("门店尚未配置支付模式");
        }
        order.setOrderNo(orderNo);
        order.setRequestKey(request.getIdempotencyKey());
        order.setStoreId(request.getStoreId());
        order.setTableId(request.getTableId());
        order.setUserId(request.getUserId());
        order.setOperatorId(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null);
        order.setOperatorName(resolveOperatorName(order.getOperatorId()));
        order.setSessionNo(request.getSessionNo());
        order.setOrderType(request.getOrderType());
        order.setTotalAmount(calculatedTotalAmount); // 原价总计
        order.setDiscountAmount(pricingDiscountAmount.add(couponDiscountAmount));
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
        order.setPaymentMode(orderPaymentMode);
        order.setNote(request.getNote());
        order.setServiceFee(BigDecimal.ZERO);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // 订单履约状态与支付状态相互独立；新订单始终从“已下单”开始。
        if (actualAmount.compareTo(BigDecimal.ZERO) == 0) {
            // 实付金额为0（优惠券全额抵扣）：订单直接标记为已支付
            order.setPaymentStatus(PaymentStatusEnum.PAID);
            order.setStatus(OrderStatusEnum.PLACED); // 已下单
            order.setPaymentType(PaymentTypeEnum.COUPON_PAY); // 优惠券全额抵扣
            order.setPaymentTime(LocalDateTime.now());
            log.info("订单实付金额为0，优惠券全额抵扣，自动标记为已支付: orderNo={}", order.getOrderNo());
            // 叫号自取模式：直接生成取餐码
            if (storeInfo.getPickupMode() == pickupModeEnum.NUMBERCALLING) {
                assignPickupCode(order);
            }
        } else if (orderPaymentMode == PaymentModeEnum.OFFLINE) {
            // 堂食线下结算直接下单，收银台结算后才记录支付时间。
            order.setPaymentStatus(PaymentStatusEnum.UNPAID);
            order.setStatus(OrderStatusEnum.PLACED); // 已下单
            order.setPaymentType(PaymentTypeEnum.OFFLINE_PAY); // 线下支付

            // 线下支付的叫号自取订单在下单时生成取餐码。
            if (storeInfo.getPickupMode() == pickupModeEnum.NUMBERCALLING) {
                assignPickupCode(order);
            }
        } else {
            // 线上支付订单等待顾客完成支付，履约状态先记录为已下单。
            order.setPaymentStatus(PaymentStatusEnum.UNPAID);
            order.setStatus(OrderStatusEnum.PLACED);
            // 线上支付的取餐码在支付成功后生成，此处不生成
        }

        // 保存订单主表。取餐码唯一约束冲突时换新码重试，不随机拼码。
        insertOrder(order);

        // 8. 创建订单明细（首次下单，批次号为1）
        List<RestaurantOrderItem> orderItems = new ArrayList<>();
        if (request.getCartItems() != null && !request.getCartItems().isEmpty()) {
            for (int i = 0; i < request.getCartItems().size(); i++) {
                CreateOrderRequest.CartItem cartItem = request.getCartItems().get(i);
                PricingQuoteVO.Line pricedLine = pricingQuote.getLines().get(i);
                RestaurantOrderItem orderItem = new RestaurantOrderItem();
                orderItem.setOrderId(order.getId());
                orderItem.setDishId(cartItem.getDishId());
                orderItem.setCategoryId(cartItem.getCategoryId());
                orderItem.setUnitListPrice(pricedLine.getUnitListPrice());
                orderItem.setUnitSellingPrice(pricedLine.getUnitSellingPrice());
                orderItem.setQuantity(cartItem.getQuantity());
                applyCostSnapshot(orderItem, pricedLine);

                // 设置赠送标识
                orderItem.setIsGift(cartItem.getIsGift() != null ? cartItem.getIsGift() : 0);

                // 计算原价小计
                BigDecimal itemOriginalTotal = pricedLine.getLineListAmount();

                // 如果是赠送菜品：单价不变，小计保留，优惠金额=小计
                if (orderItem.getIsGift() == 1) {
                    orderItem.setDiscountAmount(itemOriginalTotal); // 优惠金额=该行正常售价
                    orderItem.setLinePayableAmount(BigDecimal.ZERO);
                } else {
                    orderItem.setLinePayableAmount(pricedLine.getLinePayableAmount());
                    orderItem.setDiscountAmount(pricedLine.getLineDiscountAmount());
                }

                // 保存规格信息（作为文本存储）
                orderItem.setSpecOptionsText(cartItem.getSpecText());
                orderItem.setCreatedAt(LocalDateTime.now());
                orderItem.setUpdatedAt(LocalDateTime.now());
                // 设置批次号和加菜时间
                orderItem.setBatchNo(1); // 首次下单，批次号为1
                orderItem.setBatchOrderNote(request.getNote());
                orderItem.setSubmittedByUserId(order.getOperatorId());
                orderItem.setSubmittedByName(order.getOperatorName());
                orderItem.setAddTime(LocalDateTime.now());
                orderItems.add(orderItem);
            }

            // 8.1 【创建订单-菜品优惠券】将菜品免单券等优惠分摊到订单项（设置每项的 discountPrice / totalPrice）
            if (calcData != null && calcData.getCouponDetails() != null && !orderItems.isEmpty()) {
                Map<String, BigDecimal> freeAmountByLine = new HashMap<>();
                for (CouponDiscountResultVO.CouponDetailVO detail : calcData.getCouponDetails()) {
                    if (detail.getCouponType() != null && (detail.getCouponType().needDishItems()
                            || detail.getCouponType() == CouponTypeEnum.CASH_VOUCHER) && detail.getDishItems() != null) {
                        for (CouponDiscountResultVO.DishItemVO di : detail.getDishItems()) {
                            if (di.getLineKey() != null && di.getCurrentPrice() != null) {
                                freeAmountByLine.merge(di.getLineKey(), di.getCurrentPrice(), BigDecimal::add);
                            }
                        }
                    }
                }
                for (int i = 0; i < orderItems.size(); i++) {
                    RestaurantOrderItem item = orderItems.get(i);
                    if (item.getIsGift() != null && item.getIsGift() == 1) {
                        continue;
                    }
                    String lineKey = String.valueOf(i);
                    BigDecimal remaining = freeAmountByLine.get(lineKey);
                    if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal lineTotal = item.getLinePayableAmount() != null ? item.getLinePayableAmount() : BigDecimal.ZERO;
                    BigDecimal deduct = lineTotal.min(remaining);
                    item.setDiscountAmount((item.getDiscountAmount() == null ? BigDecimal.ZERO
                            : item.getDiscountAmount()).add(deduct));
                    item.setLinePayableAmount(lineTotal.subtract(deduct));
                    freeAmountByLine.put(lineKey, remaining.subtract(deduct));
                }
                log.info("创建订单-优惠券已分摊到订单项: orderId={}, 优惠金额={}", order.getId(), couponDiscountAmount);
            }

            // 批量保存订单明细
            restaurantOrderItemService.saveBatch(orderItems);
            persistPricingAdjustments(order.getId(), orderItems, pricingQuote);
        }
        // 下单事务内原子扣减库存，支付回调只确认资金，不再承担首次库存校验。
        reserveOrderStock(order);
        RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(order);

        if (lockedSession != null) {
            bindSessionOrder(lockedSession, order.getId());
        }

        // 事务提交后再清空共享购物车并通知同桌，避免推送时其他设备仍读到旧数据。
        if (lockedSession != null) {
            try {
                tableSessionService.updateSessionStatistics(lockedSession.getSessionNo());
            } catch (Exception e) {
                log.error("更新桌台统计失败: orderId={}, error={}", order.getId(), e.getMessage(), e);
            }
            publishTableOrderAfterCommit(lockedSession.getSessionNo(), orderVO, true);
        }
        // 零元订单走与支付成功相同的副作用，避免只改状态却不结束会话、不推送已支付。
        if (actualAmount.compareTo(BigDecimal.ZERO) == 0) {
            applyPaidSideEffects(order);
        } else if (storeInfo.getIsPrint() == BooleanEnum.YES
                && orderPaymentMode == PaymentModeEnum.OFFLINE) {
            printOrderWithStorePrinters(order.getId(), storeInfo.getId());
            log.info("线下支付订单创建成功，已打印小票: orderNo={}", order.getOrderNo());
        } else {
            log.info("订单创建成功，等待支付完成后打印小票: orderNo={}", order.getOrderNo());
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
        } else if (orderPaymentMode == PaymentModeEnum.ONLINE) {
            // 线上支付：所有订单都需要加入超时队列
            needTimeoutQueue = true;
        } else if (orderPaymentMode == PaymentModeEnum.OFFLINE && StrUtil.isBlank(request.getSessionNo())) {
            // 线下支付 + 无会话（自助点餐）：需要加入超时队列
            needTimeoutQueue = true;
        }
        // 线下支付 + 有会话：不需要加入超时队列（已在上面设置为已下单状态）

        if (needTimeoutQueue) {
            try {
                orderTimeoutQueueService.addToTimeoutQueue(order.getId());
                log.info("订单已加入超时队列: orderId={}, orderNo={}, paymentMode={}, hasSession={}",
                        order.getId(), order.getOrderNo(), orderPaymentMode,
                        !StrUtil.isBlank(request.getSessionNo()));
            } catch (Exception e) {
                log.error("订单加入超时队列失败: orderId={}, error={}", order.getId(), e.getMessage());
                throw new BusinessException("订单超时任务登记失败，请稍后重试");
            }
        } else {
            log.info("订单无需加入超时队列: orderId={}, orderNo={}, paymentMode={}, hasSession={}",
                    order.getId(), order.getOrderNo(), orderPaymentMode,
                    !StrUtil.isBlank(request.getSessionNo()));
        }

        // 所有本地关键写入和超时登记完成后再远程核销；多券中途失败会补偿已核销部分。
        verifyCouponsWithCompensation(userCouponIds, order.getId());

        // 11. 使用转换器构建返回对象
        return orderVO;
    }

    @Override
    public RestaurantOrderVO getByRequestKey(String requestKey) {
        RestaurantOrder order = findByRequestKey(requestKey);
        return order == null ? null : restaurantOrderConverter.toVO(order);
    }

    private RestaurantOrder findByRequestKey(String requestKey) {
        if (StrUtil.isBlank(requestKey)) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<RestaurantOrder>()
                .eq(RestaurantOrder::getRequestKey, requestKey)
                .last("LIMIT 1"));
    }

    private void validateIdempotentReplay(RestaurantOrder existing, CreateOrderRequest request) {
        if (!java.util.Objects.equals(existing.getStoreId(), request.getStoreId())
                || !java.util.Objects.equals(existing.getUserId(), request.getUserId())
                || !java.util.Objects.equals(existing.getSessionNo(), request.getSessionNo())) {
            throw new BusinessException("下单幂等键已被其他业务请求使用");
        }
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
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOrderStatus(Long orderId, OrderStatusEnum status) {
        RestaurantOrder order = lockOrderForMutation(orderId);
        if (order == null) {
            throw new BusinessException(ORDER_NOT_EXIST.getCode(),"订单不存在");
        }
        if (status == null) {
            throw new BusinessException("订单状态不能为空");
        }
        OrderStatusEnum currentStatus = order.getStatus();
        if (currentStatus == null) {
            throw new BusinessException("订单当前状态异常");
        }
        if (currentStatus == status) {
            return true;
        }
        if (status == OrderStatusEnum.CANCELLED) {
            throw new BusinessException("取消订单请使用取消接口并填写取消原因");
        }
        if (!currentStatus.canTransitionTo(status)) {
            throw new BusinessException("订单状态不能从“" + currentStatus.getDesc()
                    + "”直接变更为“" + status.getDesc() + "”");
        }
        if (order.getPaymentMode() == PaymentModeEnum.ONLINE
                && order.getPaymentStatus() != PaymentStatusEnum.PAID) {
            throw new BusinessException("线上订单支付成功后才能进入接单流程");
        }
        order.setStatus(status);
        order.setUpdatedAt(LocalDateTime.now());

        // 如果订单完成，设置结束用餐时间
        if (status == OrderStatusEnum.COMPLETED) {
            order.setDiningEndTime(LocalDateTime.now());
        }

        boolean updated = this.updateById(order);
        if (updated && status == OrderStatusEnum.COMPLETED && order.getUserId() != null)
            localOutboxService.enqueueMemberReward(order.getId(), "MEMBER_REWARD:" + order.getId() + ":COMPLETE");
        return updated;
    }

    /** 仅取消尚未付款生效的线上订单；关单不明确时进入对账。 */
    @Override
    public boolean cancelOrder(Long orderId, String cancelReason) {
        return closeUnpaidOrder(orderId, cancelReason, false);
    }

    /**
     * 创建支付订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentResponse createPayment(Long orderId, Integer paymentType, String paymentScene,
                                         String openid, String authCode, String returnUrl, String payerClientIp) {

        // 1. 锁定订单，避免同一订单并发创建多个支付或与线下结算竞争。
        RestaurantOrder order = lockOrderForMutation(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在");
        }
        if (order.getPaymentMode() != PaymentModeEnum.ONLINE) {
            throw new BusinessException("该订单采用线下支付，请到收银台结算");
        }

        // 2. 验证订单状态
        if (order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            throw new IllegalStateException("订单已支付，请勿重复支付");
        }
        if (order.getStatus() == OrderStatusEnum.CANCELLED) {
            throw new IllegalStateException("订单已取消，无法支付");
        }
        if (isOnlinePaymentExpired(order)) {
            throw new BusinessException("订单支付已超时，正在自动取消，请刷新订单状态");
        }

        BigDecimal serverPayable = CashierAmountGuard.requireServerPayable(
                order.getActualAmount(), order.getTotalAmount());

        // 3. 构建响应，支付金额只取服务端优惠后应付金额。
        PaymentResponse.PaymentResponseBuilder responseBuilder = PaymentResponse.builder()
                .orderId(orderId)
                .orderNo(order.getOrderNo())
                .payAmount(serverPayable)
                .paymentType(paymentType)
                .paymentScene(paymentScene)
                .expireSeconds(1800);

        // 5. 根据支付方式调用对应的支付服务
        PaymentTypeEnum paymentTypeEnum = PaymentTypeEnum.getByCode(paymentType);
        if (paymentTypeEnum == null) {
            throw new IllegalStateException("不支持的支付方式");
        }
        if ((order.getPaymentType() == PaymentTypeEnum.WECHAT
                || order.getPaymentType() == PaymentTypeEnum.ALIPAY)
                && order.getPaymentType() != paymentTypeEnum) {
            throw new IllegalStateException("该订单已创建其它支付渠道的交易，请先取消订单");
        }

        if (paymentTypeEnum != PaymentTypeEnum.WECHAT && paymentTypeEnum != PaymentTypeEnum.ALIPAY) {
            throw new IllegalStateException("不支持的支付方式");
        }

        // 订单服务只提交可信金额和场景；渠道参数、网关与通知地址都由支付服务处理。
        PaymentCreateCommand command = new PaymentCreateCommand();
        command.setTenantId(order.getTenantId());
        command.setOrderNo(order.getOrderNo());
        command.setChannel(paymentTypeEnum == PaymentTypeEnum.WECHAT ? "WECHAT" : "ALIPAY");
        command.setScene(paymentScene);
        command.setAmount(serverPayable);
        command.setSubject(paymentTypeEnum == PaymentTypeEnum.WECHAT
                ? "餐饮订单" : "餐厅订单-" + order.getOrderNo());
        command.setAttach(paymentAttach(order));
        command.setOpenid(openid);
        command.setAuthCode(authCode);
        command.setReturnUrl(returnUrl);
        command.setPayerClientIp(payerClientIp);
        PaymentCreateResult created = FeignResultUtil.unwrap(payFeignClient.createPayment(command));
        responseBuilder.payUrl(created.getPayUrl())
                .payForm(created.getPayForm())
                .qrCode(created.getQrCode())
                .tradeNo(created.getTradeNo());
        if (created.getMiniPayParams() != null) {
            Map<String, Object> payParams = created.getMiniPayParams();
            responseBuilder.miniPayParams(PaymentResponse.MiniPayParams.builder()
                    .appId((String) payParams.get("appId"))
                    .timeStamp((String) payParams.get("timeStamp"))
                    .nonceStr((String) payParams.get("nonceStr"))
                    .packageValue((String) payParams.get("package"))
                    .signType((String) payParams.get("signType"))
                    .paySign((String) payParams.get("paySign"))
                    .build());
        }

        // 记录已创建的支付渠道订单，取消/超时任务才能正确关单。
        order.setPaymentType(paymentTypeEnum);
        order.setUpdatedAt(LocalDateTime.now());
        this.updateById(order);

        return responseBuilder.build();
    }

    /**
     * 主动查询支付结果并更新订单状态。不在外层持有行锁，避免和入账事务互相等待。
     */
    @Override
    public boolean queryAndUpdatePaymentStatus(Long orderId) {
        RestaurantOrder order = this.getById(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在");
        }
        if (order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            return true;
        }
        if (order.getPaymentType() == null || order.getOrderNo() == null) {
            return false;
        }
        ChannelPayment channel = order.getPaymentType() == PaymentTypeEnum.WECHAT
                ? queryWeChatPaymentStatus(order.getTenantId(), order.getOrderNo())
                : order.getPaymentType() == PaymentTypeEnum.ALIPAY
                ? queryAlipayPaymentStatus(order.getTenantId(), order.getOrderNo())
                : null;
        if (channel == null || !channel.paid()) {
            return false;
        }
        if (!channel.accepted(order.getOrderNo())) {
            log.warn("主动查单未通过金额、币种或商户校验: orderNo={}", order.getOrderNo());
            return false;
        }
        return handlePaymentSuccess(order.getOrderNo(), channel.transactionId(), order.getPaymentType(), channel.amount(), null);
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

        // 只计算是否超时，取消由调度任务或明确的超时命令执行。
        Long waitingSeconds = null;
        Boolean isExpired = false;
        if (order.getCreatedAt() != null) {
            waitingSeconds = java.time.Duration.between(order.getCreatedAt(), LocalDateTime.now()).getSeconds();
            isExpired = waitingSeconds >= OrderTimeoutQueueService.DEFAULT_TIMEOUT_SECONDS;
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
                .canPay(order.getPaymentMode() == PaymentModeEnum.ONLINE
                        && order.getPaymentStatus() == PaymentStatusEnum.UNPAID &&
                        order.getStatus() != OrderStatusEnum.CANCELLED &&
                        order.getStatus() != OrderStatusEnum.COMPLETED && !isExpired)
                .canCancel(PaymentTransitionPolicy.canCancel(order.getStatus(), order.getPaymentStatus(),
                        order.getPaymentMode()))

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
        // 1. 锁定订单，余额扣款与其它结算方式必须互斥。
        RestaurantOrder order = lockOrderForMutation(orderId);
        if (order == null) {
            throw new IllegalStateException("订单不存在");
        }
        if (order.getPaymentMode() != PaymentModeEnum.ONLINE) {
            throw new BusinessException("该订单采用线下支付，请到收银台结算");
        }

        // 2. 验证订单状态
        if (order.getPaymentStatus() != null && order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            throw new IllegalStateException("订单已支付，请勿重复支付");
        }
        if (order.getStatus() == OrderStatusEnum.CANCELLED) {
            throw new IllegalStateException("订单已取消，无法支付");
        }
        if (isOnlinePaymentExpired(order)) {
            throw new BusinessException("订单支付已超时，正在自动取消，请刷新订单状态");
        }

        if (order.getPaymentType() == PaymentTypeEnum.WECHAT
                || order.getPaymentType() == PaymentTypeEnum.ALIPAY) {
            throw new IllegalStateException("该订单已创建其它支付渠道的交易，请先取消订单");
        }

        // 3. 获取用户信息（Feign 调用 user-service）
        Long userId = StpUtil.getLoginIdAsLong();
        SysUser user = FeignResultUtil.unwrap(userFeignClient.getUserById(userId));
        if (user == null) {
            throw new IllegalStateException("用户不存在");
        }

        // 4. 检查余额是否足够（user-service 扣款时会再次校验，这里提前给出友好提示）
        BigDecimal payAmount = CashierAmountGuard.requireServerPayable(
                order.getActualAmount(), order.getTotalAmount());
        if (user.getSurplusMoney() == null || user.getSurplusMoney().compareTo(payAmount) < 0) {
            throw new IllegalStateException("余额不足，请充值后再试");
        }

        // 5. 扣除余额 + 记录流水（user-service 本地事务内原子完成，替代原跨模块裸操作）
        changeBalanceDurable(order.getId(), BalanceChangeDTO.builder()
                .userId(userId)
                .amount(payAmount)
                .type(4)
                .orderNo(order.getOrderNo())
                .operateUserId(userId)
                .build());

        // 6. 调用统一的支付成功处理逻辑
        // 生成一个余额支付的流水号（使用订单号作为交易号）
        String transactionId = "BALANCE_" + order.getOrderNo();
        boolean success;
        try {
            // 当前事务已持有订单行锁，不能再走会开启独立事务的回调入账入口。
            CaptureOutcome outcome = captureInTransaction(
                    TransactionAspectSupport.currentTransactionStatus(), orderId,
                    transactionId, PaymentTypeEnum.BANLANCE_PAY, payAmount);
            success = outcome != null && outcome.paidOrder != null;
            if (success) {
                RestaurantOrder paidOrder = outcome.paidOrder;
                Long tenantId = TenantContext.getTenantId();
                // 订单入账提交后再结束桌台会话、打印和推送，避免客户端先收到未提交的状态。
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        TenantScope.run(tenantId, () -> {
                            try {
                                independentTx.execute(status -> {
                                    applyPaidSideEffects(paidOrder);
                                    return null;
                                });
                            } catch (RuntimeException exception) {
                                log.error("余额支付副作用失败，订单已入账: orderId={}", orderId, exception);
                            }
                        });
                    }
                });
            }
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
            changeBalanceDurable(null, BalanceChangeDTO.builder()
                    .userId(userId)
                    .amount(amount)
                    .type(3)
                    .orderNo(orderNo + "_COMPENSATE")
                    .sourceOrderNo(orderNo)
                    .operateUserId(userId)
                    .build());
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
     * 处理支付超时。已支付、退款中和对账中的订单不会被覆盖。
     */
    @Override
    public boolean handlePaymentTimeout(Long orderId, String reason) {
        return closeUnpaidOrder(orderId, reason != null ? reason : "支付超时", true);
    }

    /** 线上订单到达支付期限后，拒绝新建支付交易和余额扣款。 */
    private boolean isOnlinePaymentExpired(RestaurantOrder order) {
        return order.getPaymentMode() == PaymentModeEnum.ONLINE
                && order.getPaymentStatus() == PaymentStatusEnum.UNPAID
                && order.getCreatedAt() != null
                && !order.getCreatedAt().plusSeconds(OrderTimeoutQueueService.DEFAULT_TIMEOUT_SECONDS)
                        .isAfter(LocalDateTime.now());
    }

    /**
     * 仅把仍未支付的订单标为失败，不能覆盖已收款状态。
     */
    @Override
    public boolean handlePaymentError(Long orderId, String reason) {
        return Boolean.TRUE.equals(independentTx.execute(status -> this.update(new LambdaUpdateWrapper<RestaurantOrder>()
                .eq(RestaurantOrder::getId, orderId)
                .eq(RestaurantOrder::getPaymentStatus, PaymentStatusEnum.UNPAID)
                .ne(RestaurantOrder::getStatus, OrderStatusEnum.CANCELLED)
                .set(RestaurantOrder::getPaymentStatus, PaymentStatusEnum.FAILED)
                .set(RestaurantOrder::getCancelReason, reason != null ? reason : "支付失败")
                .set(RestaurantOrder::getUpdatedAt, LocalDateTime.now()))));
    }

    @Override
    public boolean handlePaymentSuccess(String outTradeNo, String transactionId, PaymentTypeEnum paymentType) {
        return handlePaymentSuccess(outTradeNo, transactionId, paymentType, null, null);
    }

    /**
     * 验签后的支付入账。先按订单号窄查询租户，再用 TenantScope 处理后续写入。
     */
    private boolean handlePaymentSuccess(String outTradeNo, String transactionId,
                                         PaymentTypeEnum paymentType, BigDecimal notifiedAmount, Long expectedTenantId) {
        OrderTenantRef located = TenantIgnore.get(() -> baseMapper.findTenantByOrderNo(outTradeNo));
        if (located == null || located.getTenantId() == null) {
            return false;
        }
        if (expectedTenantId != null && !expectedTenantId.equals(located.getTenantId())) {
            throw new BusinessException("支付回调订单归属校验失败");
        }
        return TenantScope.call(located.getTenantId(),
                () -> capturePayment(located.getId(), transactionId, paymentType, notifiedAmount));
    }

    /**
     * 支付完成后的库存、取餐码、打印、推送、会话结束和超时队列清理。已预占库存不会重复扣减。
     */
    private void applyPaidSideEffects(RestaurantOrder order) {
        StoreInfo storeInfo = storeInfoMapper.selectById(order.getStoreId());
        if (storeInfo != null
                && storeInfo.getPickupMode() == pickupModeEnum.NUMBERCALLING
                && StrUtil.isBlank(order.getPickupCode())) {
            assignPickupCode(order);
            order.setUpdatedAt(LocalDateTime.now());
            updateOrder(order);
        }
        if (order.getTableId() != null && order.getSessionNo() != null) {
            try {
                var tableSession = tableSessionService.getActiveSessionByTableId(order.getTableId());
                if (tableSession != null) {
                    webSocketPushService.pushOrderPaid(tableSession.getSessionNo(), restaurantOrderConverter.toVO(order));
                    tableSessionService.updateSessionStatistics(tableSession.getSessionNo());
                }
            } catch (Exception e) {
                log.error("WebSocket 推送支付成功通知失败: orderId={}, error={}", order.getId(), e.getMessage(), e);
            }
        }
        if (storeInfo != null && storeInfo.getIsPrint() == BooleanEnum.YES) {
            printOrderWithStorePrinters(order.getId(), storeInfo.getId());
        }
        if (StrUtil.isNotBlank(order.getSessionNo()) && order.getPaymentMode() == PaymentModeEnum.ONLINE) {
            BigDecimal paidAmount = order.getActualAmount() != null ? order.getActualAmount() : BigDecimal.ZERO;
            tableSessionService.endSession(order.getSessionNo(), paidAmount);
            pushSessionEndedAfterCommit(order.getSessionNo(), paidAmount);
            log.info("线上支付成功，已结束会话: sessionNo={}, orderId={}", order.getSessionNo(), order.getId());
        }
        try {
            orderTimeoutQueueService.removeFromTimeoutQueue(order.getId());
        } catch (Exception e) {
            log.error("从超时队列移除订单失败: orderId={}, error={}", order.getId(), e.getMessage());
        }
    }

    /**
     * 查询微信支付状态。金额、币种、商户单号和商户号必须齐全才允许改本地订单。
     */
    private ChannelPayment queryWeChatPaymentStatus(Long orderTenantId, String outTradeNo) {
        try {
            Map<String, Object> result = FeignResultUtil.unwrap(
                    payFeignClient.queryWeChatTrade(orderTenantId, outTradeNo));
            if (result == null || !Boolean.TRUE.equals(result.get("success"))) {
                return ChannelPayment.unpaid();
            }
            boolean paid = "SUCCESS".equals(String.valueOf(result.get("tradeState")));
            Object total = result.get("total");
            BigDecimal amount = total == null ? null : BigDecimal.valueOf(((Number) total).longValue(), 2);
            return new ChannelPayment(paid, stringValue(result.get("transactionId")), amount,
                    stringValue(result.get("currency")), stringValue(result.get("outTradeNo")),
                    Boolean.TRUE.equals(result.get("merchantMatched")));
        } catch (Exception e) {
            log.error("查询微信支付状态失败", e);
            return ChannelPayment.unpaid();
        }
    }

    /**
     * 生成订单编号
     * 格式：RO + 年月日时分秒 + 12位UUID片段。
     * UUID片段避免多实例同秒高并发下4位随机数碰撞。
     */
    private String generateOrderNo() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String timeStr = LocalDateTime.now().format(formatter);
        String randomPart = UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12)
                .toUpperCase();
        return "RO" + timeStr + randomPart;
    }

    /**
     * 查询支付宝支付状态
     */
    private ChannelPayment queryAlipayPaymentStatus(Long orderTenantId, String outTradeNo) {
        try {
            Map<String, Object> result = FeignResultUtil.unwrap(
                    payFeignClient.queryAlipayTrade(orderTenantId, outTradeNo));
            if (result == null || !Boolean.TRUE.equals(result.get("success"))) {
                return ChannelPayment.unpaid();
            }
            String tradeStatus = stringValue(result.get("tradeStatus"));
            boolean paid = "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
            BigDecimal amount = result.get("totalAmount") == null ? null : new BigDecimal(result.get("totalAmount").toString());
            return new ChannelPayment(paid, stringValue(result.get("tradeNo")), amount,
                    stringValue(result.get("currency")), stringValue(result.get("outTradeNo")),
                    Boolean.TRUE.equals(result.get("merchantMatched")));
        } catch (Exception e) {
            log.error("查询支付宝支付状态失败", e);
            return ChannelPayment.unpaid();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    /** 只处理支付服务已验签的成功事件，订单归属和金额仍以餐厅数据库为准。 */
    @Override
    public void applyVerifiedPaymentNotify(Long tenantId, PaymentNotifyResult notice) {
        if (tenantId == null || notice == null || !Boolean.TRUE.equals(notice.getIsSuccess())
                || notice.getTotalAmount() == null || notice.getTotalAmount() <= 0
                || !"CNY".equalsIgnoreCase(notice.getCurrency())
                || StrUtil.isBlank(notice.getOutTradeNo()) || StrUtil.isBlank(notice.getTransactionId())
                || StrUtil.isBlank(notice.getPaymentType())) {
            throw new BusinessException("支付回调数据不完整");
        }
        PaymentTypeEnum type = switch (notice.getPaymentType()) {
            case "WECHAT" -> PaymentTypeEnum.WECHAT;
            case "ALIPAY" -> PaymentTypeEnum.ALIPAY;
            default -> throw new BusinessException("支付回调渠道不支持");
        };
        OrderTenantRef located = TenantIgnore.get(() -> baseMapper.findTenantByOrderNo(notice.getOutTradeNo()));
        if (located == null || !tenantId.equals(located.getTenantId())) {
            throw new BusinessException("支付回调订单归属校验失败");
        }
        TenantScope.run(tenantId, () -> {
            RestaurantOrder callbackOrder = this.getById(located.getId());
            if (callbackOrder == null || (StrUtil.isNotBlank(notice.getAttach())
                    && !paymentAttach(callbackOrder).equals(notice.getAttach()))) {
                throw new BusinessException("支付回调订单附加信息不匹配");
            }
        });
        if (!handlePaymentSuccess(notice.getOutTradeNo(), notice.getTransactionId(), type,
                BigDecimal.valueOf(notice.getTotalAmount(), 2), tenantId)) {
            throw new BusinessException("支付回调业务处理失败");
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
                .ne(RestaurantOrder::getStatus, OrderStatusEnum.CANCELLED)
                .orderByDesc(RestaurantOrder::getCreatedAt)
                .last("LIMIT 1");

        return this.getOne(wrapper);
    }

    private RestaurantOrder findOpenOrderBySession(TableSession session) {
        if (session.getCurrentOrderId() != null) {
            RestaurantOrder bound = this.getById(session.getCurrentOrderId());
            if (bound != null && bound.getStatus() != OrderStatusEnum.CANCELLED) {
                return bound;
            }
        }
        return getOrderBySessionNo(session.getSessionNo());
    }

    /**
     * 在已锁定的会话行上原子绑定当前订单。期望值不一致说明并发请求已经占用该会话。
     */
    private void bindSessionOrder(TableSession lockedSession, Long orderId) {
        LambdaUpdateWrapper<TableSession> bind = new LambdaUpdateWrapper<TableSession>()
                .eq(TableSession::getId, lockedSession.getId())
                .set(TableSession::getCurrentOrderId, orderId)
                .set(TableSession::getUpdatedAt, LocalDateTime.now());
        if (lockedSession.getCurrentOrderId() == null) {
            bind.isNull(TableSession::getCurrentOrderId);
        } else {
            bind.eq(TableSession::getCurrentOrderId, lockedSession.getCurrentOrderId());
        }
        if (!tableSessionService.update(bind)) {
            throw new BusinessException("该会话已绑定其他订单，请使用追加菜品功能");
        }
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
        RestaurantOrder order = lockOrderForMutation(request.getOrderId());
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
        validateCreateOrderCartItems(order.getStoreId(), request.getCartItems());
        PricingQuoteVO pricingQuote = quoteCreateItems(order.getStoreId(), order.getUserId(), request.getCartItems(),
                order.getUserId() == null || !java.util.Objects.equals(order.getUserId(), order.getOperatorId()));

        // 5. 计算新批次号
        Integer newBatchNo = restaurantOrderItemService.getMaxBatchNo(request.getOrderId()) + 1;
        log.info("追加菜品到订单: orderId={}, 新批次号={}", request.getOrderId(), newBatchNo);

        // 6. 创建新的订单项
        List<RestaurantOrderItem> newItems = new ArrayList<>();
        BigDecimal addAmount = BigDecimal.ZERO; // 追加的总金额（不含赠送菜品）

        if (request.getCartItems() != null && !request.getCartItems().isEmpty()) {
            LocalDateTime addTime = LocalDateTime.now();

            for (int i = 0; i < request.getCartItems().size(); i++) {
                CreateOrderRequest.CartItem cartItem = request.getCartItems().get(i);
                PricingQuoteVO.Line pricedLine = pricingQuote.getLines().get(i);
                RestaurantOrderItem orderItem = new RestaurantOrderItem();
                orderItem.setOrderId(request.getOrderId());
                orderItem.setDishId(cartItem.getDishId());
                orderItem.setCategoryId(cartItem.getCategoryId());

                orderItem.setUnitListPrice(pricedLine.getUnitListPrice());
                orderItem.setUnitSellingPrice(pricedLine.getUnitSellingPrice());
                orderItem.setQuantity(cartItem.getQuantity());
                applyCostSnapshot(orderItem, pricedLine);

                // 设置赠送标识
                orderItem.setIsGift(cartItem.getIsGift() != null ? cartItem.getIsGift() : 0);

                // 计算原价小计
                BigDecimal itemOriginalTotal = pricedLine.getLineListAmount();

                // 赠菜：优惠金额=该行原价，小计 totalPrice=原价-优惠=0（该行实付）；非赠菜：小计=原价
                if (orderItem.getIsGift() == 1) {
                    orderItem.setDiscountAmount(itemOriginalTotal);
                    orderItem.setLinePayableAmount(BigDecimal.ZERO);
                } else {
                    orderItem.setLinePayableAmount(pricedLine.getLinePayableAmount());
                    orderItem.setDiscountAmount(pricedLine.getLineDiscountAmount());
                    addAmount = addAmount.add(pricedLine.getLinePayableAmount());
                }

                // 保存规格信息
                orderItem.setSpecOptionsText(cartItem.getSpecText());
                orderItem.setCreatedAt(LocalDateTime.now());
                orderItem.setUpdatedAt(LocalDateTime.now());

                // 【核心】设置批次号和加菜时间
                orderItem.setBatchNo(newBatchNo);
                orderItem.setBatchOrderNote(request.getNote());
                Long submitterId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
                orderItem.setSubmittedByUserId(submitterId);
                orderItem.setSubmittedByName(resolveOperatorName(submitterId));
                orderItem.setAddTime(addTime);

                newItems.add(orderItem);
            }

            // 批量保存新订单项
            restaurantOrderItemService.saveBatch(newItems);
            persistPricingAdjustments(order.getId(), newItems, pricingQuote);
        }

        // 6.5 加菜仅支持菜品券，优惠分摊到本批订单项。
        List<Long> addItemUserCouponIds = request.getUserCouponIds();
        List<Coupon> addItemUsedCoupons = new ArrayList<>();
        if (addItemUserCouponIds != null && !addItemUserCouponIds.isEmpty() && !newItems.isEmpty()) {
            Long userId = order.getUserId();
            if (userId == null) {
                throw new BusinessException("加菜使用优惠券需登录");
            }
            // 加菜只允许菜品免单和菜品折扣券。
            List<UserCoupon> addItemUserCoupons = FeignResultUtil.unwrap(couponFeignClient.getUserCouponsByIds(addItemUserCouponIds));
            List<Long> addItemCouponIds = addItemUserCoupons.stream()
                    .map(UserCoupon::getCouponId)
                    .collect(Collectors.toList());
            List<Coupon> addItemCoupons = FeignResultUtil.unwrap(couponFeignClient.getCouponsByIds(addItemCouponIds));
            for (Coupon c : addItemCoupons) {
                if (!c.getType().needDishItems()) {
                    throw new BusinessException("加菜仅支持使用菜品券，无法使用其它类型优惠券");
                }
                addItemUsedCoupons.add(c);
            }
            // 按本批加菜金额计算菜品券优惠。
            // 门槛按加菜后的整单金额判断，抵扣仍只落在本批实购菜品上。
            BigDecimal fullOrderAmount = (order.getTotalAmount() == null ? BigDecimal.ZERO
                    : order.getTotalAmount()).add(addAmount);
            Result calcResult = calcCouponDiscountWithLines(userId, addItemUserCouponIds,
                    fullOrderAmount, order.getStoreId(), couponLines(newItems));
            CouponDiscountResultVO calcData = (CouponDiscountResultVO) (calcResult != null ? calcResult.getData() : null);
            if (calcData == null || !Boolean.TRUE.equals(calcData.getUsable())) {
                throw new BusinessException("优惠券不可用：" + (calcData != null ? calcData.getReason() : "未知"));
            }
            // 按菜品ID汇总可免单金额（多张券同一菜品可累加）
            Map<String, BigDecimal> freeAmountByLine = new HashMap<>();
            if (calcData.getCouponDetails() != null) {
                for (CouponDiscountResultVO.CouponDetailVO detail : calcData.getCouponDetails()) {
                    if (detail.getCouponType() != null && detail.getCouponType().needDishItems() && detail.getDishItems() != null) {
                        for (CouponDiscountResultVO.DishItemVO di : detail.getDishItems()) {
                            if (di.getLineKey() != null && di.getCurrentPrice() != null) {
                                freeAmountByLine.merge(di.getLineKey(), di.getCurrentPrice(), BigDecimal::add);
                            }
                        }
                    }
                }
            }
            // 将优惠分摊到本批订单项：设置每项的 discountPrice 与 totalPrice
            boolean appliedAnyDiscount = false;
            for (int i = 0; i < newItems.size(); i++) {
                RestaurantOrderItem item = newItems.get(i);
                if (item.getIsGift() != null && item.getIsGift() == 1) {
                    continue;
                }
                String lineKey = String.valueOf(i);
                BigDecimal remaining = freeAmountByLine.get(lineKey);
                if (remaining == null || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal lineTotal = item.getLinePayableAmount() != null ? item.getLinePayableAmount() : BigDecimal.ZERO;
                BigDecimal deduct = lineTotal.min(remaining);
                item.setDiscountAmount((item.getDiscountAmount() == null ? BigDecimal.ZERO
                        : item.getDiscountAmount()).add(deduct));
                item.setLinePayableAmount(lineTotal.subtract(deduct));
                freeAmountByLine.put(lineKey, remaining.subtract(deduct));
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

        // 优惠分摊结束后再记录加菜事件，金额使用最终成交口径。
        recordAddDishChanges(order, newItems);

        // 7. 更新订单总金额
        // 加菜只扣减本批新增订单项，不能复用整单扣库存逻辑，否则会重复扣除历史批次。
        if (!newItems.isEmpty()) {
            Map<Long, Integer> addedDishStock = new HashMap<>();
            for (RestaurantOrderItem item : newItems) {
                addedDishStock.merge(item.getDishId(), item.getQuantity(), Integer::sum);
            }
            if (order.getPaymentStatus() == PaymentStatusEnum.PAID
                    || order.getPaymentStatus() == PaymentStatusEnum.PARTIAL_REFUND) {
                dishService.batchDecreaseStock(order.getStoreId(), addedDishStock);
            } else {
                dishService.batchReserveStock(order.getStoreId(), addedDishStock);
            }
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

            verifyCouponsWithCompensation(addItemUserCouponIds, order.getId());
        }

        log.info("成功追加菜品: orderId={}, 批次号={}, 追加金额={}, 新总金额={}",
                request.getOrderId(), newBatchNo, addAmount, order.getTotalAmount());

        // 8. WebSocket 推送：通知同桌其他用户有新菜品追加
        if (order.getTableId() != null && order.getSessionNo() != null) {
            try {
                TableSession tableSession = tableSessionService.getActiveSessionByTableId(order.getTableId());
                if (tableSession != null) {
                    // 事务提交后清空共享购物车并推送，避免同桌读取到旧状态。
                    RestaurantOrderVO orderVO = restaurantOrderConverter.toVO(order);
                    publishTableOrderAfterCommit(tableSession.getSessionNo(), orderVO, false);

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
     * 1. 创建订单（履约状态为已下单、支付状态为未支付）
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
        if (StrUtil.isBlank(request.getAuthCode())) {
            throw new BusinessException("请扫描顾客付款码");
        }
        PaymentTypeEnum paymentType = request.getPaymentType();
        if (paymentType != PaymentTypeEnum.WECHAT && paymentType != PaymentTypeEnum.ALIPAY) {
            throw new BusinessException("扫码支付仅支持微信或支付宝");
        }
        storeDataScopeService.checkStoreAccess(request.getStoreId());

        // 幂等：同一收款重试返回既有订单
        RestaurantOrder existing = findByRequestKey(request.getIdempotencyKey());
        if (existing != null) {
            if (!java.util.Objects.equals(existing.getStoreId(), request.getStoreId())) {
                throw new BusinessException("收银幂等键已被其他业务请求使用");
            }
            return restaurantOrderConverter.toVO(existing);
        }

        // 1. 建单（未支付）+ 明细 + 预占库存
        Long orderId = assembleScanPayOrder(request, paymentType);
        RestaurantOrder order = getById(orderId);

        // 2. 付款码扣款：金额只用服务端重算结果；用户支付中则轮询，超时撤单。
        BigDecimal payable = CashierAmountGuard.requireServerPayable(order.getActualAmount(), order.getTotalAmount());
        ScanPayOutcome outcome = scanPayCharger.charge(paymentType, order.getTenantId(), order.getOrderNo(),
                "餐饮订单-" + order.getOrderNo(), payable, request.getAuthCode());
        // 未支付：抛异常触发本地事务回滚，撤销订单与库存预占，顾客未被扣款。
        if (!outcome.paid()) {
            throw new BusinessException(StrUtil.blankToDefault(outcome.message(), "顾客付款码支付失败"));
        }

        // 3. 标记已支付并计入销量（复用线上支付入账路径）
        commitOrderSales(order);
        int updated = baseMapper.markPaidIfCapturable(orderId, paymentType.getCode(),
                outcome.transactionId(), LocalDateTime.now(), payable);
        if (updated != 1) {
            throw new BusinessException("订单支付状态更新失败");
        }

        // 4. 打印小票（失败不影响收款）
        if (request.getStoreId() != null) {
            StoreInfo storeInfoForPrint = storeInfoMapper.selectById(request.getStoreId());
            if (storeInfoForPrint != null && storeInfoForPrint.getIsPrint() == BooleanEnum.YES
                    && Boolean.TRUE.equals(request.getPrintReceipt())) {
                try {
                    printOrderWithStorePrinters(orderId, storeInfoForPrint.getId());
                } catch (Exception e) {
                    log.error("扫码支付小票打印失败: orderId={}, error={}", orderId, e.getMessage(), e);
                }
            }
        }
        return restaurantOrderConverter.toVO(getById(orderId));
    }

    /** 组装扫码支付订单：仅校验菜品、服务端计价、建单（未支付）与预占库存，返回订单ID。 */
    private Long assembleScanPayOrder(CashierOrderRequest request, PaymentTypeEnum paymentType) {
        validateCartItems(request.getStoreId(), request.getCartItems());
        PricingQuoteVO pricingQuote = quoteCashierItems(request.getStoreId(), request.getUserId(), request.getCartItems());
        BigDecimal totalAmount = pricingQuote.getListAmount();
        BigDecimal payable = BigDecimal.ZERO;
        for (int i = 0; i < request.getCartItems().size(); i++) {
            if (!Integer.valueOf(1).equals(request.getCartItems().get(i).getIsGift())) {
                payable = payable.add(pricingQuote.getLines().get(i).getLinePayableAmount());
            }
        }
        CouponDiscountResultVO couponQuote = requireCashierCoupon(request.getUserId(), request.getUserCouponIds(),
                payable, request.getStoreId(), cashierCouponLines(request.getCartItems(), pricingQuote));
        if (couponQuote != null) payable = payable.subtract(couponQuote.getTotalDiscountAmount()).max(BigDecimal.ZERO);
        if (payable.signum() == 0) {
            throw new BusinessException("优惠券已全额抵扣，请使用现金方式完成零元结算");
        }
        // 客户端金额仅做一致性检查，实际以服务端计价为准。
        CashierAmountGuard.requireClientAmountMatches(request.getTotalPrice(), payable);

        String orderNo = generateOrderNo();
        RestaurantOrder order = new RestaurantOrder();
        order.setOrderNo(orderNo);
        order.setRequestKey(request.getIdempotencyKey());
        order.setStoreId(request.getStoreId());
        order.setTableId(request.getTableId());
        order.setUserId(request.getUserId());
        order.setOrderType(request.getOrderType() != null ? request.getOrderType() : SceneTypeEnum.ADMIN_ORDER);
        order.setTotalAmount(totalAmount);
        order.setDiscountAmount(totalAmount.subtract(payable).max(BigDecimal.ZERO));
        order.setDiscountRate(BigDecimal.ONE);
        order.setActualAmount(payable);
        setCashierCouponMetadata(order, request.getUserCouponIds(), couponQuote);
        order.setDiningCount(request.getPeopleCount());
        order.setPaymentMode(PaymentModeEnum.ONLINE);
        order.setNote(request.getNote());
        order.setServiceFee(BigDecimal.ZERO);
        order.setPaymentStatus(PaymentStatusEnum.UNPAID);
        order.setPaymentType(paymentType);
        order.setStatus(OrderStatusEnum.PLACED);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        if (request.getStoreId() != null) {
            StoreInfo storeInfo = storeInfoMapper.selectById(request.getStoreId());
            if (storeInfo != null && storeInfo.getPickupMode() == pickupModeEnum.NUMBERCALLING) {
                assignPickupCode(order);
            }
        }
        insertOrder(order);

        List<RestaurantOrderItem> orderItems = new ArrayList<>();
        for (int i = 0; i < request.getCartItems().size(); i++) {
            CashierOrderRequest.CartItem cartItem = request.getCartItems().get(i);
            PricingQuoteVO.Line pricedLine = pricingQuote.getLines().get(i);
            RestaurantOrderItem orderItem = new RestaurantOrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setDishId(cartItem.getDishId());
            orderItem.setCategoryId(cartItem.getCategoryId());
            orderItem.setUnitListPrice(pricedLine.getUnitListPrice());
            orderItem.setUnitSellingPrice(pricedLine.getUnitSellingPrice());
            orderItem.setQuantity(cartItem.getQuantity());
            applyCostSnapshot(orderItem, pricedLine);
            boolean gift = Integer.valueOf(1).equals(cartItem.getIsGift());
            orderItem.setIsGift(gift ? 1 : 0);
            orderItem.setLinePayableAmount(gift ? BigDecimal.ZERO : pricedLine.getLinePayableAmount());
            orderItem.setDiscountAmount(gift ? pricedLine.getLineListAmount()
                    : pricedLine.getLineListAmount().subtract(pricedLine.getLinePayableAmount()));
            orderItem.setSpecOptionsText(cartItem.getSpecText());
            orderItem.setCreatedAt(LocalDateTime.now());
            orderItem.setUpdatedAt(LocalDateTime.now());
            orderItem.setBatchNo(1);
            orderItem.setBatchOrderNote(request.getNote());
            Long submitterId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
            orderItem.setSubmittedByUserId(submitterId);
            orderItem.setSubmittedByName(resolveOperatorName(submitterId));
            orderItem.setAddTime(LocalDateTime.now());
            orderItems.add(orderItem);
        }
        restaurantOrderItemService.saveBatch(orderItems);
        if (couponQuote != null) distributeCashierPayable(order.getId(), payable, couponQuote);
        persistPricingAdjustments(order.getId(), orderItems, pricingQuote);

        reserveOrderStock(order);
        verifyCouponsWithCompensation(request.getUserCouponIds(), order.getId());
        return order.getId();
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
        RestaurantOrder existing = findByRequestKey(request.getIdempotencyKey());
        if (existing != null) {
            if (!java.util.Objects.equals(existing.getStoreId(), request.getStoreId())
                    || !java.util.Objects.equals(existing.getUserId(), request.getUserId())) {
                throw new BusinessException("收银幂等键已被其他业务请求使用");
            }
            return restaurantOrderConverter.toVO(existing);
        }
        if (request.getPaymentType() == PaymentTypeEnum.BANLANCE_PAY && request.getUserId() == null) {
            throw new BusinessException("余额支付请先选择会员");
        }
        // 1. 仅校验菜品和规格，价格由服务端统一试价。
        validateCartItems(request.getStoreId(), request.getCartItems());
        PricingQuoteVO pricingQuote = quoteCashierItems(request.getStoreId(), request.getUserId(), request.getCartItems());

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
        BigDecimal calculatedTotalAmount = pricingQuote.getListAmount();
        BigDecimal payableBeforeRate = BigDecimal.ZERO;
        for (int i = 0; i < request.getCartItems().size(); i++) {
            CashierOrderRequest.CartItem cartItem = request.getCartItems().get(i);
            if (cartItem.getIsGift() != null && cartItem.getIsGift() == 1) {
                org.hongtu.security.OperationPermission.order("gift");
            } else {
                payableBeforeRate = payableBeforeRate.add(pricingQuote.getLines().get(i).getLinePayableAmount());
            }
        }
        BigDecimal itemDiscountSum = calculatedTotalAmount.subtract(payableBeforeRate).max(BigDecimal.ZERO);
        CouponDiscountResultVO couponQuote = requireCashierCoupon(request.getUserId(), request.getUserCouponIds(),
                payableBeforeRate, request.getStoreId(), cashierCouponLines(request.getCartItems(), pricingQuote));
        BigDecimal couponDiscount = couponQuote == null ? BigDecimal.ZERO : couponQuote.getTotalDiscountAmount();
        BigDecimal payableAfterCoupon = payableBeforeRate.subtract(couponDiscount).max(BigDecimal.ZERO);
        if (request.getDiscountRate() != null && request.getDiscountRate().compareTo(BigDecimal.ONE) != 0)
            org.hongtu.security.OperationPermission.order("discount");
        BigDecimal discountRate = request.getDiscountRate() != null ?
                request.getDiscountRate() : BigDecimal.ONE;
        // 实付 = 折前应付 * 折扣率；订单总优惠 = 订单项优惠 + 折扣优惠
        BigDecimal calculatedActualAmount = CashierAmountGuard.applyManualDiscount(payableAfterCoupon, discountRate);
        BigDecimal rateDiscountAmount = payableAfterCoupon.subtract(calculatedActualAmount);
        if (calculatedActualAmount.signum() == 0 && request.getPaymentType() == PaymentTypeEnum.BANLANCE_PAY) {
            throw new BusinessException("优惠券已全额抵扣，请使用现金方式完成零元结算");
        }
        BigDecimal totalDiscountAmount = itemDiscountSum.add(couponDiscount).add(rateDiscountAmount);

        // 3. 客户端金额仅用于一致性检查，订单和扣款金额统一使用服务端计价结果。
        CashierAmountGuard.requireClientAmountMatches(request.getTotalPrice(), calculatedActualAmount);

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
        order.setRequestKey(request.getIdempotencyKey());
        order.setStoreId(request.getStoreId());
        order.setTableId(request.getTableId());
        order.setUserId(request.getUserId());
        order.setOrderType(request.getOrderType() != null ? request.getOrderType() : SceneTypeEnum.ADMIN_ORDER);
        order.setTotalAmount(calculatedTotalAmount);
        order.setDiscountAmount(totalDiscountAmount);
        order.setDiscountRate(discountRate);
        order.setActualAmount(calculatedActualAmount);
        setCashierCouponMetadata(order, request.getUserCouponIds(), couponQuote);
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
                assignPickupCode(order);
            }
        }

        insertOrder(order);
        log.info("收银台直接下单成功: orderId={}, orderNo={}, totalAmount={}, discountRate={}, actualAmount={}, paymentType={}",
                order.getId(), orderNo, calculatedTotalAmount, discountRate, calculatedActualAmount, order.getPaymentType().getDesc());

        // 5. 创建订单明细；人工整单折扣按行分摊，保证订单项应付合计等于订单实付。
        List<RestaurantOrderItem> orderItems = new ArrayList<>();
        if (request.getCartItems() != null && !request.getCartItems().isEmpty()) {
            Map<String, BigDecimal> couponByLine = couponLineDiscounts(couponQuote);
            BigDecimal remainingActualAmount = calculatedActualAmount;
            BigDecimal remainingBaseAmount = payableBeforeRate.subtract(couponByLine.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)).max(BigDecimal.ZERO);
            for (int i = 0; i < request.getCartItems().size(); i++) {
                CashierOrderRequest.CartItem cartItem = request.getCartItems().get(i);
                PricingQuoteVO.Line pricedLine = pricingQuote.getLines().get(i);
                RestaurantOrderItem orderItem = new RestaurantOrderItem();
                orderItem.setOrderId(order.getId());
                orderItem.setDishId(cartItem.getDishId());
                orderItem.setCategoryId(cartItem.getCategoryId());
                orderItem.setUnitListPrice(pricedLine.getUnitListPrice());
                orderItem.setUnitSellingPrice(pricedLine.getUnitSellingPrice());
                orderItem.setQuantity(cartItem.getQuantity());
                applyCostSnapshot(orderItem, pricedLine);

                // 设置赠送标识
                orderItem.setIsGift(cartItem.getIsGift() != null ? cartItem.getIsGift() : 0);

                BigDecimal itemTotal = pricedLine.getLineListAmount();

                if (orderItem.getIsGift() == 1) {
                    // 赠菜：单价保留，优惠金额=该行原价；小计 totalPrice = 原价小计 - 优惠 = 0（该行实付）
                    orderItem.setDiscountAmount(itemTotal);
                    orderItem.setLinePayableAmount(BigDecimal.ZERO);
                } else {
                    BigDecimal lineBase = pricedLine.getLinePayableAmount()
                            .subtract(couponByLine.getOrDefault(String.valueOf(i), BigDecimal.ZERO))
                            .max(BigDecimal.ZERO);
                    BigDecimal linePayable = lineBase.signum() == 0 ? BigDecimal.ZERO
                            : remainingBaseAmount.compareTo(lineBase) == 0 ? remainingActualAmount
                            : remainingActualAmount.multiply(lineBase)
                                    .divide(remainingBaseAmount, 2, RoundingMode.HALF_UP);
                    orderItem.setLinePayableAmount(linePayable);
                    orderItem.setDiscountAmount(pricedLine.getLineListAmount().subtract(linePayable));
                    remainingActualAmount = remainingActualAmount.subtract(linePayable);
                    remainingBaseAmount = remainingBaseAmount.subtract(lineBase);
                }

                orderItem.setSpecOptionsText(cartItem.getSpecText());
                orderItem.setCreatedAt(LocalDateTime.now());
                orderItem.setUpdatedAt(LocalDateTime.now());
                orderItem.setBatchNo(1); // 首次下单，批次号为1
                orderItem.setBatchOrderNote(request.getNote());
                Long submitterId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null;
                orderItem.setSubmittedByUserId(submitterId);
                orderItem.setSubmittedByName(resolveOperatorName(submitterId));
                orderItem.setAddTime(LocalDateTime.now());

                orderItems.add(orderItem);
            }

            // 批量保存订单明细
            restaurantOrderItemService.saveBatch(orderItems);
            persistPricingAdjustments(order.getId(), orderItems, pricingQuote);
            persistManualRateAdjustments(order.getId(), orderItems, pricingQuote, discountRate);
        }

        // 6. 减少菜品库存并增加销量，同时记录已预占，防止后续重复扣减。
        try {
            reserveOrderStock(order);
        } catch (Exception e) {
            log.error("订单【{}】减少库存失败: {}", order.getOrderNo(), e.getMessage(), e);
            throw new BusinessException("库存不足或减少失败：" + e.getMessage());
        }

        // 先核销优惠券；后续本地事务失败会由回滚回调返券。
        verifyCouponsWithCompensation(request.getUserCouponIds(), order.getId());

        // 6.5 余额支付：扣减用户余额并记录流水（user-service 本地事务原子完成）
        // 【微服务改造】刻意放在库存扣减之后：远程扣款失败会抛异常，本地事务（订单/明细/库存）整体回滚，无需补偿
        if (user != null) {
            changeBalanceDurable(order.getId(), BalanceChangeDTO.builder()
                    .userId(request.getUserId())
                    .amount(calculatedActualAmount)
                    .type(BalanceResourceEnum.EXPENSE.getCode())
                    .orderNo(orderNo)
                    .operateUserId(StpUtil.getLoginIdAsLong())
                    .build());
            log.info("收银台余额支付扣款: orderId={}, userId={}, actualAmount={}",
                    order.getId(), request.getUserId(), calculatedActualAmount);
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
     * 收银台-桌位结算：对会话当前订单完成前台收款，并结束会话、释放桌位。
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
        // 锁定会话，防止并发结算导致重复扣余额或重复释放桌位。
        sessionWrapper.eq(TableSession::getSessionNo, sessionNo).last("FOR UPDATE");
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
        RestaurantOrder order = getOrderForUpdate(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!sessionNo.equals(order.getSessionNo())) {
            throw new BusinessException("订单与会话不匹配");
        }
        storeDataScopeService.checkStoreAccess(order.getStoreId());
        if (order.getStatus() == OrderStatusEnum.CANCELLED || order.getStatus() == OrderStatusEnum.COMPLETED) {
            throw new BusinessException("当前订单状态不允许结算");
        }
        if (order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            throw new BusinessException("订单已支付，无需重复结算");
        }
        if (order.getPaymentStatus() == PaymentStatusEnum.PARTIAL_REFUND
                || order.getPaymentStatus() == PaymentStatusEnum.FULL_REFUND) {
            throw new BusinessException("退款订单不允许再次结算");
        }

        // 2.5 从服务端保存的订单项快照重算应付，禁止信任客户端或订单主表中的异常金额。
        long orderItemCount = restaurantOrderItemService.count(new LambdaQueryWrapper<RestaurantOrderItem>()
                .eq(RestaurantOrderItem::getOrderId, orderId));
        if (orderItemCount <= 0) {
            throw new BusinessException("订单没有有效明细，禁止结算");
        }
        order = recalculateOrderAmountsFromItems(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2.6 收银传入的备注、折扣：折扣只能作用于服务端重算后的应付金额。
        if (request.getNote() != null) {
            order.setNote(request.getNote());
        }
        BigDecimal discountRate = request.getDiscountRate() == null ? BigDecimal.ONE : request.getDiscountRate();
        if (discountRate.compareTo(BigDecimal.ONE) != 0) {
            org.hongtu.security.OperationPermission.order("discount");
        }
        List<Long> selectedCoupons = request.getUserCouponIds();
        CouponDiscountResultVO couponQuote = null;
        BigDecimal couponDiscount = BigDecimal.ZERO;
        BigDecimal beforeCoupon = order.getActualAmount();
        if (selectedCoupons != null && !selectedCoupons.isEmpty()) {
            if (order.getUserId() == null || !order.getUserId().equals(request.getUserId())) {
                throw new BusinessException("优惠券会员与订单会员不一致");
            }
            if (StrUtil.isNotBlank(order.getUserCouponIds())) {
                throw new BusinessException("订单已有优惠券，不能重复使用");
            }
            List<RestaurantOrderItem> couponItems = restaurantOrderItemService.list(
                    new LambdaQueryWrapper<RestaurantOrderItem>().eq(RestaurantOrderItem::getOrderId, orderId)
                            .orderByAsc(RestaurantOrderItem::getId));
            couponQuote = requireCashierCoupon(order.getUserId(), selectedCoupons, beforeCoupon,
                    order.getStoreId(), couponLines(couponItems));
            couponDiscount = couponQuote.getTotalDiscountAmount();
        }
        BigDecimal serverPayable = CashierAmountGuard.applyManualDiscount(
                beforeCoupon.subtract(couponDiscount).max(BigDecimal.ZERO), discountRate);
        if (serverPayable.compareTo(BigDecimal.ZERO) <= 0 && couponQuote == null) {
            throw new BusinessException("服务端应付金额为0，禁止人工结算，请检查订单优惠和明细");
        }
        order.setDiscountRate(discountRate);
        order.setActualAmount(serverPayable);
        setCashierCouponMetadata(order, selectedCoupons, couponQuote);
        // 优惠金额（菜品优惠+折扣优惠）始终 = 总价 - 实付，保证无折扣时也正确显示
        BigDecimal total = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        order.setDiscountAmount(total.subtract(serverPayable).max(BigDecimal.ZERO));
        if (couponQuote != null || discountRate.compareTo(BigDecimal.ONE) != 0) {
            distributeCashierPayable(orderId, serverPayable, couponQuote);
        }

        PaymentTypeEnum paymentType = CashierAmountGuard.requireSettlementPaymentType(request.getPaymentType());
        boolean scanPayment = paymentType == PaymentTypeEnum.WECHAT || paymentType == PaymentTypeEnum.ALIPAY;
        if (serverPayable.signum() == 0 && paymentType == PaymentTypeEnum.BANLANCE_PAY) {
            throw new BusinessException("优惠券已全额抵扣，请使用现金方式完成零元结算");
        }
        if (scanPayment && serverPayable.signum() == 0) {
            throw new BusinessException("优惠券已全额抵扣，请使用现金方式完成零元结算");
        }
        if (scanPayment && StrUtil.isBlank(request.getAuthCode())) {
            throw new BusinessException("请扫描顾客付款码");
        }
        BigDecimal tenderedAmount = CashierAmountGuard.requireTenderedAmount(
                paymentType, request.getActualAmount(), serverPayable);
        verifyCouponsWithCompensation(selectedCoupons, order.getId());
        BigDecimal deductedBalanceAmount = null;
        String scanTransactionId = null;
        if (scanPayment) {
            // 顾客选择到前台付款只决定结算地点；收银员仍通过付款码完成真实扣款。
            ScanPayOutcome outcome = scanPayCharger.charge(paymentType, order.getTenantId(), order.getOrderNo(),
                    "餐饮订单-" + order.getOrderNo(), serverPayable, request.getAuthCode());
            if (!outcome.paid()) {
                throw new BusinessException(StrUtil.blankToDefault(outcome.message(), "顾客付款码支付失败"));
            }
            scanTransactionId = outcome.transactionId();
        }
        if (paymentType == PaymentTypeEnum.BANLANCE_PAY) {
            if (request.getUserId() == null) {
                throw new BusinessException("余额支付请先选择会员");
            }
            SysUser user = FeignResultUtil.unwrap(userFeignClient.getUserById(request.getUserId()));
            if (user == null) {
                throw new BusinessException("用户不存在");
            }
            BigDecimal needPay = serverPayable;
            if (user.getSurplusMoney() == null || user.getSurplusMoney().compareTo(needPay) < 0) {
                throw new BusinessException("用户余额不足，当前余额：" +
                        (user.getSurplusMoney() != null ? user.getSurplusMoney() : "0") + " 元");
            }
            // 扣减余额并记录流水（user-service 本地事务原子完成；失败抛异常，本地事务回滚）
            changeBalanceDurable(order.getId(), BalanceChangeDTO.builder()
                    .userId(request.getUserId())
                    .amount(needPay)
                    .type(BalanceResourceEnum.EXPENSE.getCode())
                    .orderNo(order.getOrderNo())
                    .operateUserId(StpUtil.getLoginIdAsLong())
                    .build());
            deductedBalanceAmount = needPay;
        }

        try {
            // 3. 更新订单为已支付
            order.setPaymentStatus(PaymentStatusEnum.PAID);
            order.setPaymentType(paymentType);
            if (scanPayment) {
                order.setPaymentNo(scanTransactionId);
            }
            order.setPaymentTime(LocalDateTime.now());
            order.setActualAmount(serverPayable);
            order.setStatus(OrderStatusEnum.COMPLETED);
            order.setUpdatedAt(LocalDateTime.now());
            commitOrderSales(order);
            this.updateById(order);
            if (order.getUserId() != null)
                localOutboxService.enqueueMemberReward(order.getId(), "MEMBER_REWARD:" + order.getId() + ":COMPLETE");

            // 4. 结束会话并更新桌位状态为空闲
            tableSessionService.endSession(sessionNo, serverPayable);
            pushSessionEndedAfterCommit(sessionNo, serverPayable);

            // 5. 打印小票（可选）
            if (Boolean.TRUE.equals(request.getPrintReceipt())) {
                try {
                    printOrderWithStorePrinters(order.getId(), order.getStoreId());
                } catch (Exception printError) {
                    // 收款已完成，打印故障不能让前台把整笔结算误判为失败。
                    log.error("桌位结算小票打印失败: orderId={}", order.getId(), printError);
                }
            }

            log.info("桌位结算成功: sessionNo={}, orderId={}, orderNo={}, payableAmount={}, tenderedAmount={}, paymentType={}",
                    sessionNo, order.getId(), order.getOrderNo(), serverPayable, tenderedAmount, paymentType);

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
     * 未支付订单只预占库存。已经支付的订单同时计入销量。
     */
    private void reserveOrderStock(RestaurantOrder order) {
        if (order == null) {
            return;
        }
        if (Boolean.TRUE.equals(order.getStockReserved())) {
            if (order.getPaymentStatus() == PaymentStatusEnum.PAID
                    && !Boolean.TRUE.equals(order.getSalesCommitted())) {
                commitOrderSales(order);
                this.updateById(order);
            }
            return;
        }
        decreaseOrderDishStock(order.getId());
        order.setStockReserved(true);
        order.setUpdatedAt(LocalDateTime.now());
        if (order.getPaymentStatus() == PaymentStatusEnum.PAID) {
            commitOrderSales(order);
        }
        if (!this.updateById(order)) {
            throw new BusinessException("记录订单库存预占状态失败");
        }
    }

    /** 支付成功后把已预占数量计入销量，重复通知不会再次累加。 */
    private void commitOrderSales(RestaurantOrder order) {
        if (order == null || Boolean.TRUE.equals(order.getSalesCommitted())) {
            return;
        }
        Map<Long, Integer> quantities = orderItemQuantities(order.getId());
        if (!quantities.isEmpty()) {
            dishService.batchCommitSales(order.getStoreId(), quantities);
        }
        order.setSalesCommitted(true);
        order.setUpdatedAt(LocalDateTime.now());
    }

    /** 取消或超时时释放仍留在订单中的预占；已计销量的订单同时冲减销量。 */
    private void restoreReservedOrderStock(RestaurantOrder order) {
        if (order == null || !Boolean.TRUE.equals(order.getStockReserved())) {
            return;
        }
        Map<Long, Integer> quantities = orderItemQuantities(order.getId());
        if (!quantities.isEmpty()) {
            if (Boolean.TRUE.equals(order.getSalesCommitted())) {
                dishService.batchIncreaseStockAndDecreaseSales(order.getStoreId(), quantities);
            } else {
                dishService.batchReleaseReservedStock(order.getStoreId(), quantities);
            }
        }
        order.setStockReserved(false);
        order.setSalesCommitted(false);
        order.setUpdatedAt(LocalDateTime.now());
    }

    private Map<Long, Integer> orderItemQuantities(Long orderId) {
        LambdaQueryWrapper<RestaurantOrderItem> itemWrapper = new LambdaQueryWrapper<>();
        itemWrapper.eq(RestaurantOrderItem::getOrderId, orderId);
        return restaurantOrderItemService.list(itemWrapper).stream()
                .collect(Collectors.toMap(RestaurantOrderItem::getDishId,
                        RestaurantOrderItem::getQuantity, Integer::sum));
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

            // 库存属于门店，必须按订单门店扣减，不能再修改总部菜品主数据。
            RestaurantOrder order = this.getById(orderId);
            if (order == null) throw new BusinessException("订单不存在");
            dishService.batchReserveStock(order.getStoreId(), dishStockMap);
            log.info("订单【orderId={}】已减少菜品库存: {}", orderId, dishStockMap);
        }
    }

    /**
     * 生成取餐码（叫号自取模式使用）
     * 策略：每天按顺序从001开始递增，第二天重新从001开始
     * 格式：少于3位数字时前面补0（001、002...999、1000、1001...）
     */
    private void assignPickupCode(RestaurantOrder order) {
        LocalDate pickupDate = LocalDate.now();
        String redisKey = "order:pickup-code:" + TenantContext.getTenantId()
                + ":" + order.getStoreId() + ":" + pickupDate;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                "local value = redis.call('INCR', KEYS[1]); "
                        + "if redis.call('TTL', KEYS[1]) < 0 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; return value;",
                Long.class);
        // Lua 参数必须按纯字符串传递，通用 RedisTemplate 的 JSON 序列化会让 EXPIRE 无法解析秒数。
        Long nextCode = stringRedisTemplate.execute(
                script, Collections.singletonList(redisKey), String.valueOf(3 * 24 * 60 * 60));
        if (nextCode == null || nextCode <= 0) {
            throw new BusinessException("取餐码生成失败，请稍后重试");
        }
        order.setPickupDate(pickupDate);
        order.setPickupCode(nextCode < 1000 ? String.format("%03d", nextCode) : String.valueOf(nextCode));
    }

    private void changeBalanceDurable(Long orderId, BalanceChangeDTO dto) {
        localOutboxService.executeRemote("BALANCE_CHANGE",
                "balance:" + dto.getOrderNo() + ":" + dto.getType(),
                JSONUtil.createObj()
                        .set("orderId", orderId)
                        .set("userId", dto.getUserId())
                        .set("amount", dto.getAmount())
                        .set("type", dto.getType())
                        .set("orderNo", dto.getOrderNo())
                        .set("sourceOrderNo", dto.getSourceOrderNo())
                        .toString(),
                () -> FeignResultUtil.check(userFeignClient.changeBalance(dto)));
    }

    private void insertOrder(RestaurantOrder order) {
        persistOrderWithPickupRetry(order, true);
    }

    private void updateOrder(RestaurantOrder order) {
        persistOrderWithPickupRetry(order, false);
    }

    /**
     * 取餐码唯一约束冲突时重新取号。Redis 不可用时直接失败，不退回随机三位码。
     */
    private void persistOrderWithPickupRetry(RestaurantOrder order, boolean insert) {
        for (int attempt = 1; attempt <= 5; attempt++) {
            try {
                boolean saved = insert ? this.baseMapper.insert(order) > 0 : this.updateById(order);
                if (!saved) {
                    throw new BusinessException(insert ? "保存订单失败" : "更新订单失败");
                }
                return;
            } catch (DuplicateKeyException duplicate) {
                if (attempt == 5 || StrUtil.isBlank(order.getPickupCode()) || !isPickupDuplicate(duplicate)) {
                    throw duplicate;
                }
                log.warn("取餐码冲突，重新生成: orderNo={}, attempt={}", order.getOrderNo(), attempt);
                assignPickupCode(order);
            }
        }
    }

    private boolean isPickupDuplicate(DuplicateKeyException duplicate) {
        Throwable cause = duplicate.getMostSpecificCause();
        String message = cause == null ? duplicate.getMessage() : cause.getMessage();
        return message != null && message.contains("uk_tenant_store_pickup");
    }

    /**
     * 多张券跨服务核销时逐张记录成功项；任一失败立即按原订单号补偿，避免部分核销。
     */
    private void verifyCouponsWithCompensation(List<Long> userCouponIds, Long orderId) {
        if (userCouponIds == null || userCouponIds.isEmpty()) {
            return;
        }
        List<Long> verified = new ArrayList<>();
        try {
            for (Long userCouponId : userCouponIds) {
                localOutboxService.executeRemote("COUPON_VERIFY",
                        "coupon-verify:" + orderId + ":" + userCouponId,
                        JSONUtil.createObj().set("orderId", orderId).set("userCouponId", userCouponId).toString(),
                        () -> FeignResultUtil.check(couponFeignClient.verifyCouponOnOrder(userCouponId, orderId)));
                verified.add(userCouponId);
                log.info("优惠券已核销: orderId={}, userCouponId={}", orderId, userCouponId);
            }
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                Long tenantId = TenantContext.getTenantId();
                List<Long> rollbackCoupons = List.copyOf(verified);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_ROLLED_BACK) {
                            return;
                        }
                        TenantScope.run(tenantId, () -> rollbackCoupons.forEach(couponId -> {
                            try {
                                FeignResultUtil.check(couponFeignClient.restoreCouponOnCancel(couponId, orderId));
                            } catch (Exception compensationError) {
                                log.error("订单事务回滚后的优惠券补偿失败: orderId={}, userCouponId={}",
                                        orderId, couponId, compensationError);
                            }
                        }));
                    }
                });
            }
        } catch (RuntimeException verifyError) {
            for (Long verifiedId : verified) {
                try {
                    FeignResultUtil.check(couponFeignClient.restoreCouponOnCancel(verifiedId, orderId));
                } catch (Exception compensationError) {
                    log.error("优惠券核销补偿失败，需告警处理: orderId={}, userCouponId={}",
                            orderId, verifiedId, compensationError);
                }
            }
            throw verifyError;
        }
    }

    /** 取消订单返券出现部分失败时，将已返还部分重新绑定原订单，维持两侧一致。 */
    private void restoreCouponsWithCompensation(List<Long> userCouponIds, Long orderId) {
        List<Long> restored = new ArrayList<>();
        try {
            for (Long userCouponId : userCouponIds) {
                localOutboxService.executeRemote("COUPON_RESTORE",
                        "coupon-restore:" + orderId + ":" + userCouponId,
                        JSONUtil.createObj().set("orderId", orderId).set("userCouponId", userCouponId).toString(),
                        () -> FeignResultUtil.check(couponFeignClient.restoreCouponOnCancel(userCouponId, orderId)));
                restored.add(userCouponId);
                log.info("优惠券已返还: orderId={}, userCouponId={}", orderId, userCouponId);
            }
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                Long tenantId = TenantContext.getTenantId();
                List<Long> rollbackCoupons = List.copyOf(restored);
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_ROLLED_BACK) {
                            return;
                        }
                        TenantScope.run(tenantId, () -> rollbackCoupons.forEach(couponId -> {
                            try {
                                FeignResultUtil.check(couponFeignClient.undoRestore(couponId, orderId));
                            } catch (Exception compensationError) {
                                log.error("取消事务回滚后的优惠券补偿失败: orderId={}, userCouponId={}",
                                        orderId, couponId, compensationError);
                            }
                        }));
                    }
                });
            }
        } catch (RuntimeException restoreError) {
            for (Long restoredId : restored) {
                try {
                    FeignResultUtil.check(couponFeignClient.undoRestore(restoredId, orderId));
                } catch (Exception compensationError) {
                    log.error("取消订单返券补偿失败，需告警处理: orderId={}, userCouponId={}",
                            orderId, restoredId, compensationError);
                }
            }
            throw restoreError;
        }
    }

    /**
     * 验证购物车商品数据（CreateOrderRequest）
     * 验证内容：
     * 1. 菜品是否存在
     * 2. 规格额外价格是否与服务端规格定义一致
     * 客户端价格字段不参与校验和结算，最终金额统一由 PricingService 计算。
     *
     * @param cartItems 购物车商品列表
     * @throws BusinessException 验证失败时抛出异常
     */
    private void validateCreateOrderCartItems(Long storeId, List<CreateOrderRequest.CartItem> cartItems) {
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
            DishVO dish = dishService.getEffectiveDish(cartItem.getDishId(), storeId, true);

            if (dish.getEffectiveSalePrice() == null) {
                throw new BusinessException("菜品【" + dish.getDishName() + "】价格未设置");
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

            log.info("菜品【{}】验证通过，服务端正常售价：{}，规格价格：{}",
                    dish.getDishName(), dish.getEffectiveSalePrice(), cartItem.getExtraPrice());
        }

        log.info("购物车商品数据验证完成");
    }

    /**
     * 验证购物车商品数据（CashierOrderRequest）
     * 验证内容：
     * 1. 菜品是否存在
     * 2. 规格额外价格是否与服务端规格定义一致
     * 客户端价格字段不参与校验和结算，最终金额统一由 PricingService 计算。
     *
     * @param cartItems 购物车商品列表
     * @throws BusinessException 验证失败时抛出异常
     */
    private void validateCartItems(Long storeId, List<CashierOrderRequest.CartItem> cartItems) {
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
            DishVO dish = dishService.getEffectiveDish(cartItem.getDishId(), storeId, true);

            if (dish.getEffectiveSalePrice() == null) {
                throw new BusinessException("菜品【" + dish.getDishName() + "】价格未设置");
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

            log.info("菜品【{}】验证通过，服务端正常售价：{}，规格价格：{}",
                    dish.getDishName(), dish.getEffectiveSalePrice(), cartItem.getExtraPrice());
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
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            Long tenantId = TenantContext.getTenantId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    TenantScope.run(tenantId, () -> doPrintOrderWithStorePrinters(orderId, storeId));
                }
            });
            return;
        }
        doPrintOrderWithStorePrinters(orderId, storeId);
    }

    private void doPrintOrderWithStorePrinters(Long orderId, Long storeId) {
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
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            Long tenantId = TenantContext.getTenantId();
            List<Long> itemIds = List.copyOf(orderItemIds);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    TenantScope.run(tenantId, () -> doPrintAddItemsWithStorePrinters(orderId, itemIds, storeId));
                }
            });
            return;
        }
        doPrintAddItemsWithStorePrinters(orderId, orderItemIds, storeId);
    }

    private void doPrintAddItemsWithStorePrinters(Long orderId, List<Long> orderItemIds, Long storeId) {
        try {
            List<PrinterInfo> enabledPrinters = printerInfoService.getEnabledPrintersByStore(storeId);
            if (enabledPrinters == null || enabledPrinters.isEmpty()) {
                log.warn("门店【{}】开启了打印功能，但未配置启用的打印机", storeId);
                return;
            }
            log.info("门店【{}】开始打印加菜单 orderId={}，本批{}项，共{}台打印机", storeId, orderId, orderItemIds.size(), enabledPrinters.size());
            for (PrinterInfo printer : enabledPrinters) {
                try {
                    // 让打印服务按加菜用途选模板，未配置时才回退到打印机绑定模板。
                    Map<String, Object> printResult = orderPrintService.printAddItems(orderId, orderItemIds, printer.getId(), null);
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

    /** 订单落库成功后清空共享购物车，再向同桌设备广播可读取的新状态。 */
    private void publishTableOrderAfterCommit(String sessionNo, RestaurantOrderVO orderVO, boolean created) {
        Long tenantId = TenantContext.getTenantId();
        Runnable publish = () -> TenantScope.run(tenantId, () -> {
            try {
                sharedCartService.clearCartAfterOrder(sessionNo);
                webSocketPushService.pushCartCleared(sessionNo);
            } catch (Exception e) {
                log.error("清空已提交订单的共享购物车失败: sessionNo={}", sessionNo, e);
            }
            try {
                if (created) webSocketPushService.pushOrderCreated(sessionNo, orderVO);
                else webSocketPushService.pushOrderUpdated(sessionNo, orderVO);
            } catch (Exception e) {
                log.error("推送新订单失败: sessionNo={}", sessionNo, e);
            }
        });
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }
        publish.run();
    }

    private void pushSessionEndedAfterCommit(String sessionNo, BigDecimal paidAmount) {
        Long tenantId = TenantContext.getTenantId();
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    TenantScope.run(tenantId,
                            () -> webSocketPushService.pushSessionEnded(sessionNo, paidAmount));
                }
            });
            return;
        }
        webSocketPushService.pushSessionEnded(sessionNo, paidAmount);
    }

    // ==================== 优惠券计算（下单专用） ====================

    /**
     * 获取优惠券计算优先级（数值越小越先计算）
     * 计算顺序：菜品免单券 > 菜品折扣券 > 代金券 > 满减券 > 整单折扣券。
     */
    private int getCouponPriority(CouponTypeEnum type) {
        if (type == null) return 99;
        return switch (type) {
            case DISH_FREE -> 1;
            case DISH_DISCOUNT -> 2;
            case CASH_VOUCHER -> 3;
            case FULL_REDUCTION -> 4;
            case ORDER_DISCOUNT -> 5;
        };
    }

    /** 每个购物车行保留可免单数量，防止多张券重复免掉同一份菜品。 */
    static final class CouponOrderLine {
        private final String lineKey;
        private final Long dishId;
        private final String dishName;
        private final BigDecimal linePayable;
        private final BigDecimal unitPayable;
        private BigDecimal remainingEligibleAmount;
        private int remainingQuantity;

        CouponOrderLine(String lineKey, Long dishId, String dishName,
                                int quantity, BigDecimal linePayable) {
            this.lineKey = lineKey;
            this.dishId = dishId;
            this.dishName = dishName;
            this.linePayable = linePayable;
            this.unitPayable = linePayable.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.DOWN);
            this.remainingEligibleAmount = linePayable;
            this.remainingQuantity = quantity;
        }
    }

    private List<CouponOrderLine> couponLines(List<CreateOrderRequest.CartItem> cartItems,
                                               PricingQuoteVO quote) {
        List<CouponOrderLine> lines = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            CreateOrderRequest.CartItem cart = cartItems.get(i);
            if (cart.getIsGift() != null && cart.getIsGift() == 1) continue;
            PricingQuoteVO.Line priced = quote.getLines().get(i);
            lines.add(new CouponOrderLine(String.valueOf(i), cart.getDishId(), priced.getDishName(),
                    cart.getQuantity(), priced.getLinePayableAmount()));
        }
        return lines;
    }

    private List<CouponOrderLine> couponLines(List<RestaurantOrderItem> items) {
        List<CouponOrderLine> lines = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            RestaurantOrderItem item = items.get(i);
            if (item.getIsGift() != null && item.getIsGift() == 1) continue;
            lines.add(new CouponOrderLine(String.valueOf(i), item.getDishId(), null,
                    item.getQuantity(), item.getLinePayableAmount()));
        }
        return lines;
    }

    /** 多个候选菜品时自动选一份价格最高的实购菜品。 */
    private CouponOrderLine chooseFreeDishLine(Coupon coupon, List<CouponOrderLine> orderLines) {
        if (orderLines == null || orderLines.isEmpty()) return null;
        List<CouponDishItem> allowed = FeignResultUtil.unwrap(couponFeignClient.getItemsByCouponId(coupon.getId()));
        if (allowed == null || allowed.isEmpty()) return null;
        Set<Long> allowedIds = allowed.stream().map(CouponDishItem::getDishId).collect(Collectors.toSet());
        CouponOrderLine best = null;
        for (CouponOrderLine line : orderLines) {
            if (line.remainingQuantity <= 0 || !allowedIds.contains(line.dishId)
                    || line.unitPayable.compareTo(BigDecimal.ZERO) <= 0) continue;
            if (best == null || line.unitPayable.compareTo(best.unitPayable) > 0) best = line;
        }
        return best;
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
    public Result calcCouponDiscount(Long userId, List<Long> userCouponIds,
                                     BigDecimal orderTotalAmount, Long storeId) {
        return calcCouponDiscountWithLines(userId, userCouponIds, orderTotalAmount, storeId, null);
    }

    /** 结算页以服务端定价重算购物车，防止客户端伪造菜品或价格。 */
    @Override
    public Result calcCouponDiscountForCart(Long userId, CouponQuoteRequest request) {
        if (request == null || request.getStoreId() == null || request.getCartItems() == null
                || request.getCartItems().isEmpty()) {
            return Result.error("请选择门店和菜品");
        }
        validateCreateOrderCartItems(request.getStoreId(), request.getCartItems());
        PricingQuoteVO pricing = quoteCreateItems(request.getStoreId(), userId, request.getCartItems(), false);
        List<CouponOrderLine> lines = couponLines(request.getCartItems(), pricing);
        BigDecimal total = lines.stream().map(line -> line.linePayable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return calcCouponDiscountWithLines(userId, request.getUserCouponIds(), total, request.getStoreId(), lines);
    }

    @Override
    public Result cashierCouponQuote(CashierCouponQuoteRequest request) {
        if (request == null || request.getUserId() == null) {
            throw new BusinessException("请先选择会员");
        }
        if (StrUtil.isNotBlank(request.getSessionNo())) {
            RestaurantOrder order = getOrderBySessionNo(request.getSessionNo());
            if (order == null || !request.getSessionNo().equals(order.getSessionNo())) {
                throw new BusinessException("桌位订单不存在");
            }
            storeDataScopeService.checkStoreAccess(order.getStoreId());
            if (!request.getUserId().equals(order.getUserId())) {
                throw new BusinessException("优惠券会员与订单会员不一致");
            }
            if (StrUtil.isNotBlank(order.getUserCouponIds()) || order.getPaymentStatus() == PaymentStatusEnum.PAID) {
                throw new BusinessException("订单已有优惠券或已支付");
            }
            List<RestaurantOrderItem> items = restaurantOrderItemService.list(
                    new LambdaQueryWrapper<RestaurantOrderItem>().eq(RestaurantOrderItem::getOrderId, order.getId())
                            .orderByAsc(RestaurantOrderItem::getId));
            List<CouponOrderLine> lines = couponLines(items);
            BigDecimal total = lines.stream().map(line -> line.linePayable)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return calcCouponDiscountWithLines(request.getUserId(), request.getUserCouponIds(),
                    total, order.getStoreId(), lines);
        }
        if (request.getStoreId() == null || request.getCartItems() == null || request.getCartItems().isEmpty()) {
            throw new BusinessException("请选择门店和菜品");
        }
        storeDataScopeService.checkStoreAccess(request.getStoreId());
        validateCartItems(request.getStoreId(), request.getCartItems());
        PricingQuoteVO pricing = quoteCashierItems(request.getStoreId(), request.getUserId(), request.getCartItems());
        List<CouponOrderLine> lines = cashierCouponLines(request.getCartItems(), pricing);
        BigDecimal total = lines.stream().map(line -> line.linePayable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return calcCouponDiscountWithLines(request.getUserId(), request.getUserCouponIds(),
                total, request.getStoreId(), lines);
    }

    private List<CouponOrderLine> cashierCouponLines(List<CashierOrderRequest.CartItem> items, PricingQuoteVO pricing) {
        List<CouponOrderLine> lines = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            CashierOrderRequest.CartItem item = items.get(i);
            if (Integer.valueOf(1).equals(item.getIsGift())) continue;
            PricingQuoteVO.Line priced = pricing.getLines().get(i);
            lines.add(new CouponOrderLine(String.valueOf(i), item.getDishId(), priced.getDishName(),
                    item.getQuantity(), priced.getLinePayableAmount()));
        }
        return lines;
    }

    private CouponDiscountResultVO requireCashierCoupon(Long userId, List<Long> ids,
                                                          BigDecimal total, Long storeId,
                                                          List<CouponOrderLine> lines) {
        if (ids == null || ids.isEmpty()) return null;
        if (userId == null) throw new BusinessException("使用优惠券请先选择会员");
        Result result = calcCouponDiscountWithLines(userId, ids, total, storeId, lines);
        CouponDiscountResultVO quote = result == null ? null : (CouponDiscountResultVO) result.getData();
        if (quote == null || !Boolean.TRUE.equals(quote.getUsable())) {
            throw new BusinessException("优惠券不可用：" + (quote == null ? "试算失败" : quote.getReason()));
        }
        return quote;
    }

    private void setCashierCouponMetadata(RestaurantOrder order, List<Long> ids, CouponDiscountResultVO quote) {
        if (quote == null || ids == null || ids.isEmpty()) return;
        order.setUserCouponIds(ids.stream().map(String::valueOf).collect(Collectors.joining(",")));
        order.setCouponTypes(quote.getCouponDetails().stream()
                .map(CouponDiscountResultVO.CouponDetailVO::getCouponType).collect(Collectors.toList()));
    }

    /** 汇总已明确分配到订单项的菜品券与代金券优惠。 */
    private Map<String, BigDecimal> couponLineDiscounts(CouponDiscountResultVO quote) {
        Map<String, BigDecimal> discounts = new HashMap<>();
        if (quote == null || quote.getCouponDetails() == null) return discounts;
        for (CouponDiscountResultVO.CouponDetailVO detail : quote.getCouponDetails()) {
            if (detail.getDishItems() == null) continue;
            for (CouponDiscountResultVO.DishItemVO item : detail.getDishItems()) {
                if (item.getLineKey() != null && item.getCurrentPrice() != null)
                    discounts.merge(item.getLineKey(), item.getCurrentPrice(), BigDecimal::add);
            }
        }
        return discounts;
    }

    /** 先扣减命中菜品，再按剩余应付比例分摊整单优惠。 */
    private void distributeCashierPayable(Long orderId, BigDecimal payable, CouponDiscountResultVO quote) {
        List<RestaurantOrderItem> items = restaurantOrderItemService.list(
                new LambdaQueryWrapper<RestaurantOrderItem>().eq(RestaurantOrderItem::getOrderId, orderId)
                        .orderByAsc(RestaurantOrderItem::getId));
        List<RestaurantOrderItem> payableItems = items.stream()
                .filter(item -> !Integer.valueOf(1).equals(item.getIsGift()))
                .filter(item -> item.getLinePayableAmount() != null && item.getLinePayableAmount().signum() > 0)
                .collect(Collectors.toList());
        Map<String, BigDecimal> couponByLine = couponLineDiscounts(quote);
        Map<Long, BigDecimal> baseByItem = new HashMap<>();
        BigDecimal remainingBase = BigDecimal.ZERO;
        for (int i = 0; i < items.size(); i++) {
            RestaurantOrderItem item = items.get(i);
            if (Integer.valueOf(1).equals(item.getIsGift()) || item.getLinePayableAmount() == null
                    || item.getLinePayableAmount().signum() <= 0) continue;
            BigDecimal base = item.getLinePayableAmount()
                    .subtract(couponByLine.getOrDefault(String.valueOf(i), BigDecimal.ZERO)).max(BigDecimal.ZERO);
            baseByItem.put(item.getId(), base);
            remainingBase = remainingBase.add(base);
        }
        BigDecimal remainingPayable = payable;
        int lastPositive = -1;
        for (int i = 0; i < payableItems.size(); i++) {
            if (baseByItem.getOrDefault(payableItems.get(i).getId(), BigDecimal.ZERO).signum() > 0)
                lastPositive = i;
        }
        for (int i = 0; i < payableItems.size(); i++) {
            RestaurantOrderItem item = payableItems.get(i);
            BigDecimal previous = item.getLinePayableAmount();
            BigDecimal base = baseByItem.getOrDefault(item.getId(), previous);
            BigDecimal allocated = base.signum() == 0 ? BigDecimal.ZERO
                    : i == lastPositive ? remainingPayable
                    : remainingPayable.multiply(base).divide(remainingBase, 2, RoundingMode.HALF_UP);
            item.setLinePayableAmount(allocated);
            item.setDiscountAmount((item.getDiscountAmount() == null ? BigDecimal.ZERO
                    : item.getDiscountAmount()).add(previous.subtract(allocated)));
            remainingPayable = remainingPayable.subtract(allocated);
            remainingBase = remainingBase.subtract(base);
        }
        if (!payableItems.isEmpty()) restaurantOrderItemService.updateBatchById(payableItems);
    }

    /** 下单、加菜和结算试算共用同一套券规则。 */
    Result calcCouponDiscountWithLines(Long userId, List<Long> userCouponIds,
                                               BigDecimal orderTotalAmount, Long storeId,
                                               List<CouponOrderLine> orderLines) {
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

        if (userCouponIds.contains(null) || new HashSet<>(userCouponIds).size() != userCouponIds.size()) {
            return Result.success(CouponDiscountResultVO.builder().usable(false)
                    .reason("优惠券选择重复或参数错误").build());
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

            // 5. 菜品券先于整单券，避免整单折扣先耗尽可抵扣金额。
            List<UserCoupon> sortedUserCoupons = new ArrayList<>(userCoupons);
            sortedUserCoupons.sort((a, b) -> {
                Coupon ca = couponMap.get(a.getCouponId());
                Coupon cb = couponMap.get(b.getCouponId());
                return getCouponPriority(ca == null ? null : ca.getType())
                        - getCouponPriority(cb == null ? null : cb.getType());
            });

            // 6. 递减计算各优惠券的优惠金额（整单折扣基于前面券折扣后的剩余金额）
            BigDecimal remainingAmount = orderTotalAmount;
            BigDecimal totalDiscount = BigDecimal.ZERO;
            List<CouponDiscountResultVO.CouponDetailVO> couponDetails = new ArrayList<>();

            for (UserCoupon uc : sortedUserCoupons) {
                Coupon coupon = couponMap.get(uc.getCouponId());
                if (coupon == null) {
                    return Result.success(CouponDiscountResultVO.builder().usable(false)
                            .reason("优惠券信息不存在").build());
                }

                // 模板限制实时生效，结算试算与下单共用此处校验。
                if ((coupon.getStartTime() != null && now.isBefore(coupon.getStartTime()))
                        || (coupon.getEndTime() != null && now.isAfter(coupon.getEndTime()))) {
                    return Result.success(CouponDiscountResultVO.builder().usable(false)
                            .reason("优惠券「" + coupon.getName() + "」不在有效期内").build());
                }
                if (coupon.getStoreIds() != null && !coupon.getStoreIds().isBlank()
                        && (storeId == null || !List.of(coupon.getStoreIds().split(","))
                        .contains(String.valueOf(storeId)))) {
                    return Result.success(CouponDiscountResultVO.builder().usable(false)
                            .reason("优惠券「" + coupon.getName() + "」不适用于当前门店").build());
                }
                if (coupon.getUseWeekdays() != null && !coupon.getUseWeekdays().isBlank()
                        && !List.of(coupon.getUseWeekdays().split(","))
                        .contains(String.valueOf(now.getDayOfWeek().getValue()))) {
                    return Result.success(CouponDiscountResultVO.builder().usable(false)
                            .reason("优惠券「" + coupon.getName() + "」今天不可用").build());
                }
                if (org.hongtu.utils.CouponUnavailableDay.matches(coupon.getUnavailableDates(),
                        coupon.getUnavailableHolidays(), now.toLocalDate())) {
                    return Result.success(CouponDiscountResultVO.builder().usable(false)
                            .reason("优惠券「" + coupon.getName() + "」今天不可使用").build());
                }
                if (coupon.getUseStartTime() != null && coupon.getUseEndTime() != null
                        && (now.toLocalTime().isBefore(coupon.getUseStartTime())
                        || !now.toLocalTime().isBefore(coupon.getUseEndTime()))) {
                    return Result.success(CouponDiscountResultVO.builder().usable(false)
                            .reason("优惠券「" + coupon.getName() + "」不在可用时段").build());
                }

                // 最低消费按整单优惠前金额判断，所有券类型使用相同口径。
                BigDecimal minAmount = coupon.getMinAmount() == null ? BigDecimal.ZERO : coupon.getMinAmount();
                if (orderTotalAmount.compareTo(minAmount) < 0) {
                    return Result.success(CouponDiscountResultVO.builder().usable(false)
                            .reason("订单未达到优惠券「" + coupon.getName() + "」的最低消费金额").build());
                }

                // 计算优惠金额（根据券类型和剩余金额）
                CouponOrderLine freeLine = null;
                List<CouponDiscountResultVO.DishItemVO> voucherItems = null;
                BigDecimal discountAmount;
                if (coupon.getType().needDishItems()) {
                    freeLine = chooseFreeDishLine(coupon, orderLines);
                    if (freeLine == null) {
                        return Result.success(CouponDiscountResultVO.builder().usable(false)
                                .reason("订单中没有优惠券「" + coupon.getName() + "」适用的菜品").build());
                    }
                    discountAmount = coupon.getType() == CouponTypeEnum.DISH_FREE
                            ? freeLine.unitPayable.min(remainingAmount)
                            : freeLine.unitPayable.multiply(BigDecimal.ONE.subtract(coupon.getValue()))
                                    .setScale(2, RoundingMode.DOWN).min(remainingAmount);
                    freeLine.remainingQuantity--;
                    freeLine.remainingEligibleAmount = freeLine.remainingEligibleAmount.subtract(discountAmount)
                            .max(BigDecimal.ZERO);
                } else if (coupon.getType() == CouponTypeEnum.CASH_VOUCHER) {
                    List<CouponDishItem> allowed = FeignResultUtil.unwrap(couponFeignClient.getItemsByCouponId(coupon.getId()));
                    boolean scoped = allowed != null && !allowed.isEmpty();
                    if (orderLines == null && scoped) {
                        return Result.success(CouponDiscountResultVO.builder().usable(false)
                                .reason("代金券需要菜品明细才能校验适用范围").build());
                    }
                    if (orderLines == null) {
                        discountAmount = coupon.getValue().min(remainingAmount);
                    } else {
                        Set<Long> allowedIds = scoped ? allowed.stream().map(CouponDishItem::getDishId)
                                .collect(Collectors.toSet()) : Set.of();
                        BigDecimal eligibleAmount = orderLines.stream()
                                .filter(line -> !scoped || allowedIds.contains(line.dishId))
                                .map(line -> line.remainingEligibleAmount)
                                .reduce(BigDecimal.ZERO, BigDecimal::add).min(remainingAmount);
                        discountAmount = coupon.getValue().min(eligibleAmount);
                        voucherItems = new ArrayList<>();
                        BigDecimal unallocated = discountAmount;
                        for (CouponOrderLine line : orderLines) {
                            if ((scoped && !allowedIds.contains(line.dishId)) || unallocated.signum() <= 0) continue;
                            BigDecimal portion = line.remainingEligibleAmount.min(unallocated);
                            if (portion.signum() <= 0) continue;
                            voucherItems.add(CouponDiscountResultVO.DishItemVO.builder()
                                    .lineKey(line.lineKey).dishId(line.dishId).dishName(line.dishName)
                                    .currentPrice(portion).build());
                            line.remainingEligibleAmount = line.remainingEligibleAmount.subtract(portion);
                            unallocated = unallocated.subtract(portion);
                        }
                    }
                } else {
                    discountAmount = calculateCouponDiscountAmount(coupon, remainingAmount);
                }
                if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    return Result.success(CouponDiscountResultVO.builder().usable(false)
                            .reason("优惠券「" + coupon.getName() + "」当前无法产生优惠").build());
                }

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

                // 只返回本张券实际免掉的一份，供订单项按行精确分摊。
                if (freeLine != null) {
                    detailBuilder.dishItems(List.of(CouponDiscountResultVO.DishItemVO.builder()
                            .lineKey(freeLine.lineKey).dishId(freeLine.dishId)
                            .dishName(freeLine.dishName).currentPrice(discountAmount).build()));
                }
                if (voucherItems != null) detailBuilder.dishItems(voucherItems);

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
            case FULL_REDUCTION, CASH_VOUCHER -> {
                // 门槛已按优惠前整单金额检查，抵扣不能超过当前剩余应付。
                yield coupon.getValue() != null ? coupon.getValue().min(orderAmount) : BigDecimal.ZERO;
            }

            case ORDER_DISCOUNT -> {
                // 整单折扣券：订单金额 * (1 - 折扣率)
                // value 为折扣率，例如 0.8 表示8折，优惠金额 = orderAmount * (1 - 0.8) = orderAmount * 0.2
                BigDecimal discountRate = coupon.getValue() != null ? coupon.getValue() : BigDecimal.ONE;
                BigDecimal discountAmount = orderAmount.multiply(BigDecimal.ONE.subtract(discountRate));
                // 保留两位小数，向下取整
                yield discountAmount.setScale(2, RoundingMode.DOWN);
            }

            case DISH_FREE, DISH_DISCOUNT -> BigDecimal.ZERO;
        };
    }

    // ================== 退菜功能实现 ==================

    /**
     * 退菜。先落退款单，渠道明确成功后才修改订单项、库存和支付状态。
     */
    @Override
    public RefundDishVO refundDish(RefundDishRequest request) {
        OrderRefund refund = independentTx.execute(status -> stageRefund(request));
        if (refund.getStatus() == RefundStatusEnum.SUCCEEDED) {
            return refundResult(refund, refund.getAmount());
        }
        ChannelRefundState state = callRefundChannel(refund);
        RefundDishVO result = independentTx.execute(status -> applyRefundResult(refund.getId(), state));
        if (RefundStatusEnum.FAILED.getDesc().equals(result.getRefundStatus())) {
            throw new BusinessException("渠道退款失败，请稍后使用同一退菜请求重试");
        }
        return result;
    }

    /** 补偿仍未完成的退款单。渠道成功后才提交业务。 */
    @Override
    public void reconcileOpenRefunds() {
        List<OrderRefund> open = TenantIgnore.get(() -> orderRefundMapper.selectList(
                new LambdaQueryWrapper<OrderRefund>()
                        .in(OrderRefund::getStatus, RefundStatusEnum.REQUESTED, RefundStatusEnum.PROCESSING)
                        .orderByAsc(OrderRefund::getUpdatedAt)
                        .last("LIMIT 20")));
        for (OrderRefund refund : open) {
            try {
                TenantScope.run(refund.getTenantId(), () -> {
                    ChannelRefundState state = callRefundChannel(refund);
                    independentTx.execute(status -> applyRefundResult(refund.getId(), state));
                });
            } catch (RuntimeException exception) {
                log.warn("退款补偿失败: refundNo={}", refund.getRefundNo(), exception);
            }
        }
    }

    /** 支付服务验签后，餐厅服务按退款单归属和金额完成入账。 */
    @Override
    public void applyVerifiedRefundNotify(Long tenantId, PaymentRefundNotifyResult parsed) {
        if (tenantId == null || parsed == null || !Boolean.TRUE.equals(parsed.getMerchantMatched())
                || StrUtil.isBlank(parsed.getOutRefundNo()) || StrUtil.isBlank(parsed.getOutTradeNo())) {
            throw new BusinessException("退款回调缺少原订单或退款单");
        }
        TenantScope.run(tenantId, () -> {
            OrderRefund refund = orderRefundMapper.selectOne(new LambdaQueryWrapper<OrderRefund>()
                    .eq(OrderRefund::getRefundNo, parsed.getOutRefundNo()).last("LIMIT 1"));
            if (refund == null || !tenantId.equals(refund.getTenantId())
                    || !parsed.getOutTradeNo().equals(refund.getOrderNo())) {
                throw new BusinessException("退款回调与退款单不匹配");
            }
            if (parsed.getRefundAmount() != null) {
                CashierAmountGuard.requireClientAmountMatches(parsed.getRefundAmount(), refund.getAmount());
            }
            ChannelRefundState state = channelRefundState(parsed.getStatus());
            if (StrUtil.isNotBlank(parsed.getChannelRefundId())) {
                refund.setChannelRefundId(parsed.getChannelRefundId());
                orderRefundMapper.updateById(refund);
            }
            independentTx.execute(status -> applyRefundResult(refund.getId(), state));
        });
    }

    private boolean closeUnpaidOrder(Long orderId, String reason, boolean timeout) {
        RestaurantOrder snapshot = independentTx.execute(status -> {
            RestaurantOrder order = lockOrderForMutation(orderId);
            if (order == null) {
                throw new BusinessException(ORDER_NOT_EXIST.getCode(), "订单不存在");
            }
            if (order.getPaymentStatus() == PaymentStatusEnum.PAID
                    || order.getPaymentStatus() == PaymentStatusEnum.PARTIAL_REFUND
                    || order.getPaymentStatus() == PaymentStatusEnum.PAID_PENDING) {
                throw new BusinessException(ORDER_CANCEL_FAIL.getCode(), "已支付的订单不能取消，请先退款");
            }
            if (order.getPaymentStatus() == PaymentStatusEnum.RECONCILING) {
                throw new BusinessException("订单对账中，请稍后再试");
            }
            boolean allowed = timeout
                    ? PaymentTransitionPolicy.canTimeout(order.getStatus(), order.getPaymentStatus())
                    : PaymentTransitionPolicy.canCancel(order.getStatus(), order.getPaymentStatus(),
                            order.getPaymentMode());
            if (!allowed) {
                throw new BusinessException(ORDER_CANCEL_FAIL.getCode(), timeout
                        ? "订单已完成或已取消，无法超时关闭" : "仅线上未支付且尚未生效的订单可自行取消");
            }
            return order;
        });
        PaymentTransitionPolicy.Close close = resolveChannelClose(snapshot);
        if (close == PaymentTransitionPolicy.Close.PAID) {
            handlePaymentSuccess(snapshot.getOrderNo(), null, snapshot.getPaymentType(), null, snapshot.getTenantId());
            throw new BusinessException(ORDER_CANCEL_FAIL.getCode(), "订单已支付，不能取消，请走退款");
        }
        if (close == PaymentTransitionPolicy.Close.UNKNOWN) {
            return Boolean.TRUE.equals(independentTx.execute(status ->
                    (timeout ? baseMapper.markReconcilingIfUnpaid(orderId)
                            : baseMapper.markCustomerReconcilingIfUnpaid(orderId)) == 1));
        }
        RestaurantOrder closed = independentTx.execute(status -> finishUnpaidClose(orderId, reason, timeout));
        if (closed == null) {
            return false;
        }
        restoreCouponsAfterClose(closed);
        try {
            orderTimeoutQueueService.removeFromTimeoutQueue(orderId);
        } catch (RuntimeException exception) {
            log.error("从超时队列移除订单失败: orderId={}", orderId, exception);
        }
        if (StrUtil.isNotBlank(closed.getSessionNo())) {
            try {
                webSocketPushService.pushOrderUpdated(closed.getSessionNo(),
                        restaurantOrderConverter.toVO(this.getById(orderId)));
            } catch (RuntimeException exception) {
                log.warn("推送桌台订单取消状态失败: orderId={}", orderId, exception);
            }
        }
        return true;
    }

    private RestaurantOrder finishUnpaidClose(Long orderId, String reason, boolean timeout) {
        RestaurantOrder order = lockOrderForMutation(orderId);
        if (order == null) {
            return null;
        }
        boolean allowed = timeout
                ? PaymentTransitionPolicy.canTimeout(order.getStatus(), order.getPaymentStatus())
                : PaymentTransitionPolicy.canCancel(order.getStatus(), order.getPaymentStatus(),
                        order.getPaymentMode());
        if (!allowed) {
            return null;
        }
        restoreReservedOrderStock(order);
        int updated = timeout
                ? baseMapper.markTimeoutIfUnpaid(orderId, reason, LocalDateTime.now())
                : baseMapper.markCustomerClosedIfUnpaid(orderId, reason, LocalDateTime.now());
        if (updated != 1) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
        }
        if (StrUtil.isNotBlank(order.getSessionNo())) {
            if (order.getPaymentMode() == PaymentModeEnum.ONLINE) {
                // 线上支付一桌一单：未付款订单取消后结束本次会话并释放桌位。
                TableSession session = tableSessionService.getOne(new LambdaQueryWrapper<TableSession>()
                        .eq(TableSession::getSessionNo, order.getSessionNo()));
                if (session != null && session.getStatus() == SessionStatusEnum.IN_PROGRESS) {
                    tableSessionService.updateSessionStatistics(order.getSessionNo());
                    tableSessionService.endSession(order.getSessionNo(), BigDecimal.ZERO);
                    pushSessionEndedAfterCommit(order.getSessionNo(), BigDecimal.ZERO);
                }
            } else {
                // 线下支付会话可在取消当前订单后继续点餐。
                tableSessionService.update(new LambdaUpdateWrapper<TableSession>()
                        .eq(TableSession::getSessionNo, order.getSessionNo())
                        .eq(TableSession::getCurrentOrderId, orderId)
                        .set(TableSession::getCurrentOrderId, null)
                        .set(TableSession::getUpdatedAt, LocalDateTime.now()));
                tableSessionService.updateSessionStatistics(order.getSessionNo());
            }
        }
        return order;
    }

    private void restoreCouponsAfterClose(RestaurantOrder order) {
        if (StrUtil.isBlank(order.getUserCouponIds())) {
            return;
        }
        List<Long> couponIds = java.util.Arrays.stream(order.getUserCouponIds().split(","))
                .filter(StrUtil::isNotBlank).map(String::trim).map(Long::valueOf).toList();
        restoreCouponsWithCompensation(couponIds, order.getId());
    }

    private PaymentTransitionPolicy.Close resolveChannelClose(RestaurantOrder order) {
        if (order.getPaymentType() != PaymentTypeEnum.WECHAT && order.getPaymentType() != PaymentTypeEnum.ALIPAY) {
            return PaymentTransitionPolicy.Close.CLOSED;
        }
        try {
            Map<String, Object> result = order.getPaymentType() == PaymentTypeEnum.WECHAT
                    ? FeignResultUtil.unwrap(payFeignClient.closeWeChatTrade(order.getTenantId(), order.getOrderNo()))
                    : FeignResultUtil.unwrap(payFeignClient.closeAlipayTrade(order.getTenantId(), order.getOrderNo()));
            PaymentTransitionPolicy.Close outcome = PaymentTransitionPolicy.parseOutcome(stringValue(result.get("outcome")));
            if (outcome != PaymentTransitionPolicy.Close.UNKNOWN) {
                return outcome;
            }
        } catch (BusinessException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("未配置")) {
                throw exception;
            }
            return queryChannelClose(order);
        } catch (RuntimeException exception) {
            return queryChannelClose(order);
        }
        return queryChannelClose(order);
    }

    private PaymentTransitionPolicy.Close queryChannelClose(RestaurantOrder order) {
        try {
            if (order.getPaymentType() == PaymentTypeEnum.WECHAT) {
                Map<String, Object> result = FeignResultUtil.unwrap(
                        payFeignClient.queryWeChatTrade(order.getTenantId(), order.getOrderNo()));
                if (!Boolean.TRUE.equals(result.get("success"))) {
                    return PaymentTransitionPolicy.Close.UNKNOWN;
                }
                return PaymentTransitionPolicy.interpretWeChatTradeState(stringValue(result.get("tradeState")));
            }
            Map<String, Object> result = FeignResultUtil.unwrap(
                    payFeignClient.queryAlipayTrade(order.getTenantId(), order.getOrderNo()));
            return PaymentTransitionPolicy.interpretAlipayTrade(Boolean.TRUE.equals(result.get("success")),
                    stringValue(result.get("tradeStatus")), stringValue(result.get("subCode")));
        } catch (RuntimeException exception) {
            log.warn("关单后查单失败: orderNo={}", order.getOrderNo(), exception);
            return PaymentTransitionPolicy.Close.UNKNOWN;
        }
    }

    private boolean capturePayment(Long orderId, String transactionId, PaymentTypeEnum paymentType, BigDecimal notifiedAmount) {
        try {
            CaptureOutcome outcome = independentTx.execute(status -> captureInTransaction(
                    status, orderId, transactionId, paymentType, notifiedAmount));
            if (outcome != null && outcome.paidOrder != null) {
                try {
                    applyPaidSideEffects(outcome.paidOrder);
                } catch (RuntimeException exception) {
                    log.error("支付副作用失败，订单已入账: orderId={}", outcome.paidOrder.getId(), exception);
                }
            }
            if (outcome != null && outcome.lateRefund != null
                    && outcome.lateRefund.getStatus() != RefundStatusEnum.SUCCEEDED) {
                ChannelRefundState state = callRefundChannel(outcome.lateRefund);
                independentTx.execute(status -> applyRefundResult(outcome.lateRefund.getId(), state));
            }
            return true;
        } catch (StockCaptureException exception) {
            independentTx.execute(status -> {
                RestaurantOrder order = lockOrderForMutation(orderId);
                if (order == null) {
                    return false;
                }
                BigDecimal payable = CashierAmountGuard.requireServerPayable(order.getActualAmount(), order.getTotalAmount());
                baseMapper.markPaidPendingIfCapturable(orderId,
                        paymentType == null ? null : paymentType.getCode(), transactionId, LocalDateTime.now(), payable);
                return true;
            });
            return true;
        }
    }

    private CaptureOutcome captureInTransaction(org.springframework.transaction.TransactionStatus status, Long orderId,
                                                String transactionId, PaymentTypeEnum paymentType, BigDecimal notifiedAmount) {
        RestaurantOrder order = lockOrderForMutation(orderId);
        if (order == null) {
            return new CaptureOutcome(null, null);
        }
        if (order.getPaymentType() != null && paymentType != null && order.getPaymentType() != paymentType) {
            throw new BusinessException("支付回调渠道与订单支付渠道不一致");
        }
        BigDecimal payable = CashierAmountGuard.requireServerPayable(order.getActualAmount(), order.getTotalAmount());
        if (notifiedAmount != null) {
            CashierAmountGuard.requireClientAmountMatches(notifiedAmount, payable);
        }
        PaymentTransitionPolicy.Capture decision = PaymentTransitionPolicy.decide(order.getStatus(), order.getPaymentStatus());
        if (decision == PaymentTransitionPolicy.Capture.ALREADY) {
            return new CaptureOutcome(null, null);
        }
        if (decision == PaymentTransitionPolicy.Capture.REFUND) {
            if (paymentType != null) {
                order.setPaymentType(paymentType);
            }
            return new CaptureOutcome(null, stageLateRefund(order, payable));
        }
        try {
            if (!Boolean.TRUE.equals(order.getStockReserved())) {
                reserveOrderStock(order);
            }
            commitOrderSales(order);
            int updated = baseMapper.markPaidIfCapturable(orderId,
                    paymentType == null ? null : paymentType.getCode(), transactionId, LocalDateTime.now(), payable);
            if (updated != 1) {
                throw new BusinessException("订单状态已变化");
            }
            order.setPaymentStatus(PaymentStatusEnum.PAID);
            order.setPaymentType(paymentType);
            order.setPaymentNo(transactionId);
            order.setActualAmount(payable);
            order.setSalesCommitted(true);
            order.setStockReserved(true);
            return new CaptureOutcome(order, null);
        } catch (BusinessException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("库存")) {
                status.setRollbackOnly();
                throw new StockCaptureException();
            }
            throw exception;
        }
    }

    private OrderRefund stageLateRefund(RestaurantOrder order, BigDecimal amount) {
        String refundNo = "LATE" + order.getId();
        OrderRefund existing = orderRefundMapper.selectOne(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getRefundNo, refundNo).last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        OrderRefund refund = new OrderRefund();
        refund.setTenantId(order.getTenantId());
        refund.setStoreId(order.getStoreId());
        refund.setOrderId(order.getId());
        refund.setOrderNo(order.getOrderNo());
        refund.setRefundNo(refundNo);
        refund.setIdempotencyKey("LATE");
        refund.setChannel(order.getPaymentType());
        refund.setAmount(amount);
        refund.setStatus(RefundStatusEnum.REQUESTED);
        refund.setReason("订单取消后支付成功自动退款");
        refund.setCreatedAt(LocalDateTime.now());
        refund.setUpdatedAt(LocalDateTime.now());
        orderRefundMapper.insert(refund);
        return refund;
    }

    private static final class CaptureOutcome {
        private final RestaurantOrder paidOrder;
        private final OrderRefund lateRefund;

        private CaptureOutcome(RestaurantOrder paidOrder, OrderRefund lateRefund) {
            this.paidOrder = paidOrder;
            this.lateRefund = lateRefund;
        }
    }

    private OrderRefund stageRefund(RefundDishRequest request) {
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new BusinessException("退菜请求缺少幂等键");
        }
        RestaurantOrder order = lockOrderForMutation(request.getOrderId());
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        validateOrderStatusForRefund(order);
        String refundNo = buildRefundRequestNo(order, request);
        OrderRefund existing = orderRefundMapper.selectOne(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getOrderId, order.getId())
                .eq(OrderRefund::getIdempotencyKey, request.getIdempotencyKey())
                .last("LIMIT 1"));
        if (existing != null) {
            if (existing.getStatus() == RefundStatusEnum.FAILED) {
                existing.setStatus(RefundStatusEnum.REQUESTED);
                existing.setUpdatedAt(LocalDateTime.now());
                orderRefundMapper.updateById(existing);
            }
            return existing;
        }
        List<RestaurantOrderItem> orderItems = restaurantOrderItemService.list(
                new LambdaQueryWrapper<RestaurantOrderItem>().eq(RestaurantOrderItem::getOrderId, order.getId()));
        Map<Long, RestaurantOrderItem> orderItemMap = orderItems.stream()
                .collect(Collectors.toMap(RestaurantOrderItem::getId, item -> item));
        Map<Long, Integer> pending = pendingRefundQuantities(order.getId());
        BigDecimal total = BigDecimal.ZERO;
        List<OrderRefundItem> details = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (RefundDishRequest.RefundItem refundItem : request.getRefundItems()) {
            if (refundItem.getOrderItemId() == null || refundItem.getRefundQuantity() == null
                    || refundItem.getRefundQuantity() <= 0 || !seen.add(refundItem.getOrderItemId())) {
                throw new IllegalArgumentException("退菜项和退菜数量必须有效");
            }
            RestaurantOrderItem orderItem = orderItemMap.get(refundItem.getOrderItemId());
            if (orderItem == null) {
                throw new IllegalArgumentException("订单项不存在: " + refundItem.getOrderItemId());
            }
            int available = orderItem.getQuantity() - pending.getOrDefault(orderItem.getId(), 0);
            if (refundItem.getRefundQuantity() > available) {
                throw new IllegalArgumentException("退菜数量不能超过可退数量");
            }
            if (orderItem.getLinePayableAmount() == null || orderItem.getQuantity() == null || orderItem.getQuantity() <= 0) {
                throw new BusinessException("订单项金额异常，禁止退菜");
            }
            BigDecimal refundAmount = refundItem.getRefundQuantity().equals(orderItem.getQuantity())
                    ? orderItem.getLinePayableAmount()
                    : orderItem.getLinePayableAmount()
                    .multiply(BigDecimal.valueOf(refundItem.getRefundQuantity()))
                    .divide(BigDecimal.valueOf(orderItem.getQuantity()), 2, RoundingMode.HALF_UP);
            if (refundItem.getRefundAmount() != null) {
                CashierAmountGuard.requireClientAmountMatches(refundItem.getRefundAmount(), refundAmount);
            }
            total = total.add(refundAmount);
            OrderRefundItem detail = new OrderRefundItem();
            detail.setTenantId(order.getTenantId());
            detail.setOrderItemId(orderItem.getId());
            detail.setDishId(orderItem.getDishId());
            detail.setQuantity(refundItem.getRefundQuantity());
            detail.setAmount(refundAmount);
            detail.setCreatedAt(LocalDateTime.now());
            details.add(detail);
        }
        OrderRefund refund = new OrderRefund();
        refund.setTenantId(order.getTenantId());
        refund.setStoreId(order.getStoreId());
        refund.setOrderId(order.getId());
        refund.setOrderNo(order.getOrderNo());
        refund.setRefundNo(refundNo);
        refund.setIdempotencyKey(request.getIdempotencyKey());
        refund.setChannel(order.getPaymentType() == null ? PaymentTypeEnum.CASH : order.getPaymentType());
        refund.setAmount(total);
        refund.setStatus(RefundStatusEnum.REQUESTED);
        refund.setReason(request.getRefundReason());
        refund.setOperatorId(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null);
        refund.setOperatorName(resolveOperatorName(refund.getOperatorId()));
        refund.setCreatedAt(LocalDateTime.now());
        refund.setUpdatedAt(LocalDateTime.now());
        try {
            orderRefundMapper.insert(refund);
        } catch (DuplicateKeyException exception) {
            OrderRefund raced = orderRefundMapper.selectOne(new LambdaQueryWrapper<OrderRefund>()
                    .eq(OrderRefund::getOrderId, order.getId())
                    .eq(OrderRefund::getIdempotencyKey, request.getIdempotencyKey())
                    .last("LIMIT 1"));
            return raced != null ? raced : orderRefundMapper.selectOne(new LambdaQueryWrapper<OrderRefund>()
                    .eq(OrderRefund::getRefundNo, refundNo).last("LIMIT 1"));
        }
        for (OrderRefundItem detail : details) {
            detail.setRefundId(refund.getId());
            orderRefundItemMapper.insert(detail);
        }
        return refund;
    }

    private Map<Long, Integer> pendingRefundQuantities(Long orderId) {
        List<OrderRefund> open = orderRefundMapper.selectList(new LambdaQueryWrapper<OrderRefund>()
                .eq(OrderRefund::getOrderId, orderId)
                .in(OrderRefund::getStatus, RefundStatusEnum.REQUESTED, RefundStatusEnum.PROCESSING));
        if (open.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = open.stream().map(OrderRefund::getId).toList();
        return orderRefundItemMapper.selectList(new LambdaQueryWrapper<OrderRefundItem>()
                        .in(OrderRefundItem::getRefundId, ids)).stream()
                .collect(Collectors.toMap(OrderRefundItem::getOrderItemId, OrderRefundItem::getQuantity, Integer::sum));
    }

    private ChannelRefundState callRefundChannel(OrderRefund refund) {
        RestaurantOrder order = this.getById(refund.getOrderId());
        if (order == null) {
            return ChannelRefundState.FAILED;
        }
        boolean captured = order.getPaymentStatus() == PaymentStatusEnum.PAID
                || order.getPaymentStatus() == PaymentStatusEnum.PARTIAL_REFUND
                || order.getPaymentStatus() == PaymentStatusEnum.TIMEOUT
                || order.getPaymentStatus() == PaymentStatusEnum.CLOSED
                || order.getPaymentStatus() == PaymentStatusEnum.PAID_PENDING;
        PaymentTypeEnum paymentType = refund.getChannel();
        if (!captured || paymentType == PaymentTypeEnum.CASH || paymentType == PaymentTypeEnum.BANK_CARD
                || paymentType == PaymentTypeEnum.OFFLINE_PAY || paymentType == PaymentTypeEnum.COUPON_PAY) {
            return ChannelRefundState.SUCCESS;
        }
        if (paymentType == PaymentTypeEnum.BANLANCE_PAY) {
            return ChannelRefundState.BALANCE;
        }
        if (paymentType == PaymentTypeEnum.WECHAT) {
            return callWeChatRefund(order, refund);
        }
        if (paymentType == PaymentTypeEnum.ALIPAY) {
            return callAlipayRefund(order, refund);
        }
        throw new BusinessException("当前支付方式尚未接入退款，禁止修改本地支付状态");
    }

    private ChannelRefundState callWeChatRefund(RestaurantOrder order, OrderRefund refund) {
        Map<String, Object> trade = FeignResultUtil.unwrap(
                payFeignClient.queryWeChatTrade(order.getTenantId(), order.getOrderNo()));
        if (!Boolean.TRUE.equals(trade.get("success")) || !"SUCCESS".equals(String.valueOf(trade.get("tradeState")))) {
            throw new BusinessException("微信支付原交易状态异常，无法退款");
        }
        Object total = trade.get("total");
        if (!(total instanceof Number totalNumber)) {
            throw new BusinessException("微信支付原交易金额缺失，无法退款");
        }
        Map<String, Object> current = FeignResultUtil.unwrap(
                payFeignClient.queryWeChatRefund(order.getTenantId(), refund.getRefundNo()));
        if (!Boolean.TRUE.equals(current.get("success"))) {
            current = FeignResultUtil.unwrap(payFeignClient.refundRestaurantWeChatTrade(
                    order.getTenantId(), order.getOrderNo(), refund.getRefundNo(), toCentString(refund.getAmount()),
                    String.valueOf(totalNumber.longValue()), StrUtil.blankToDefault(refund.getReason(), "订单退菜退款")));
        }
        if (!Boolean.TRUE.equals(current.get("success"))) {
            return ChannelRefundState.FAILED;
        }
        if (current.get("refundId") != null) {
            refund.setChannelRefundId(String.valueOf(current.get("refundId")));
        }
        return channelRefundState(String.valueOf(current.get("status")));
    }

    private ChannelRefundState callAlipayRefund(RestaurantOrder order, OrderRefund refund) {
        Map<String, Object> current = FeignResultUtil.unwrap(payFeignClient.refundAlipayTrade(
                order.getTenantId(), order.getOrderNo(), refund.getRefundNo(),
                refund.getAmount().setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                StrUtil.blankToDefault(refund.getReason(), "订单退菜退款")));
        if (!Boolean.TRUE.equals(current.get("success"))) {
            return ChannelRefundState.FAILED;
        }
        if (current.get("tradeNo") != null) {
            refund.setChannelRefundId(String.valueOf(current.get("tradeNo")));
        }
        return ChannelRefundState.SUCCESS;
    }

    private RefundDishVO applyRefundResult(Long refundId, ChannelRefundState state) {
        OrderRefund refund = orderRefundMapper.selectById(refundId);
        if (refund == null) {
            throw new BusinessException("退款单不存在");
        }
        if (refund.getStatus() == RefundStatusEnum.SUCCEEDED) {
            return refundResult(refund, refund.getAmount());
        }
        if (state == ChannelRefundState.PROCESSING) {
            orderRefundMapper.compareAndSetOpen(refundId, RefundStatusEnum.PROCESSING.getCode(), refund.getChannelRefundId());
            RefundDishVO processing = refundResult(refund, null);
            processing.setRefundStatus(RefundStatusEnum.PROCESSING.getDesc());
            return processing;
        }
        if (state == ChannelRefundState.FAILED) {
            orderRefundMapper.compareAndSetOpen(refundId, RefundStatusEnum.FAILED.getCode(), refund.getChannelRefundId());
            RefundDishVO failed = refundResult(refund, null);
            failed.setRefundStatus(RefundStatusEnum.FAILED.getDesc());
            return failed;
        }
        if (state == ChannelRefundState.BALANCE) {
            RestaurantOrder order = lockOrderForMutation(refund.getOrderId());
            if (order.getUserId() == null) {
                throw new BusinessException("余额支付订单缺少用户信息，无法退款");
            }
            Long operatorId = StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : order.getUserId();
            changeBalanceDurable(order.getId(), BalanceChangeDTO.builder()
                    .userId(order.getUserId()).amount(refund.getAmount()).type(3)
                    .orderNo(refund.getRefundNo()).sourceOrderNo(order.getOrderNo())
                    .operateUserId(operatorId).build());
        }
        if (orderRefundMapper.compareAndSetOpen(refundId, RefundStatusEnum.SUCCEEDED.getCode(), refund.getChannelRefundId()) != 1) {
            OrderRefund current = orderRefundMapper.selectById(refundId);
            return refundResult(current, current.getAmount());
        }
        return commitRefundBusiness(refund);
    }

    private RefundDishVO commitRefundBusiness(OrderRefund refund) {
        RestaurantOrder order = lockOrderForMutation(refund.getOrderId());
        List<OrderRefundItem> details = orderRefundItemMapper.selectList(new LambdaQueryWrapper<OrderRefundItem>()
                .eq(OrderRefundItem::getRefundId, refund.getId()));
        Map<Long, Integer> stock = new HashMap<>();
        List<RefundDishVO.RefundItemDetail> viewItems = new ArrayList<>();
        for (OrderRefundItem detail : details) {
            RestaurantOrderItem orderItem = restaurantOrderItemService.getById(detail.getOrderItemId());
            if (orderItem == null) {
                continue;
            }
            recordRefundDishChange(order, orderItem, detail.getQuantity(), detail.getAmount(), refund.getReason(), refund.getRefundNo(), refund.getOperatorId(), refund.getOperatorName());
            int remaining = orderItem.getQuantity() - detail.getQuantity();
            if (remaining <= 0) {
                restaurantOrderItemService.removeById(orderItem.getId());
            } else {
                orderItem.setQuantity(remaining);
                orderItem.setLinePayableAmount(orderItem.getLinePayableAmount().subtract(detail.getAmount()));
                orderItem.setUpdatedAt(LocalDateTime.now());
                restaurantOrderItemService.updateById(orderItem);
            }
            stock.merge(detail.getDishId(), detail.getQuantity(), Integer::sum);
            RefundDishVO.RefundItemDetail view = new RefundDishVO.RefundItemDetail();
            view.setOrderItemId(detail.getOrderItemId());
            view.setDishId(detail.getDishId());
            view.setDishName(getDishName(detail.getDishId()));
            view.setRefundQuantity(detail.getQuantity());
            view.setRemainingQuantity(Math.max(remaining, 0));
            view.setRefundAmount(detail.getAmount());
            viewItems.add(view);
        }
        if ("LATE".equals(refund.getIdempotencyKey())) {
            baseMapper.markRefunded(order.getId(), PaymentStatusEnum.FULL_REFUND.getCode(),
                    0, 0, order.getStatus().getCode(), order.getCancelReason(), order.getCancelTime());
            RefundDishVO late = refundResult(refund, BigDecimal.ZERO);
            late.setRefundStatus(RefundStatusEnum.SUCCEEDED.getDesc());
            return late;
        }
        boolean captured = order.getPaymentStatus() == PaymentStatusEnum.PAID
                || order.getPaymentStatus() == PaymentStatusEnum.PARTIAL_REFUND
                || order.getPaymentStatus() == PaymentStatusEnum.TIMEOUT
                || order.getPaymentStatus() == PaymentStatusEnum.CLOSED
                || order.getPaymentStatus() == PaymentStatusEnum.PAID_PENDING;
        if (!stock.isEmpty()) {
            if (captured && Boolean.TRUE.equals(order.getSalesCommitted())) {
                dishService.batchIncreaseStockAndDecreaseSales(order.getStoreId(), stock);
            } else if (Boolean.TRUE.equals(order.getStockReserved())) {
                dishService.batchReleaseReservedStock(order.getStoreId(), stock);
            }
        }
        RestaurantOrder recalculated = details.isEmpty() ? order : recalculateOrderAmountsFromItems(order.getId());
        BigDecimal remainingAmount = recalculated == null || recalculated.getActualAmount() == null
                ? BigDecimal.ZERO : recalculated.getActualAmount();
        boolean full = remainingAmount.compareTo(BigDecimal.ZERO) <= 0;
        if (captured) {
            int stockFlag = full ? 0 : (Boolean.TRUE.equals(order.getStockReserved()) ? 1 : 0);
            int salesFlag = full ? 0 : (Boolean.TRUE.equals(order.getSalesCommitted()) ? 1 : 0);
            baseMapper.markRefunded(order.getId(),
                    (full ? PaymentStatusEnum.FULL_REFUND : PaymentStatusEnum.PARTIAL_REFUND).getCode(),
                    stockFlag, salesFlag,
                    full ? OrderStatusEnum.CANCELLED.getCode() : order.getStatus().getCode(),
                    full ? "退菜后订单金额为0，自动取消" : order.getCancelReason(),
                    full ? LocalDateTime.now() : order.getCancelTime());
            if (order.getUserId() != null)
                localOutboxService.enqueueMemberReward(order.getId(), "MEMBER_REWARD:" + order.getId() + ":REFUND:" + refund.getId());
        } else if (full) {
            baseMapper.markClosedIfUnpaid(order.getId(), "退菜后订单金额为0，自动取消", LocalDateTime.now());
        }
        RefundDishVO result = refundResult(refund, remainingAmount);
        result.setRefundItems(viewItems);
        result.setRefundStatus(RefundStatusEnum.SUCCEEDED.getDesc());
        if (order.getSessionNo() != null) {
            String sessionNo = order.getSessionNo();
            Long tenantId = order.getTenantId();
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() {
                            TenantScope.run(tenantId, () -> {
                                try {
                                    webSocketPushService.pushOrderUpdated(sessionNo,
                                            restaurantOrderConverter.toVO(RestaurantOrderServiceImpl.this.getById(order.getId())));
                                } catch (Exception e) {
                                    log.error("推送退菜结果失败: orderId={}", order.getId(), e);
                                }
                            });
                        }
                    });
        }
        return result;
    }

    private RefundDishVO refundResult(OrderRefund refund, BigDecimal remaining) {
        RefundDishVO result = new RefundDishVO();
        result.setOrderId(refund.getOrderId());
        result.setOrderNo(refund.getOrderNo());
        result.setTotalRefundAmount(refund.getAmount());
        result.setNewOrderTotalPrice(remaining);
        result.setRefundReason(refund.getReason());
        result.setRefundTime(LocalDateTime.now());
        result.setRefundNo(refund.getRefundNo());
        result.setRefundStatus(refund.getStatus() == null ? null : refund.getStatus().getDesc());
        return result;
    }

    private ChannelRefundState channelRefundState(String status) {
        if ("SUCCESS".equals(status)) {
            return ChannelRefundState.SUCCESS;
        }
        if ("PROCESSING".equals(status)) {
            return ChannelRefundState.PROCESSING;
        }
        return ChannelRefundState.FAILED;
    }

    private enum ChannelRefundState {
        SUCCESS, PROCESSING, FAILED, BALANCE
    }

    private static final class StockCaptureException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    /** 根据退菜明细生成稳定的渠道退款单号。 */
    private String buildRefundRequestNo(RestaurantOrder order, RefundDishRequest request) {
        String source = order.getTenantId() + ":" + order.getId() + ":" + request.getIdempotencyKey();
        return "RF" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

    /** 微信支付金额使用分，转换时不允许静默舍入。 */
    private String toCentString(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** 匿名支付回调仅按全局唯一订单号定位一次，定位后立即恢复订单租户上下文。 */
    private RestaurantOrder findOrderByOrderNoAcrossTenants(String orderNo) {
        if (StrUtil.isBlank(orderNo)) {
            return null;
        }
        LambdaQueryWrapper<RestaurantOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RestaurantOrder::getOrderNo, orderNo);
        return TenantContext.hasTenant()
                ? this.getOne(wrapper)
                : TenantIgnore.get(() -> this.getOne(wrapper));
    }

    /** 支付渠道附加字段只携带服务端生成的租户和订单标识。 */
    private String paymentAttach(RestaurantOrder order) {
        return "LITESHOP:" + order.getTenantId() + ":" + order.getId();
    }

    /** 生产支付不允许使用占位地址或明文 HTTP 回调。 */
    private String requireSecureNotifyUrl(String configuredUrl, String provider) {
        if (StrUtil.isBlank(configuredUrl) || configuredUrl.contains("your-domain")
                || !configuredUrl.startsWith("https://")) {
            throw new BusinessException(provider + "支付回调地址未配置为 HTTPS 公网地址");
        }
        return configuredUrl;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RestaurantOrderVO markOrderItemsGift(MarkOrderItemsGiftRequest request) {
        RestaurantOrder order = lockOrderForMutation(request.getOrderId());
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
            BigDecimal lineOriginalTotal = item.getUnitListPrice() != null
                    ? item.getUnitListPrice().multiply(new BigDecimal(item.getQuantity()))
                    : (item.getLinePayableAmount() != null ? item.getLinePayableAmount() : BigDecimal.ZERO);
            if (request.getIsGift() == 1) {
                item.setIsGift(1);
                item.setDiscountAmount(lineOriginalTotal);
                item.setLinePayableAmount(BigDecimal.ZERO); // 该行实付=0
                deltaAmount = deltaAmount.add(lineOriginalTotal);
            } else {
                item.setIsGift(0);
                item.setDiscountAmount(BigDecimal.ZERO);
                item.setLinePayableAmount(lineOriginalTotal); // 恢复该行正常售价小计
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
     * 记录本批加菜。每个新订单项只有一个 ADD 事件，事件键用于避免重复写入。
     */
    private void recordAddDishChanges(RestaurantOrder order, List<RestaurantOrderItem> items) {
        for (RestaurantOrderItem item : items) {
            OrderDishChangeLog change = new OrderDishChangeLog()
                    .setEventKey("ADD:" + item.getId())
                    .setOrderId(order.getId())
                    .setOrderItemId(item.getId())
                    .setStoreId(order.getStoreId())
                    .setDishId(item.getDishId())
                    .setCategoryId(item.getCategoryId())
                    .setDishName(getDishName(item.getDishId()))
                    .setChangeType(1)
                    .setQuantity(item.getQuantity())
                    .setAmount(item.getLinePayableAmount() == null ? BigDecimal.ZERO : item.getLinePayableAmount())
                    .setReason("追加菜品")
                    .setOperatorId(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null)
                    .setOperatorName(resolveOperatorName(StpUtil.isLogin() ? StpUtil.getLoginIdAsLong() : null))
                    .setOccurredAt(item.getAddTime() == null ? LocalDateTime.now() : item.getAddTime());
            orderDishChangeLogMapper.insert(change);
        }
    }

    /**
     * 记录一次退菜事件。退菜可能对同一订单项发生多次，因此使用随机事件键。
     */
    private void recordRefundDishChange(RestaurantOrder order, RestaurantOrderItem item,
                                        Integer quantity, BigDecimal amount, String reason,
                                        String refundRequestNo, Long operatorId, String operatorName) {
        OrderDishChangeLog change = new OrderDishChangeLog()
                .setEventKey(refundEventKey(refundRequestNo, item.getId()))
                .setOrderId(order.getId())
                .setOrderItemId(item.getId())
                .setStoreId(order.getStoreId())
                .setDishId(item.getDishId())
                .setCategoryId(item.getCategoryId())
                .setDishName(getDishName(item.getDishId()))
                .setChangeType(2)
                .setQuantity(quantity)
                .setAmount(amount == null ? BigDecimal.ZERO : amount)
                .setReason(StrUtil.blankToDefault(reason, "未填写原因"))
                .setOperatorId(operatorId)
                .setOperatorName(operatorName)
                .setOccurredAt(LocalDateTime.now());
        orderDishChangeLogMapper.insert(change);
    }

    private String refundEventKey(String refundRequestNo, Long orderItemId) {
        return "REFUND:" + refundRequestNo + ":" + orderItemId;
    }

    private String resolveOperatorName(Long operatorId) {
        if (operatorId == null) return null;
        try {
            SysUser user = FeignResultUtil.unwrap(userFeignClient.getUserById(operatorId));
            if (user != null) return StrUtil.blankToDefault(user.getNickname(), user.getUsername());
        } catch (Exception e) {
            log.warn("操作人名称查询失败: operatorId={}", operatorId, e);
        }
        return "用户" + operatorId;
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

        // 支付结果由 paymentStatus 判断；履约完成或取消后不能退菜。
        if (order.getStatus() == OrderStatusEnum.COMPLETED ||
                order.getStatus() == OrderStatusEnum.CANCELLED
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
            BigDecimal tp = item.getLinePayableAmount() != null ? item.getLinePayableAmount()
                    : (item.getUnitSellingPrice() != null ? item.getUnitSellingPrice().multiply(new BigDecimal(item.getQuantity() != null ? item.getQuantity() : 0)) : BigDecimal.ZERO);
            BigDecimal dp = item.getDiscountAmount() != null ? item.getDiscountAmount() : BigDecimal.ZERO;
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

    /** 将试价时解析出的门店实际成本固化到订单项，避免历史毛利随成本配置变化。 */
    private void applyCostSnapshot(RestaurantOrderItem orderItem, PricingQuoteVO.Line pricedLine) {
        BigDecimal unitCost = pricedLine.getUnitCost();
        orderItem.setUnitCostSnapshot(unitCost);
        orderItem.setLineCostAmount(unitCost == null ? null : unitCost
                .multiply(BigDecimal.valueOf(orderItem.getQuantity())).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 按“会话行、订单行”的固定顺序加锁，串行化加菜、赠菜、退菜与支付。
     */
    private RestaurantOrder lockOrderForMutation(Long orderId) {
        RestaurantOrder snapshot = this.getById(orderId);
        if (snapshot == null) {
            return null;
        }
        if (StrUtil.isNotBlank(snapshot.getSessionNo())) {
            LambdaQueryWrapper<TableSession> sessionWrapper = new LambdaQueryWrapper<>();
            sessionWrapper.eq(TableSession::getSessionNo, snapshot.getSessionNo()).last("FOR UPDATE");
            tableSessionService.getOne(sessionWrapper);
        }
        return getOrderForUpdate(orderId);
    }

    /**
     * 锁定订单行，支付状态检查与资金操作必须在同一事务中完成。
     */
    private RestaurantOrder getOrderForUpdate(Long orderId) {
        if (orderId == null) {
            return null;
        }
        LambdaQueryWrapper<RestaurantOrder> orderWrapper = new LambdaQueryWrapper<>();
        orderWrapper.eq(RestaurantOrder::getId, orderId).last("FOR UPDATE");
        return this.getOne(orderWrapper);
    }

    private PricingQuoteVO quoteCreateItems(Long storeId, Long userId,
                                             List<CreateOrderRequest.CartItem> cartItems, boolean cashier) {
        PricingQuoteRequest request = new PricingQuoteRequest();
        request.setStoreId(storeId);
        request.setUserId(userId);
        request.setChannel(cashier ? "CASHIER" : "CUSTOMER");
        List<PricingQuoteRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            CreateOrderRequest.CartItem cart = cartItems.get(i);
            PricingQuoteRequest.Item item = new PricingQuoteRequest.Item();
            item.setLineKey(String.valueOf(i));
            item.setDishId(cart.getDishId());
            item.setQuantity(cart.getQuantity());
            item.setSpecExtraPrice(cart.getExtraPrice());
            items.add(item);
        }
        request.setItems(items);
        return pricingService.quote(request);
    }

    private PricingQuoteVO quoteCashierItems(Long storeId, Long userId,
                                              List<CashierOrderRequest.CartItem> cartItems) {
        PricingQuoteRequest request = new PricingQuoteRequest();
        request.setStoreId(storeId);
        request.setUserId(userId);
        request.setChannel("CASHIER");
        List<PricingQuoteRequest.Item> items = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            CashierOrderRequest.CartItem cart = cartItems.get(i);
            PricingQuoteRequest.Item item = new PricingQuoteRequest.Item();
            item.setLineKey(String.valueOf(i));
            item.setDishId(cart.getDishId());
            item.setQuantity(cart.getQuantity());
            item.setSpecExtraPrice(cart.getExtraPrice());
            items.add(item);
        }
        request.setItems(items);
        return pricingService.quote(request);
    }

    private void persistPricingAdjustments(Long orderId, List<RestaurantOrderItem> orderItems,
                                           PricingQuoteVO quote) {
        for (int i = 0; i < orderItems.size() && i < quote.getLines().size(); i++) {
            RestaurantOrderItem orderItem = orderItems.get(i);
            PricingQuoteVO.Line line = quote.getLines().get(i);
            for (PricingQuoteVO.Adjustment adjustment : line.getAdjustments()) {
                orderPriceAdjustmentMapper.insert(new OrderPriceAdjustment()
                        .setOrderId(orderId).setOrderItemId(orderItem.getId())
                        .setCampaignId(adjustment.getCampaignId())
                        .setAdjustmentType(adjustment.getAdjustmentType())
                        .setBeforeAmount(adjustment.getBeforeAmount())
                        .setAdjustmentAmount(adjustment.getAdjustmentAmount())
                        .setAfterAmount(adjustment.getAfterAmount())
                        .setRuleSnapshot(adjustment.getRuleSnapshot())
                        .setCreatedAt(LocalDateTime.now()));
            }
            if (Integer.valueOf(1).equals(orderItem.getIsGift())) {
                orderPriceAdjustmentMapper.insert(new OrderPriceAdjustment()
                        .setOrderId(orderId).setOrderItemId(orderItem.getId())
                        .setAdjustmentType(AdjustmentType.GIFT)
                        .setBeforeAmount(line.getLinePayableAmount())
                        .setAdjustmentAmount(line.getLinePayableAmount())
                        .setAfterAmount(BigDecimal.ZERO)
                        .setRuleSnapshot("{\"source\":\"MANUAL_GIFT\"}")
                        .setCreatedAt(LocalDateTime.now()));
            }
        }
    }

    private void persistManualRateAdjustments(Long orderId, List<RestaurantOrderItem> orderItems,
                                              PricingQuoteVO quote, BigDecimal discountRate) {
        if (discountRate == null || discountRate.compareTo(BigDecimal.ONE) >= 0) return;
        for (int i = 0; i < orderItems.size() && i < quote.getLines().size(); i++) {
            RestaurantOrderItem orderItem = orderItems.get(i);
            if (Integer.valueOf(1).equals(orderItem.getIsGift())) continue;
            BigDecimal before = quote.getLines().get(i).getLinePayableAmount();
            BigDecimal after = orderItem.getLinePayableAmount();
            BigDecimal discount = before.subtract(after).max(BigDecimal.ZERO);
            if (discount.signum() == 0) continue;
            orderPriceAdjustmentMapper.insert(new OrderPriceAdjustment()
                    .setOrderId(orderId).setOrderItemId(orderItem.getId())
                    .setAdjustmentType(AdjustmentType.MANUAL_DISCOUNT)
                    .setBeforeAmount(before).setAdjustmentAmount(discount).setAfterAmount(after)
                    .setRuleSnapshot("{\"discountRate\":" + discountRate.stripTrailingZeros().toPlainString() + "}")
                    .setCreatedAt(LocalDateTime.now()));
        }
    }

}
