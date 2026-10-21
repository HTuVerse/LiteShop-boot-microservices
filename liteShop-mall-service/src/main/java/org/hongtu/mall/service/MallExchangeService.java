package org.hongtu.mall.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.api.client.CouponFeignClient;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.MallPointsChangeDTO;
import org.hongtu.api.util.FeignResultUtil;
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

/** 兑换订单与跨服务补偿流程。 */
@Slf4j
@Service
public class MallExchangeService {
    @Resource private JdbcTemplate jdbc;
    @Resource private TransactionTemplate transaction;
    @Resource private MallCatalogService catalog;
    @Resource private UserFeignClient userClient;
    @Resource private CouponFeignClient couponClient;
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
                jdbc.update("INSERT INTO mall_exchange_order (id,tenant_id,user_id,product_id,product_name,coupon_id,points_cost,status,request_key,created_at,updated_at) VALUES (?,?,?,?,?,?,?,'PENDING',?,NOW(),NOW())",
                        id, tenantId, userId, product.getId(), product.getName(), product.getCouponId(), product.getPointsPrice(), request.getRequestKey());
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
        if ("SUCCESS".equals(order.getStatus()) || "FAILED".equals(order.getStatus())) return order;
        int claimed = jdbc.update("UPDATE mall_exchange_order SET status=IF(status='REFUNDING','REFUNDING','PROCESSING'), processing_until=DATE_ADD(NOW(), INTERVAL 60 SECOND), updated_at=NOW() WHERE id=? AND tenant_id=? AND status IN ('PENDING','PROCESSING','SPENT','REFUNDING') AND (processing_until IS NULL OR processing_until < NOW())",
                id, TenantContext.getTenantId());
        if (claimed != 1) return get(id, null);
        order = get(id, null);
        if ("REFUNDING".equals(order.getStatus())) return refund(order);
        MallPointsChangeDTO spend = change(order, "SPEND");
        try {
            FeignResultUtil.unwrap(userClient.changeMallPoints(spend));
        } catch (BusinessException rejected) {
            // 只有明确的余额/账户拒绝才能释放库存；远程失败结果不明时必须重试同一幂等键。
            if ("积分不足或用户不存在".equals(rejected.getMessage()) || "用户不存在".equals(rejected.getMessage()))
                failWithoutSpend(order, rejected.getMessage());
            else log.warn("兑换扣分结果待确认，orderId={}", id, rejected);
            return get(id, null);
        } catch (Exception unknown) {
            log.warn("兑换扣分结果待确认，orderId={}", id, unknown);
            return get(id, null);
        }
        jdbc.update("UPDATE mall_exchange_order SET status='SPENT',updated_at=NOW() WHERE id=? AND tenant_id=? AND status='PROCESSING'",
                id, TenantContext.getTenantId());
        try {
            Long couponId = FeignResultUtil.unwrap(couponClient.issueMallCoupon(order.getUserId(), order.getCouponId(), id));
            jdbc.update("UPDATE mall_exchange_order SET status='SUCCESS',user_coupon_id=?,processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='SPENT'",
                    couponId, id, TenantContext.getTenantId());
        } catch (BusinessException rejected) {
            if (definitiveCouponFailure(rejected.getMessage())) {
                jdbc.update("UPDATE mall_exchange_order SET status='REFUNDING',failure_reason=?,processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='SPENT'",
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
            FeignResultUtil.unwrap(userClient.changeMallPoints(change(order, "REFUND")));
            transaction.executeWithoutResult(status -> {
                int updated = jdbc.update("UPDATE mall_exchange_order SET status='FAILED',processing_until=NULL,updated_at=NOW() WHERE id=? AND tenant_id=? AND status='REFUNDING'",
                        order.getId(), TenantContext.getTenantId());
                if (updated == 1) jdbc.update("UPDATE mall_product SET stock=stock+1,updated_at=NOW() WHERE id=? AND tenant_id=?",
                        order.getProductId(), TenantContext.getTenantId());
            });
        } catch (Exception unknown) {
            log.warn("兑换退分待重试，orderId={}", order.getId(), unknown);
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

    /** 恢复结果不明的订单。 */
    @Scheduled(fixedDelay = 30000)
    public void retryPending() {
        List<Map<String, Object>> pending = jdbc.queryForList("SELECT id,tenant_id FROM mall_exchange_order WHERE status IN ('PENDING','PROCESSING','SPENT','REFUNDING') AND (processing_until IS NULL OR processing_until < NOW()) ORDER BY created_at LIMIT 100");
        for (Map<String, Object> row : pending) {
            Long id = ((Number) row.get("id")).longValue();
            Long tenantId = ((Number) row.get("tenant_id")).longValue();
            try {
                TenantScope.run(tenantId, () -> process(id));
            } catch (Exception ex) {
                log.warn("重试兑换订单失败，orderId={}", id, ex);
            }
        }
    }
}
