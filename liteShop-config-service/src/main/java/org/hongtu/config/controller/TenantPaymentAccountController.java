package org.hongtu.config.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.TenantPaymentAccountView;
import org.hongtu.api.dto.TenantPaymentRuntimeDTO;
import org.hongtu.config.service.TenantPaymentAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户支付账户接口。管理端仅租户管理员可访问，运行参数仅供内部支付服务读取。
 */
@RestController
@RequiredArgsConstructor
public class TenantPaymentAccountController {
    private final TenantPaymentAccountService service;

    /** 读取当前租户的微信和支付宝账户，密钥不回显。 */
    @SaCheckPermission(value = "config:payment-account", mode = SaMode.OR, orRole = "SUPER_ADMIN")
    @GetMapping("/config/payment-account")
    public Result<List<TenantPaymentAccountView>> list() {
        return Result.success(service.listForEdit());
    }

    /** 保存当前租户的一个支付渠道。 */
    @SaCheckPermission(value = "config:payment-account", mode = SaMode.OR, orRole = "SUPER_ADMIN")
    @PutMapping("/config/payment-account")
    public Result<TenantPaymentAccountView> save(@RequestBody TenantPaymentAccountView account) {
        return Result.success(service.save(account, StpUtil.getLoginIdAsLong()));
    }

    /** 内部接口按租户和渠道读取解密后的支付参数。 */
    @GetMapping("/inner/config/payment-account")
    public Result<TenantPaymentRuntimeDTO> runtime(@RequestParam("tenantId") Long tenantId,
                                                   @RequestParam("channel") String channel) {
        return Result.success(service.runtime(tenantId, channel));
    }
}
