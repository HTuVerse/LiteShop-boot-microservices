package org.hongtu.mall.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.api.client.CouponFeignClient;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.client.PayFeignClient;
import org.hongtu.api.dto.MallPointsChangeDTO;
import org.hongtu.api.dto.PaymentRefundNotifyResult;
import org.hongtu.api.dto.WeChatNotifyParseDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.exception.BusinessException;
import org.hongtu.mall.dto.MallExchangeRequest;
import org.hongtu.mall.dto.MallOrder;
import org.hongtu.mall.dto.MallProduct;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

/** 兑换订单与跨服务补偿流程。 */
@Slf4j
@Service
public class MallExchangeService {
    @Resource private JdbcTemplate jdbc;
    @Resource private TransactionTemplate transaction;
    @Resource private MallCatalogService catalog;
    @Resource private UserFeignClient userClient;
    @Resource private CouponFeignClient couponClient;
    @Resource private PayFeignClient payClient;
    @Resource private MallPaymentGateway paymentGateway;
    private static final BeanPropertyRowMapper<MallOrder> ROW = new BeanPropertyRowMapper<>(MallOrder.class);

    /** 预占库存并创建订单；同一 requestKey 重试返回同一订单。 */
    public MallOrder exchange(Long userId, MallExchangeRequest request) {
        if (userId == null || request == null || request.getProductId() == null
                || request.getRequestKey() == null || !request.getRequestKey().matches("[A-Za-z0-9_-]{8,64}"))
            throw new BusinessException("兑换参数无效");
        long tenantId = TenantContext.getTenantId();
        MallOrder existing = findByKey(tenantId, userId, request.getRequestKey());
        if (existing != null) return process(existing.getId());
        try {
            Long orderId = transaction.execute(status -> {
                MallProduct product = catalog.get(request.getProductId());
                if (product.getStatus() != 1) throw new BusinessException("商品已下架");
                int changed = jdbc.update("UPDATE mall_product SET stock=stock-1,updated_at=NOW() WHERE id=? AND tenant_id=? AND status=1 AND stock>0",
                        product.getId(), tenantId);
                if (changed != 1) throw new BusinessException("商品库存不足");
                long id = IdWorker.getId();
                boolean cash = product.getCashPrice().signum() > 0;
                jdbc.update("INSERT INTO mall_exchange_order (id,tenant_id,user_id,product_id,product_name,coupon_id,points_cost,pricing_mode,cash_amount,payment_status,pay_out_trade_no,refund_no,expires_at,status,request_key,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,IF(?=1,DATE_ADD(NOW(),INTERVAL 30 MINUTE),NULL),'PENDING',?,NOW(),NOW())",
                        id, tenantId, userId, product.getId(), product.getName(), product.getCouponId(), product.getPointsPrice(),
                        product.getPricingMode(), product.getCashPrice(), cash ? "UNPAID" : "NONE", cash ? "M" + id : null,
                        cash ? "MR" + id : null, cash ? 1 : 0, request.getRequestKey());
                return id;
            });
            return process(orderId);
        } catch (DuplicateKeyException duplicate) {
            MallOrder repeated = findByKey(tenantId, userId, request.getRequestKey());
            if (repeated != null) return process(repeated.getId());
            throw duplicate;
        } catch (BusinessException rejected) {
            // 仅剩一件时，并发重复请求可能在扣库存处等待首单提交后看到零库存。
            MallOrder repeated = findByKey(tenantId, userId, request.getRequestKey());
            if (repeated != null) return process(repeated.getId());
            throw rejected;
        }
    }

    /** 管理端查看全租户订单，客户端仅查看本人。 */
    public Map<String, Object> page(int currentPage, int pageSize, Long userId) {
        long tenantId = TenantContext.getTenantId();
        int page = Math.max(1, currentPage);
        int size = Math.max(1, Math.min(100, pageSize));
        String condition = userId == null ? "" : " AND user_id=?";
        Long total = userId == null
                ? jdbc.queryForObject("SELECT COUNT(*) FROM mall_exchange_order WHERE tenant_id=?", Long.class, tenantId)
                : jdbc.queryForObject("SELECT COUNT(*) FROM mall_exchange_order WHERE tenant_id=?" + condition, Long.class, tenantId, userId);
        List<MallOrder> rows = userId == null
                ? jdbc.query("SELECT * FROM mall_exchange_order WHERE tenant_id=? ORDER BY created_at DESC LIMIT ? OFFSET ?", ROW, tenantId, size, (page - 1) * size)
                : jdbc.query("SELECT * FROM mall_exchange_order WHERE tenant_id=?" + condition + " ORDER BY created_at DESC LIMIT ? OFFSET ?", ROW, tenantId, userId, size, (page - 1) * size);
        return Map.of("records", rows, "total", total == null ? 0 : total, "current", page, "size", size);
    }

    /** 租户内查询订单，管理员可传空 userId。 */
    public MallOrder get(Long id, Long userId) {
        if (id == null) throw new BusinessException("订单ID不能为空");
        List<MallOrder> rows = userId == null
                ? jdbc.query("SELECT * FROM mall_exchange_order WHERE id=? AND tenant_id=?", ROW, id, TenantContext.getTenantId())
                : jdbc.query("SELECT * FROM mall_exchange_order WHERE id=? AND tenant_id=? AND user_id=?", ROW, id, TenantContext.getTenantId(), userId);
        if (rows.isEmpty()) throw new BusinessException("兑换订单不存在");
        return rows.getFirst();
    }

    private MallOrder findByKey(long tenantId, Long userId, String key) {
        List<MallOrder> rows = jdbc.query("SELECT * FROM mall_exchange_order WHERE tenant_id=? AND user_id=? AND request_key=?", ROW, tenantId, userId, key);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /** 领取到期处理租约；远程结果不明时保留订单并稍后重试。 */
    public MallOrder process(Long id) {
        MallOrder order = get(id, null);
        if ("SUCCESS".equals(order.getStatus()) || "FAILED".equals(order.getStatus()) || "WAIT_PAY".equals(order.getStatus())) return order;
        int claimed = jdbc.update("UPDATE mall_exchange_order SET status=IF(status IN ('REFUNDING','LATE_REFUND','PAID','SPENT'),status,'PROCESSING'), processing_until=DATE_ADD(NOW(), INTERVAL 60 SECOND), updated_at=NOW() WHERE id=? AND tenant_id=? AND status IN ('PENDING','PROCESSING','SPENT','PAID','REFUNDING','LATE_REFUND') AND (processing_until IS NULL OR processing_until < NOW())",
                id, TenantContext.getTenantId());
        if (claimed != 1) return get(id, null);
        order = get(id, null);
        if ("REFUNDING".equals(order.getStatus())) return refund(order);
        if ("LATE_REFUND".equals(order.getStatus())) return refundLate(order);
        if (order.getPointsCost() > 0) {
            try {
                FeignResultUtil.unwrap(userClient.changeMallPoints(change(order, "SPEND")));
            } catch (BusinessException rejected) {
                // 明确余额不足时释放库存；远程结果不明时继续用同一幂等键重试。
                if ("积分不足或用户不存在".equals(rejected.getMessage()) || "用户不存在".equals(rejected.getMessage()))
                    failWithoutSpend(order, rejected.getMessage());
                else log.warn("兑换扣分结果待确认，orderId={}", id, rejected);
                return get(id, null);
            } catch (Exception unknown) {
                log.warn("兑换扣分结果待确认，orderId={}", id, unknown);
                return get(id, null);
            }
        }
        jdbc.update("UPDATE mall_exchange_order SET status='SPENT',updated_at=NOW() WHERE id=? AND tenant_id=? AND status='PROCESSING'",
                id, TenantContext.getTenantId());
        order = get(id, null);
        if (order.getCashAmount().signum() > 0 && "UNPAID".equals(order.getPaymentStatus())) {
            jdbc.update("UPDATE mall_exchange_order SET status='WAIT_PAY',processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='SPENT'",
                    id, TenantContext.getTenantId());
            return get(id, null);
        }
        if (order.getCashAmount().signum() > 0 && !"PAID".equals(order.getPaymentStatus())) return order;
        try {
            Long couponId = FeignResultUtil.unwrap(couponClient.issueMallCoupon(order.getUserId(), order.getCouponId(), id));
            jdbc.update("UPDATE mall_exchange_order SET status='SUCCESS',user_coupon_id=?,processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status IN ('SPENT','PAID')",
                    couponId, id, TenantContext.getTenantId());
        } catch (BusinessException rejected) {
            if (definitiveCouponFailure(rejected.getMessage())) {
                jdbc.update("UPDATE mall_exchange_order SET status='REFUNDING',payment_status=IF(payment_status='PAID','REFUNDING',payment_status),failure_reason=?,processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status IN ('SPENT','PAID')",
                        trimReason(rejected.getMessage()), id, TenantContext.getTenantId());
                return refund(get(id, null));
            }
            log.warn("兑换发券结果待确认，orderId={}", id, rejected);
        } catch (Exception unknown) {
            log.warn("兑换发券结果待确认，orderId={}", id, unknown);
        }
        return get(id, null);
    }

    private MallOrder refund(MallOrder order) {
        try {
            if (order.getCashAmount().signum() > 0 && !"REFUNDED".equals(order.getPaymentStatus())
                    && !"UNPAID".equals(order.getPaymentStatus())) {
                if (!"REFUNDING".equals(order.getPaymentStatus()))
                    jdbc.update("UPDATE mall_exchange_order SET payment_status='REFUNDING',updated_at=NOW() WHERE id=? AND tenant_id=? AND status='REFUNDING'",
                            order.getId(), TenantContext.getTenantId());
                if (!ensureCashRefund(order)) return get(order.getId(), null);
                jdbc.update("UPDATE mall_exchange_order SET payment_status='REFUNDED',updated_at=NOW() WHERE id=? AND tenant_id=? AND status='REFUNDING'",
                        order.getId(), TenantContext.getTenantId());
            }
            if (order.getPointsCost() > 0)
                FeignResultUtil.unwrap(userClient.changeMallPoints(change(order, "REFUND")));
            transaction.executeWithoutResult(status -> {
                int updated = jdbc.update("UPDATE mall_exchange_order SET status='FAILED',processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='REFUNDING'",
                        order.getId(), TenantContext.getTenantId());
                if (updated == 1) jdbc.update("UPDATE mall_product SET stock=stock+1,updated_at=NOW() WHERE id=? AND tenant_id=?",
                        order.getProductId(), TenantContext.getTenantId());
            });
        } catch (Exception unknown) {
            log.warn("兑换退款或退分待重试，orderId={}", order.getId(), unknown);
        }
        return get(order.getId(), null);
    }

    private void failWithoutSpend(MallOrder order, String reason) {
        transaction.executeWithoutResult(status -> {
            int updated = jdbc.update("UPDATE mall_exchange_order SET status='FAILED',failure_reason=?,processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='PROCESSING'",
                    trimReason(reason), order.getId(), TenantContext.getTenantId());
            if (updated == 1) jdbc.update("UPDATE mall_product SET stock=stock+1,updated_at=NOW() WHERE id=? AND tenant_id=?",
                    order.getProductId(), TenantContext.getTenantId());
        });
    }

    private MallPointsChangeDTO change(MallOrder order, String action) {
        MallPointsChangeDTO dto = new MallPointsChangeDTO();
        dto.setUserId(order.getUserId());
        dto.setOrderId(order.getId());
        dto.setPoints(order.getPointsCost());
        dto.setAction(action);
        return dto;
    }

    private String trimReason(String reason) {
        return reason == null ? "兑换失败" : reason.substring(0, Math.min(200, reason.length()));
    }

    private boolean definitiveCouponFailure(String reason) {
        return reason != null && (reason.contains("库存不足") || reason.contains("抢完")
                || reason.contains("未上架") || reason.contains("已过期")
                || reason.contains("优惠券不存在") || reason.contains("用户不存在")
                || reason.contains("过期时间必须晚于"));
    }

    /** 发起微信小程序预支付；只允许订单所属会员支付未过期订单。 */
    public Map<String, Object> createWechatPay(Long id, Long userId, String openId) {
        MallOrder order = process(get(id, userId).getId());
        if (!"WAIT_PAY".equals(order.getStatus()) || !"UNPAID".equals(order.getPaymentStatus()))
            throw new BusinessException("订单当前不可支付");
        if (order.getExpiresAt() != null && !order.getExpiresAt().isAfter(java.time.LocalDateTime.now())) {
            cancel(id, userId);
            throw new BusinessException("订单支付时间已过，请重新兑换");
        }
        return paymentGateway.create(order, openId);
    }

    /** 查询微信订单并同步支付结果；前端不可以自行声明支付成功。 */
    public MallOrder syncPayment(Long id, Long userId) {
        MallOrder order = get(id, userId);
        if (!"WAIT_PAY".equals(order.getStatus()) && !"REFUNDING".equals(order.getStatus())) return order;
        if ("WAIT_PAY".equals(order.getStatus())) {
            try {
                Map<String, Object> result = paymentGateway.query(order);
                if ("SUCCESS".equals(result.get("tradeState"))) confirmPaid(order, result);
            } catch (Exception unknown) {
                log.warn("商城支付查询待重试，orderId={}", id, unknown);
            }
        }
        return get(id, userId);
    }

    /** 微信签名由支付服务验证，金额与本地订单快照再核对。 */
    public void paymentNotify(WeChatNotifyParseDTO request) {
        PaymentNotifyResult paid = FeignResultUtil.unwrap(payClient.parseWeChatNotify(request));
        if (paid == null || !Boolean.TRUE.equals(paid.getIsSuccess())) return;
        if (paid.getOutTradeNo() == null || !paid.getOutTradeNo().matches("M[0-9]+"))
            throw new BusinessException("支付订单号无效");
        Long id = Long.valueOf(paid.getOutTradeNo().substring(1));
        MallOrder order = get(id, null);
        if (!order.getTenantId().equals(request.getTenantId()) || !paid.getOutTradeNo().equals(order.getPayOutTradeNo())
                || paid.getTotalAmount() == null
                || !order.getCashAmount().movePointRight(2).toBigIntegerExact().equals(java.math.BigInteger.valueOf(paid.getTotalAmount()))
                || !"CNY".equalsIgnoreCase(paid.getCurrency()) || !("mall:" + id).equals(paid.getAttach())
                || paid.getTransactionId() == null)
            throw new BusinessException("支付通知与商城订单不匹配");
        confirmPaid(order, Map.of("outTradeNo", paid.getOutTradeNo(), "transactionId", paid.getTransactionId(),
                "total", paid.getTotalAmount(), "currency", paid.getCurrency(), "merchantMatched", true));
    }

    /** 退款通知只作为唤醒信号，仍通过支付服务主动查退款结果。 */
    public void refundNotify(WeChatNotifyParseDTO request) {
        PaymentRefundNotifyResult notice = FeignResultUtil.unwrap(payClient.parseWeChatRefundNotify(request));
        if (notice == null || notice.getOutRefundNo() == null || !notice.getOutRefundNo().matches("MR[0-9]+"))
            throw new BusinessException("退款通知单号无效");
        MallOrder order = get(Long.valueOf(notice.getOutRefundNo().substring(2)), null);
        if (!order.getTenantId().equals(request.getTenantId()) || !order.getRefundNo().equals(notice.getOutRefundNo())
                || !order.getPayOutTradeNo().equals(notice.getOutTradeNo())
                || !Boolean.TRUE.equals(notice.getMerchantMatched())
                || notice.getRefundAmount() == null || notice.getRefundAmount().compareTo(order.getCashAmount()) != 0)
            throw new BusinessException("退款通知与商城订单不匹配");
        if ("SUCCESS".equals(notice.getStatus()) && ("REFUNDING".equals(order.getStatus()) || "LATE_REFUND".equals(order.getStatus()))) {
            jdbc.update("UPDATE mall_exchange_order SET payment_status='REFUNDED',processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status IN ('REFUNDING','LATE_REFUND')",
                    order.getId(), TenantContext.getTenantId());
            process(order.getId());
        }
    }

    /** 付款后的状态转换采用条件更新，回调与主动查单并发时只发一次券。 */
    private void confirmPaid(MallOrder order, Map<String, Object> result) {
        if (!Boolean.TRUE.equals(result.get("merchantMatched"))
                || !order.getPayOutTradeNo().equals(result.get("outTradeNo"))
                || !"CNY".equalsIgnoreCase(String.valueOf(result.get("currency")))
                || !order.getCashAmount().movePointRight(2).toBigIntegerExact().equals(asInteger(result.get("total")))
                || result.get("transactionId") == null) throw new BusinessException("微信支付金额或商户不匹配");
        int changed = jdbc.update("UPDATE mall_exchange_order SET status='PAID',payment_status='PAID',payment_txn_id=?,paid_at=NOW(),processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='WAIT_PAY' AND payment_status='UNPAID'",
                result.get("transactionId"), order.getId(), TenantContext.getTenantId());
        if (changed == 1) process(order.getId());
        else if ("REFUNDING".equals(get(order.getId(), null).getStatus())) {
            int refunding = jdbc.update("UPDATE mall_exchange_order SET payment_status='REFUNDING',payment_txn_id=?,paid_at=NOW(),processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='REFUNDING' AND payment_status='UNPAID'",
                    result.get("transactionId"), order.getId(), TenantContext.getTenantId());
            if (refunding == 1) process(order.getId());
        } else if ("FAILED".equals(get(order.getId(), null).getStatus())) {
            // 关单与支付竞态下的迟到付款，只退现金；积分和库存先前已恢复。
            int late = jdbc.update("UPDATE mall_exchange_order SET status='LATE_REFUND',payment_status='REFUNDING',payment_txn_id=?,paid_at=NOW(),processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='FAILED' AND payment_status='UNPAID'",
                    result.get("transactionId"), order.getId(), TenantContext.getTenantId());
            if (late == 1) process(order.getId());
        }
    }

    /** 已释放库存的迟到支付只需退款，不再退分或重复恢复库存。 */
    private MallOrder refundLate(MallOrder order) {
        try {
            if (!"REFUNDED".equals(order.getPaymentStatus())) {
                if (!ensureCashRefund(order)) return get(order.getId(), null);
            }
            jdbc.update("UPDATE mall_exchange_order SET status='FAILED',payment_status='REFUNDED',processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='LATE_REFUND'",
                    order.getId(), TenantContext.getTenantId());
        } catch (Exception unknown) {
            log.warn("迟到支付退款待重试，orderId={}", order.getId(), unknown);
        }
        return get(order.getId(), null);
    }

    /** 取消前先关单，若渠道报告已支付则按支付成功处理。 */
    public MallOrder cancel(Long id, Long userId) {
        MallOrder order = get(id, userId);
        if (!"WAIT_PAY".equals(order.getStatus())) return order;
        Map<String, Object> closed = paymentGateway.close(order);
        if ("PAID".equals(closed.get("outcome"))) return syncPayment(id, userId);
        if (!"CLOSED".equals(closed.get("outcome"))) throw new BusinessException("微信订单关闭结果待确认，请稍后重试");
        jdbc.update("UPDATE mall_exchange_order SET status='REFUNDING',failure_reason='用户取消或支付超时',processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='WAIT_PAY' AND payment_status='UNPAID'",
                id, TenantContext.getTenantId());
        return process(id);
    }

    private boolean refundSucceeded(MallOrder order, Map<String, Object> result) {
        if (result == null || !Boolean.TRUE.equals(result.get("success")) || !"SUCCESS".equals(result.get("status"))) return false;
        return Boolean.TRUE.equals(result.get("merchantMatched"))
                && order.getRefundNo().equals(result.get("outRefundNo"))
                && order.getPayOutTradeNo().equals(result.get("outTradeNo"))
                && order.getCashAmount().movePointRight(2).toBigIntegerExact().equals(asInteger(result.get("refund")))
                && order.getCashAmount().movePointRight(2).toBigIntegerExact().equals(asInteger(result.get("total")));
    }

    /** 退款请求结果未知时主动查单，仍使用相同商户退款单号。 */
    private boolean ensureCashRefund(MallOrder order) {
        // 商户配置被停用后无法确认退款，等待恢复配置后继续处理。
        if (!paymentGateway.hasUsableWeChatAccount(order.getTenantId())) return false;
        try {
            if (refundSucceeded(order, paymentGateway.refund(order))) return true;
        } catch (Exception unknown) {
            log.warn("商城退款请求结果待确认，orderId={}", order.getId(), unknown);
        }
        try { return refundSucceeded(order, paymentGateway.queryRefund(order)); }
        catch (Exception unknown) { return false; }
    }

    private java.math.BigInteger asInteger(Object value) {
        try { return new BigDecimal(String.valueOf(value)).toBigIntegerExact(); }
        catch (Exception invalid) { return java.math.BigInteger.valueOf(-1); }
    }

    /** 恢复结果不明的订单。 */
    @Scheduled(fixedDelay = 30000)
    public void retryPending() {
        List<Map<String, Object>> pending = jdbc.queryForList("SELECT id,tenant_id FROM mall_exchange_order WHERE status IN ('PENDING','PROCESSING','SPENT','PAID','REFUNDING','LATE_REFUND') AND (processing_until IS NULL OR processing_until < NOW()) ORDER BY created_at LIMIT 100");
        for (Map<String, Object> row : pending) {
            Long id = ((Number) row.get("id")).longValue();
            Long tenantId = ((Number) row.get("tenant_id")).longValue();
            try {
                TenantScope.run(tenantId, () -> process(id));
            } catch (Exception ex) {
                log.warn("重试兑换订单失败，orderId={}", id, ex);
            }
        }
        // 无账户的到期订单保留原状态；不能在无法查渠道时断定未支付。
        List<Long> tenantIds = jdbc.queryForList("SELECT DISTINCT tenant_id FROM mall_exchange_order WHERE status='WAIT_PAY' AND expires_at<NOW()", Long.class);
        for (Long tenantId : tenantIds) {
            try {
                TenantScope.run(tenantId, () -> {
                    if (!paymentGateway.hasUsableWeChatAccount(tenantId)) return;
                    List<Long> expired = jdbc.queryForList("SELECT id FROM mall_exchange_order WHERE tenant_id=? AND status='WAIT_PAY' AND expires_at<NOW() ORDER BY created_at LIMIT 100", Long.class, tenantId);
                    for (Long id : expired) {
                        try { cancel(id, null); }
                        catch (Exception ex) { log.warn("过期兑换订单关单待重试，orderId={}", id, ex); }
                    }
                });
            } catch (Exception ex) {
                log.warn("查询租户支付账户失败，tenantId={}", tenantId, ex);
            }
        }
    }
}
