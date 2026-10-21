package org.hongtu.mall.service;

import jakarta.annotation.Resource;
import org.hongtu.api.client.ConfigFeignClient;
import org.hongtu.api.client.PayFeignClient;
import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.exception.BusinessException;
import org.hongtu.mall.dto.MallOrder;
import org.hongtu.params.WeChatPayTradeParam;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;

/** 商城支付适配层，复用租户微信商户号与 pay-service。 */
@Service
public class MallPaymentGateway {
    @Resource private PayFeignClient payClient;
    @Resource private ConfigFeignClient configClient;

    /** 使用餐饮支付账户配置的公网域名，构造商城自己的回调地址。 */
    private String notifyUrl(Long tenantId, boolean refund) {
        TenantPaymentRuntimeDTO account = FeignResultUtil.unwrap(configClient.paymentAccount(tenantId, "WECHAT"));
        if (account == null || !Boolean.TRUE.equals(account.getEnabled()) || account.getNotifyUrl() == null)
            throw new BusinessException("租户未配置微信支付账户");
        URI uri = URI.create(account.getNotifyUrl());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getHost().contains("your-domain"))
            throw new BusinessException("微信支付回调地址须为 HTTPS 公网地址");
        String origin = uri.getScheme() + "://" + uri.getRawAuthority();
        return origin + "/points-mall/wechat-pay/" + (refund ? "refund-notify/" : "notify/") + tenantId;
    }

    /** 创建小程序支付参数。 */
    public Map<String, Object> create(MallOrder order, String openId) {
        if (openId == null || openId.isBlank()) throw new BusinessException("微信支付身份不能为空");
        WeChatPayTradeParam param = WeChatPayTradeParam.builder()
                .tenantId(order.getTenantId()).outTradeNo(order.getPayOutTradeNo())
                .description(order.getProductName()).totalAmount(order.getCashAmount())
                .openid(openId).notifyUrl(notifyUrl(order.getTenantId(), false))
                .attach("mall:" + order.getId()).timeExpire(30).build();
        return FeignResultUtil.unwrap(payClient.createWeChatJsapiPay(param));
    }

    /** 查询渠道真实支付结果。 */
    public Map<String, Object> query(MallOrder order) {
        return FeignResultUtil.unwrap(payClient.queryWeChatTrade(order.getTenantId(), order.getPayOutTradeNo()));
    }

    /** 关闭未付款的微信订单。 */
    public Map<String, Object> close(MallOrder order) {
        return FeignResultUtil.unwrap(payClient.closeWeChatTrade(order.getTenantId(), order.getPayOutTradeNo()));
    }

    /** 使用稳定退款单号申请全额退款。 */
    public Map<String, Object> refund(MallOrder order) {
        String cents = order.getCashAmount().movePointRight(2).toBigIntegerExact().toString();
        return FeignResultUtil.unwrap(payClient.refundWeChatTrade(order.getTenantId(), order.getPayOutTradeNo(),
                order.getRefundNo(), cents, cents, "优惠券发放失败", notifyUrl(order.getTenantId(), true)));
    }

    /** 核对退款结果，避免超时后重复退分或恢复库存。 */
    public Map<String, Object> queryRefund(MallOrder order) {
        return FeignResultUtil.unwrap(payClient.queryWeChatRefund(order.getTenantId(), order.getRefundNo()));
    }
}
