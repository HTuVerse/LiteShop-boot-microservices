package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.WeChatNotifyParseDTO;
import org.hongtu.wallet.dto.WalletOrder;
import org.hongtu.wallet.dto.WalletPlan;
import org.hongtu.wallet.dto.WalletRechargeRequest;
import org.hongtu.service.WalletService;
import org.hongtu.tenant.TenantScope;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 租户储值配置、会员钱包和微信充值接口。 */
@RestController
@RequestMapping("/wallet")
public class WalletController {
    @Resource private WalletService wallet;

    /** 管理端查看本租户所有储值档位。 */
    @GetMapping("/admin/plans")
    @SaCheckPermission(value = "marketing:wallet", orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> adminPlans(@RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(wallet.plans(currentPage, pageSize, false));
    }

    /** 新增本租户储值档位。 */
    @PostMapping("/admin/plans")
    @SaCheckPermission(value = "marketing:wallet", orRole = "SUPER_ADMIN")
    public Result<WalletPlan> createPlan(@RequestBody WalletPlan plan) { return Result.success(wallet.savePlan(null, plan)); }

    /** 编辑本租户储值档位。 */
    @PutMapping("/admin/plans/{id}")
    @SaCheckPermission(value = "marketing:wallet", orRole = "SUPER_ADMIN")
    public Result<WalletPlan> updatePlan(@PathVariable Long id, @RequestBody WalletPlan plan) {
        return Result.success(wallet.savePlan(id, plan));
    }

    /** 删除无订单档位；已有充值记录的档位将停用。 */
    @DeleteMapping("/admin/plans/{id}")
    @SaCheckPermission(value = "marketing:wallet", orRole = "SUPER_ADMIN")
    public Result<Void> deletePlan(@PathVariable Long id) { wallet.deletePlan(id); return Result.success(); }

    /** 管理端分页查看本租户储值订单。 */
    @GetMapping("/admin/orders")
    @SaCheckPermission(value = "marketing:wallet", orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> adminOrders(@RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(wallet.orders(currentPage, pageSize, null));
    }

    /** 会员可选的启用档位。 */
    @GetMapping("/plans")
    public Result<Map<String, Object>> plans(@RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(wallet.plans(currentPage, pageSize, true));
    }

    /** 查询当前会员的本金与赠送余额。 */
    @GetMapping("/balance")
    public Result<Map<String, Object>> balance() { return Result.success(wallet.balance(StpUtil.getLoginIdAsLong())); }

    /** 创建本人的储值订单。 */
    @PostMapping("/orders")
    public Result<WalletOrder> create(@RequestBody WalletRechargeRequest request) {
        return Result.success(wallet.create(StpUtil.getLoginIdAsLong(), request));
    }

    /** 查看本人的储值订单。 */
    @GetMapping("/my-orders")
    public Result<Map<String, Object>> myOrders(@RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(wallet.orders(currentPage, pageSize, StpUtil.getLoginIdAsLong()));
    }

    /** 获取本人待付款订单的微信小程序支付参数。 */
    @PostMapping("/orders/{id}/wechat-pay")
    public Result<Map<String, Object>> wechatPay(@PathVariable Long id, @RequestParam String openId) {
        return Result.success(wallet.wechatPay(id, StpUtil.getLoginIdAsLong(), openId));
    }

    /** 主动查询真实支付结果并重试入账。 */
    @GetMapping("/orders/{id}/status")
    public Result<WalletOrder> status(@PathVariable Long id) {
        return Result.success(wallet.sync(id, StpUtil.getLoginIdAsLong()));
    }

    /** 微信支付通知使用路径租户选择验签账户。 */
    @PostMapping("/wechat-pay/notify/{tenantId}")
    @SaIgnore
    public Map<String, String> notify(@PathVariable Long tenantId, HttpServletRequest request, @RequestBody String body) {
        try {
            WeChatNotifyParseDTO notice = WeChatNotifyParseDTO.builder().tenantId(tenantId).requestBody(body)
                    .signature(request.getHeader("Wechatpay-Signature"))
                    .timestamp(request.getHeader("Wechatpay-Timestamp"))
                    .nonce(request.getHeader("Wechatpay-Nonce"))
                    .serial(request.getHeader("Wechatpay-Serial")).build();
            TenantScope.run(tenantId, () -> wallet.notifyPaid(notice));
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception failure) {
            return Map.of("code", "FAIL", "message", "储值通知处理失败");
        }
    }
}
