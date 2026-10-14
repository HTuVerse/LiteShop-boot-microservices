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

    /** 微信退款回调只在核对退款单、原订单、金额和商户后入账。 */
    @Override
    public String handleWeChatRefundNotify(Long tenantId, String requestBody, String signature,
                                           String timestamp, String nonce, String serial) {
        if (tenantId == null) {
            return "{\"code\": \"FAIL\", \"message\": \"缺少租户\"}";
        }
        try {
            PaymentRefundNotifyResult parsed = FeignResultUtil.unwrap(payFeignClient.parseWeChatRefundNotify(
                    WeChatNotifyParseDTO.builder().tenantId(tenantId).requestBody(requestBody)
                            .signature(signature).timestamp(timestamp).nonce(nonce).serial(serial).build()));
            completeChannelRefundNotify(tenantId, parsed);
            return "{\"code\": \"SUCCESS\", \"message\": \"成功\"}";
        } catch (RuntimeException exception) {
            log.error("微信退款回调处理失败", exception);
            return "{\"code\": \"FAIL\", \"message\": \"处理失败\"}";
        }
    }

    /** 支付宝退款通知验签后按退款单入账。 */
    @Override
    public String handleAlipayRefundNotify(Long tenantId, Map<String, String> params) {
        if (tenantId == null) {
            return "failure";
        }
        try {
            PaymentRefundNotifyResult parsed = FeignResultUtil.unwrap(
                    payFeignClient.parseAlipayRefundNotify(tenantId, params));
            completeChannelRefundNotify(tenantId, parsed);
            return "success";
        } catch (RuntimeException exception) {
            log.error("支付宝退款回调处理失败", exception);
            return "failure";
        }
    }

    private void completeChannelRefundNotify(Long tenantId, PaymentRefundNotifyResult parsed) {
        if (parsed == null || !Boolean.TRUE.equals(parsed.getMerchantMatched())
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
            ChannelRefundState state = "SUCCESS".equals(parsed.getStatus())
                    ? ChannelRefundState.SUCCESS
                    : "PROCESSING".equals(parsed.getStatus()) ? ChannelRefundState.PROCESSING : ChannelRefundState.FAILED;
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
                    : PaymentTransitionPolicy.canCancel(order.getStatus(), order.getPaymentStatus());
            if (!allowed) {
                throw new BusinessException(ORDER_CANCEL_FAIL.getCode(), "订单已完成或已取消，无法再次取消");
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
                    baseMapper.markReconcilingIfUnpaid(orderId) == 1));
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
        return true;
    }

    private RestaurantOrder finishUnpaidClose(Long orderId, String reason, boolean timeout) {
        RestaurantOrder order = lockOrderForMutation(orderId);
        if (order == null) {
            return null;
        }
        boolean allowed = timeout
                ? PaymentTransitionPolicy.canTimeout(order.getStatus(), order.getPaymentStatus())
                : PaymentTransitionPolicy.canCancel(order.getStatus(), order.getPaymentStatus());
        if (!allowed) {
            return null;
        }
        restoreReservedOrderStock(order);
        int updated = timeout
                ? baseMapper.markTimeoutIfUnpaid(orderId, reason, LocalDateTime.now())
                : baseMapper.markClosedIfUnpaid(orderId, reason, LocalDateTime.now());
        if (updated != 1) {
            throw new BusinessException("订单状态已变化，请刷新后重试");
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
            current = FeignResultUtil.unwrap(payFeignClient.refundWeChatTrade(
                    order.getTenantId(), order.getOrderNo(), refund.getRefundNo(), toCentString(refund.getAmount()),
                    String.valueOf(totalNumber.longValue()), StrUtil.blankToDefault(refund.getReason(), "订单退菜退款"),
                    PaymentNotifyUrls.wechatRefund(weChatOrderNotifyUrl, order.getTenantId())));
        }
        if (!Boolean.TRUE.equals(current.get("success"))) {
            return ChannelRefundState.FAILED;
        }
        if (current.get("refundId") != null) {
            refund.setChannelRefundId(String.valueOf(current.get("refundId")));
        }
        return "SUCCESS".equals(String.valueOf(current.get("status")))
                ? ChannelRefundState.SUCCESS : ChannelRefundState.PROCESSING;
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
                    .orderNo(refund.getRefundNo()).operateUserId(operatorId).build());
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
            recordRefundDishChange(order, orderItem, detail.getQuantity(), detail.getAmount(), refund.getReason(), refund.getRefundNo());
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
        } else if (full) {
            baseMapper.markClosedIfUnpaid(order.getId(), "退菜后订单金额为0，自动取消", LocalDateTime.now());
        }
        RefundDishVO result = refundResult(refund, remainingAmount);
        result.setRefundItems(viewItems);
        result.setRefundStatus(RefundStatusEnum.SUCCEEDED.getDesc());
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

    private enum ChannelRefundState {
        SUCCESS, PROCESSING, FAILED, BALANCE
    }

    private static final class StockCaptureException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
