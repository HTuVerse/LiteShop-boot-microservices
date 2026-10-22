package org.hongtu.service;

import lombok.RequiredArgsConstructor;
import org.hongtu.api.dto.PaymentCreateCommand;
import org.hongtu.api.dto.PaymentCreateResult;
import org.hongtu.exception.BusinessException;
import org.hongtu.params.AlipayMiniPayParam;
import org.hongtu.params.AlipayPayTradeParam;
import org.hongtu.params.AlipayTradeWapPayRequestParams;
import org.hongtu.params.WeChatPayTradeParam;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 将订单业务命令转换为微信、支付宝 SDK 所需的交易参数。 */
@Service
@RequiredArgsConstructor
public class PaymentCreationService {
    private final WeChatPayService weChatPayService;
    private final AlipayService alipayService;

    /** 支付服务独立决定各渠道场景的参数与返回形式。 */
    public PaymentCreateResult create(PaymentCreateCommand command) {
        if (command == null || command.getTenantId() == null
                || !StringUtils.hasText(command.getOrderNo())
                || !StringUtils.hasText(command.getSubject())
                || command.getAmount() == null || command.getAmount().signum() <= 0) {
            throw new BusinessException("支付订单参数不完整");
        }
        PaymentCreateResult result = new PaymentCreateResult();
        if ("WECHAT".equals(command.getChannel())) {
            createWeChat(command, result);
        } else if ("ALIPAY".equals(command.getChannel())) {
            createAlipay(command, result);
        } else {
            throw new BusinessException("不支持的支付渠道");
        }
        return result;
    }

    private void createWeChat(PaymentCreateCommand command, PaymentCreateResult result) {
        WeChatPayTradeParam param = WeChatPayTradeParam.builder()
                .tenantId(command.getTenantId())
                .outTradeNo(command.getOrderNo())
                .description(command.getSubject())
                .totalAmount(command.getAmount())
                .openid(command.getOpenid())
                .payerClientIp(command.getPayerClientIp())
                .attach(command.getAttach())
                .callbackOwner("RESTAURANT")
                .timeExpire(30)
                .build();
        String scene = command.getScene();
        if ("H5".equals(scene)) {
            result.setPayUrl(weChatPayService.createH5Pay(param));
        } else if ("PC".equals(scene) || "FACE_TO_FACE".equals(scene)) {
            result.setQrCode(weChatPayService.createNativePay(param));
        } else {
            if (!StringUtils.hasText(command.getOpenid())) {
                throw new BusinessException("微信支付需要提供 openid");
            }
            result.setMiniPayParams(weChatPayService.createJsapiPay(param));
        }
    }

    private void createAlipay(PaymentCreateCommand command, PaymentCreateResult result) {
        String scene = command.getScene();
        if ("MINI_PROGRAM".equals(scene) || "JSAPI".equals(scene)) {
            AlipayMiniPayParam param = new AlipayMiniPayParam();
            param.setTenantId(command.getTenantId());
            param.setOutTradeNo(command.getOrderNo());
            param.setSubject(command.getSubject());
            param.setTotalAmount(command.getAmount());
            param.setAuthCode(command.getAuthCode());
            result.setTradeNo(alipayService.createMiniPay(param));
        } else if ("PC".equals(scene) || "FACE_TO_FACE".equals(scene)) {
            AlipayPayTradeParam param = new AlipayPayTradeParam();
            param.setTenantId(command.getTenantId());
            param.setOutTradeNo(command.getOrderNo());
            param.setSubject(command.getSubject());
            param.setTotalAmount(command.getAmount());
            param.setReturnUrl(command.getReturnUrl());
            if ("PC".equals(scene)) {
                String form = alipayService.createPagePay(param);
                result.setPayForm(form);
                result.setPayUrl(form);
            } else {
                result.setQrCode(alipayService.createPrecreatePay(param));
            }
        } else {
            AlipayTradeWapPayRequestParams param = AlipayTradeWapPayRequestParams.builder()
                    .tenantId(command.getTenantId())
                    .outTradeNo(command.getOrderNo())
                    .subject(command.getSubject())
                    .totalAmount(command.getAmount())
                    .timeExpire(LocalDateTime.now().plusMinutes(30)
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .returnUrl(command.getReturnUrl())
                    .quitUrl(command.getReturnUrl())
                    .build();
            String form = alipayService.createWapPay(param);
            result.setPayForm(form);
            result.setPayUrl(form);
        }
    }
}
