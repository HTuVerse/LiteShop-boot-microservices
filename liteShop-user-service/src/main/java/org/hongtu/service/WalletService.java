package org.hongtu.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.DTO.PaymentNotifyResult;
import org.hongtu.DTO.SysUser;
import org.hongtu.DAO.SysUserMapper;
import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.client.PayFeignClient;
import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.hongtu.wallet.dto.WalletCreditDTO;
import org.hongtu.api.dto.WeChatNotifyParseDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.exception.BusinessException;
import org.hongtu.wallet.dto.WalletOrder;
import org.hongtu.wallet.dto.WalletPlan;
import org.hongtu.wallet.dto.WalletRechargeRequest;
import org.hongtu.params.WeChatPayTradeParam;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantScope;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;

/** 租户储值档位、支付确认和会员余额入账。 */
@Slf4j
@Service
public class WalletService {
    @Resource private JdbcTemplate jdbc;
    @Resource private SysUserMapper userMapper;
    @Resource private org.hongtu.service.impl.InnerBalanceService balances;
    @Resource private PayFeignClient pay;
    @Resource private ConfigFeignClient config;
    private static final BeanPropertyRowMapper<WalletPlan> PLAN_ROW = new BeanPropertyRowMapper<>(WalletPlan.class);
    private static final BeanPropertyRowMapper<WalletOrder> ORDER_ROW = new BeanPropertyRowMapper<>(WalletOrder.class);

    /** 分页列出当前租户的档位。 */
    public Map<String, Object> plans(int currentPage, int pageSize, boolean enabledOnly) {
        int page = Math.max(1, currentPage), size = Math.max(1, Math.min(100, pageSize));
        String active = enabledOnly ? " AND status=1" : "";
        long tenantId = TenantContext.getTenantId();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_recharge_plan WHERE tenant_id=?" + active,
                Long.class, tenantId);
        List<WalletPlan> records = jdbc.query("SELECT * FROM wallet_recharge_plan WHERE tenant_id=?" + active
                + " ORDER BY sort DESC,amount ASC LIMIT ? OFFSET ?", PLAN_ROW, tenantId, size, (page - 1) * size);
        return Map.of("records", records, "total", total == null ? 0 : total, "current", page, "size", size);
    }

    /** 保存档位，金额与赠送额均由服务端验证。 */
    public WalletPlan savePlan(Long id, WalletPlan plan) {
        if (plan == null || plan.getAmount() == null || plan.getBonus() == null
                || plan.getAmount().signum() <= 0 || plan.getAmount().scale() > 2
                || plan.getAmount().compareTo(new BigDecimal("99999999.99")) > 0
                || plan.getBonus().signum() < 0 || plan.getBonus().scale() > 2
                || plan.getBonus().compareTo(new BigDecimal("99999999.99")) > 0
                || plan.getAmount().add(plan.getBonus()).compareTo(new BigDecimal("99999999.99")) > 0
                || plan.getStatus() == null || (plan.getStatus() != 0 && plan.getStatus() != 1)
                || (plan.getBadge() != null && plan.getBadge().length() > 30))
            throw new BusinessException("储值档位参数无效");
        long tenantId = TenantContext.getTenantId();
        int sort = plan.getSort() == null ? 0 : plan.getSort();
        if (id == null) {
            id = IdWorker.getId();
            jdbc.update("INSERT INTO wallet_recharge_plan (id,tenant_id,amount,bonus,badge,sort,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,NOW(),NOW())",
                    id, tenantId, plan.getAmount(), plan.getBonus(), plan.getBadge(), sort, plan.getStatus());
        } else {
            if (jdbc.update("UPDATE wallet_recharge_plan SET amount=?,bonus=?,badge=?,sort=?,status=?,updated_at=NOW() WHERE id=? AND tenant_id=?",
                    plan.getAmount(), plan.getBonus(), plan.getBadge(), sort, plan.getStatus(), id, tenantId) != 1)
                throw new BusinessException("储值档位不存在");
        }
        return plan(id);
    }

    /** 有订单时保留档位快照并停用。 */
    public void deletePlan(Long id) {
        plan(id);
        long tenantId = TenantContext.getTenantId();
        Long used = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_recharge_order WHERE tenant_id=? AND plan_id=?",
                Long.class, tenantId, id);
        if (used != null && used > 0)
            jdbc.update("UPDATE wallet_recharge_plan SET status=0,updated_at=NOW() WHERE id=? AND tenant_id=?", id, tenantId);
        else jdbc.update("DELETE FROM wallet_recharge_plan WHERE id=? AND tenant_id=?", id, tenantId);
    }

    /** 当前会员余额拆分，赠送部分包含在总余额中。 */
    public Map<String, Object> balance(Long userId) {
        SysUser user = member(userId);
        BigDecimal total = user.getSurplusMoney() == null ? BigDecimal.ZERO : user.getSurplusMoney();
        BigDecimal gift = user.getGiftMoney() == null ? BigDecimal.ZERO : user.getGiftMoney();
        return Map.of("total", total, "principal", total.subtract(gift), "bonus", gift);
    }

    /** 只接受档位 ID，实付和赠送金额使用服务端快照。 */
    public WalletOrder create(Long userId, WalletRechargeRequest request) {
        if (request == null || request.getPlanId() == null || request.getRequestKey() == null
                || !request.getRequestKey().matches("[A-Za-z0-9_-]{8,64}"))
            throw new BusinessException("储值订单参数无效");
        member(userId);
        long tenantId = TenantContext.getTenantId();
        List<WalletOrder> repeated = jdbc.query("SELECT * FROM wallet_recharge_order WHERE tenant_id=? AND user_id=? AND request_key=?",
                ORDER_ROW, tenantId, userId, request.getRequestKey());
        if (!repeated.isEmpty()) {
            if (!repeated.getFirst().getPlanId().equals(request.getPlanId())) throw new BusinessException("请求键对应其它档位");
            return repeated.getFirst();
        }
        WalletPlan plan = plan(request.getPlanId());
        if (plan.getStatus() != 1) throw new BusinessException("储值档位已停用");
        long id = IdWorker.getId();
        try {
            jdbc.update("INSERT INTO wallet_recharge_order (id,tenant_id,user_id,plan_id,amount,bonus,status,request_key,out_trade_no,expires_at,created_at,updated_at) VALUES (?,?,?,?,?,?,'UNPAID',?,?,DATE_ADD(NOW(),INTERVAL 30 MINUTE),NOW(),NOW())",
                    id, tenantId, userId, plan.getId(), plan.getAmount(), plan.getBonus(), request.getRequestKey(), "W" + id);
            return order(id, userId);
        } catch (DuplicateKeyException duplicate) {
            List<WalletOrder> existing = jdbc.query("SELECT * FROM wallet_recharge_order WHERE tenant_id=? AND user_id=? AND request_key=?",
                    ORDER_ROW, tenantId, userId, request.getRequestKey());
            if (!existing.isEmpty() && existing.getFirst().getPlanId().equals(request.getPlanId())) return existing.getFirst();
            throw duplicate;
        }
    }

    /** 租户订单分页，会员只能查本人。 */
    public Map<String, Object> orders(int currentPage, int pageSize, Long userId) {
        int page = Math.max(1, currentPage), size = Math.max(1, Math.min(100, pageSize));
        long tenantId = TenantContext.getTenantId();
        String userCondition = userId == null ? "" : " AND user_id=" + userId;
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM wallet_recharge_order WHERE tenant_id=?" + userCondition,
                Long.class, tenantId);
        List<WalletOrder> records = jdbc.query("SELECT * FROM wallet_recharge_order WHERE tenant_id=?" + userCondition
                + " ORDER BY created_at DESC LIMIT ? OFFSET ?", ORDER_ROW, tenantId, size, (page - 1) * size);
        return Map.of("records", records, "total", total == null ? 0 : total, "current", page, "size", size);
    }

    /** 微信小程序支付参数，使用租户自身商户账户。 */
    public Map<String, Object> wechatPay(Long id, Long userId, String openId) {
        WalletOrder order = order(id, userId);
        if (!"UNPAID".equals(order.getStatus()) || !order.getExpiresAt().isAfter(java.time.LocalDateTime.now()))
            throw new BusinessException("储值订单已过期或不可支付");
        if (openId == null || openId.isBlank()) throw new BusinessException("微信支付身份不能为空");
        WeChatPayTradeParam param = WeChatPayTradeParam.builder().tenantId(order.getTenantId())
                .outTradeNo(order.getOutTradeNo()).description("会员储值")
                .totalAmount(order.getAmount()).openid(openId).notifyUrl(notifyUrl(order.getTenantId()))
                .attach("wallet:" + order.getId()).timeExpire(30).build();
        return FeignResultUtil.unwrap(pay.createWeChatJsapiPay(param));
    }

    /** 主动查单确认支付；入账失败保留 PAID 并交给定时重试。 */
    public WalletOrder sync(Long id, Long userId) {
        WalletOrder order = order(id, userId);
        if ("UNPAID".equals(order.getStatus())) {
            Map<String, Object> result = FeignResultUtil.unwrap(pay.queryWeChatTrade(order.getTenantId(), order.getOutTradeNo()));
            if ("SUCCESS".equals(result.get("tradeState"))) confirm(order, result);
        }
        if ("PAID".equals(order(id, userId).getStatus())) credit(id);
        return order(id, userId);
    }

    /** 验签成功后再核对租户、订单号、金额、币种与附加数据。 */
    public void notifyPaid(WeChatNotifyParseDTO request) {
        PaymentNotifyResult notice = FeignResultUtil.unwrap(pay.parseWeChatNotify(request));
        if (notice == null || !Boolean.TRUE.equals(notice.getIsSuccess())) return;
        if (notice.getOutTradeNo() == null || !notice.getOutTradeNo().matches("W[0-9]+"))
            throw new BusinessException("储值商户订单号无效");
        Long id = Long.valueOf(notice.getOutTradeNo().substring(1));
        WalletOrder order = order(id, null);
        if (!order.getTenantId().equals(request.getTenantId()) || !order.getOutTradeNo().equals(notice.getOutTradeNo())
                || !order.getAmount().movePointRight(2).toBigIntegerExact().equals(java.math.BigInteger.valueOf(notice.getTotalAmount()))
                || !"CNY".equalsIgnoreCase(notice.getCurrency())
                || !("wallet:" + id).equals(notice.getAttach())) throw new BusinessException("储值支付通知不匹配");
        confirm(order, Map.of("merchantMatched", true, "outTradeNo", notice.getOutTradeNo(),
                "total", notice.getTotalAmount(), "currency", notice.getCurrency(),
                "transactionId", notice.getTransactionId()));
        credit(id);
    }

    /** 只有渠道真实确认的付款可以改变订单状态。 */
    private void confirm(WalletOrder order, Map<String, Object> result) {
        if (!Boolean.TRUE.equals(result.get("merchantMatched")) || !order.getOutTradeNo().equals(result.get("outTradeNo"))
                || !"CNY".equalsIgnoreCase(String.valueOf(result.get("currency")))
                || !order.getAmount().movePointRight(2).toBigIntegerExact().equals(new BigDecimal(String.valueOf(result.get("total"))).toBigIntegerExact())
                || result.get("transactionId") == null) throw new BusinessException("储值支付金额或商户不匹配");
        jdbc.update("UPDATE wallet_recharge_order SET status='PAID',payment_txn_id=?,paid_at=NOW(),updated_at=NOW() WHERE id=? AND tenant_id=? AND status='UNPAID'",
                result.get("transactionId"), order.getId(), order.getTenantId());
    }

    /** 用户服务按订单号幂等入账；超时后重试不会双倍充值。 */
    private void credit(Long id) {
        WalletOrder order = order(id, null);
        if (!"PAID".equals(order.getStatus())) return;
        WalletCreditDTO command = new WalletCreditDTO();
        command.setUserId(order.getUserId());
        command.setAmount(order.getAmount());
        command.setBonus(order.getBonus());
        command.setOrderNo(order.getOutTradeNo());
        balances.creditWallet(command);
        jdbc.update("UPDATE wallet_recharge_order SET status='SUCCESS',updated_at=NOW() WHERE id=? AND tenant_id=? AND status='PAID'",
                id, order.getTenantId());
    }

    /** 定时重试已付款待入账订单，并核对过期未付款订单的渠道状态。 */
    @Scheduled(fixedDelay = 30000)
    public void reconcileOrders() {
        List<Map<String, Object>> pending = jdbc.queryForList("SELECT id,tenant_id FROM wallet_recharge_order WHERE status='PAID' ORDER BY updated_at LIMIT 100");
        for (Map<String, Object> row : pending) {
            Long id = ((Number) row.get("id")).longValue();
            Long tenantId = ((Number) row.get("tenant_id")).longValue();
            try { TenantScope.run(tenantId, () -> credit(id)); }
            catch (Exception failure) { log.warn("储值订单入账待重试，orderId={}", id, failure); }
        }
        List<Map<String, Object>> expired = jdbc.queryForList("SELECT id,tenant_id FROM wallet_recharge_order WHERE status='UNPAID' AND expires_at<NOW() ORDER BY expires_at LIMIT 100");
        for (Map<String, Object> row : expired) {
            Long id = ((Number) row.get("id")).longValue();
            Long tenantId = ((Number) row.get("tenant_id")).longValue();
            try { TenantScope.run(tenantId, () -> closeExpired(id)); }
            catch (Exception failure) { log.warn("过期储值订单关单待重试，orderId={}", id, failure); }
        }
    }

    /** 关单前先查渠道；支付与过期竞态时以微信的最终结果为准。 */
    private void closeExpired(Long id) {
        WalletOrder order = order(id, null);
        if (!"UNPAID".equals(order.getStatus())) return;
        Map<String, Object> queried = FeignResultUtil.unwrap(pay.queryWeChatTrade(order.getTenantId(), order.getOutTradeNo()));
        if ("SUCCESS".equals(queried.get("tradeState"))) {
            confirm(order, queried);
            credit(id);
            return;
        }
        // 从未调起微信支付的订单查单可能返回 ORDERNOTEXIST，关单接口可确认该状态。
        Map<String, Object> closed = FeignResultUtil.unwrap(pay.closeWeChatTrade(order.getTenantId(), order.getOutTradeNo()));
        if ("PAID".equals(closed.get("outcome"))) {
            sync(id, null);
        } else if ("CLOSED".equals(closed.get("outcome"))) {
            jdbc.update("UPDATE wallet_recharge_order SET status='CLOSED',updated_at=NOW() WHERE id=? AND tenant_id=? AND status='UNPAID'",
                    id, order.getTenantId());
        }
    }

    private WalletPlan plan(Long id) {
        if (id == null) throw new BusinessException("储值档位不能为空");
        List<WalletPlan> rows = jdbc.query("SELECT * FROM wallet_recharge_plan WHERE id=? AND tenant_id=?",
                PLAN_ROW, id, TenantContext.getTenantId());
        if (rows.isEmpty()) throw new BusinessException("储值档位不存在");
        return rows.getFirst();
    }

    private WalletOrder order(Long id, Long userId) {
        if (id == null) throw new BusinessException("储值订单不能为空");
        List<WalletOrder> rows = jdbc.query("SELECT * FROM wallet_recharge_order WHERE id=? AND tenant_id=?" +
                (userId == null ? "" : " AND user_id=" + userId), ORDER_ROW, id, TenantContext.getTenantId());
        if (rows.isEmpty()) throw new BusinessException("储值订单不存在");
        return rows.getFirst();
    }

    private SysUser member(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !TenantContext.getTenantId().equals(user.getTenantId()))
            throw new BusinessException("会员不存在或不属于当前租户");
        return user;
    }

    private String notifyUrl(Long tenantId) {
        TenantPaymentRuntimeDTO account = FeignResultUtil.unwrap(config.paymentAccount(tenantId, "WECHAT"));
        if (account == null || !Boolean.TRUE.equals(account.getEnabled()) || account.getNotifyUrl() == null)
            throw new BusinessException("租户未配置微信支付账户");
        URI uri = URI.create(account.getNotifyUrl());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().contains("your-domain"))
            throw new BusinessException("微信支付回调地址须为 HTTPS 公网地址");
        return uri.getScheme() + "://" + uri.getRawAuthority() + "/wallet/wechat-pay/notify/" + tenantId;
    }
}
