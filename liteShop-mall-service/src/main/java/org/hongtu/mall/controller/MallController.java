package org.hongtu.mall.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.hongtu.Result.Result;
import org.hongtu.api.dto.WeChatNotifyParseDTO;
import org.hongtu.mall.dto.MallExchangeRequest;
import org.hongtu.mall.dto.MallOrder;
import org.hongtu.mall.dto.MallProduct;
import org.hongtu.mall.dto.MallProductRequest;
import org.hongtu.mall.service.MallCatalogService;
import org.hongtu.mall.service.MallExchangeService;
import org.hongtu.tenant.TenantScope;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 积分兑换商品、后台配置和用户兑换接口。 */
@RestController
@RequestMapping("/points-mall")
public class MallController {
    @Resource private MallCatalogService catalog;
    @Resource private MallExchangeService exchange;

    /** 后台商品分页。 */
    @GetMapping("/admin/products")
    @SaCheckPermission(value = "marketing:points-mall", orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> adminProducts(@RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "10") int pageSize, @RequestParam(required = false) String name) {
        return Result.success(catalog.page(currentPage, pageSize, name, false));
    }

    /** 新增商品。 */
    @PostMapping("/admin/products")
    @SaCheckPermission(value = "marketing:points-mall", orRole = "SUPER_ADMIN")
    public Result<MallProduct> create(@RequestBody MallProductRequest request) {
        return Result.success(catalog.save(null, request));
    }

    /** 编辑商品。 */
    @PutMapping("/admin/products/{id}")
    @SaCheckPermission(value = "marketing:points-mall", orRole = "SUPER_ADMIN")
    public Result<MallProduct> update(@PathVariable Long id, @RequestBody MallProductRequest request) {
        return Result.success(catalog.save(id, request));
    }

    /** 删除未兑换商品；有历史时下架。 */
    @DeleteMapping("/admin/products/{id}")
    @SaCheckPermission(value = "marketing:points-mall", orRole = "SUPER_ADMIN")
    public Result<Void> delete(@PathVariable Long id) {
        catalog.delete(id);
        return Result.success();
    }

    /** 后台兑换订单分页。 */
    @GetMapping("/admin/orders")
    @SaCheckPermission(value = "marketing:points-mall", orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> adminOrders(@RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(exchange.page(currentPage, pageSize, null));
    }

    /** 小程序可兑换商品。 */
    @GetMapping("/products")
    public Result<Map<String, Object>> products(@RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(catalog.page(currentPage, pageSize, null, true));
    }

    /** 登录会员使用积分兑换优惠券。 */
    @PostMapping("/exchange")
    public Result<MallOrder> redeem(@RequestBody MallExchangeRequest request) {
        return Result.success(exchange.exchange(StpUtil.getLoginIdAsLong(), request));
    }

    /** 登录会员的兑换记录。 */
    @GetMapping("/my-orders")
    public Result<Map<String, Object>> myOrders(@RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(exchange.page(currentPage, pageSize, StpUtil.getLoginIdAsLong()));
    }

    /** 为自己的待支付订单获取微信小程序付款参数。 */
    @PostMapping("/orders/{id}/wechat-pay")
    public Result<Map<String, Object>> wechatPay(@PathVariable Long id, @RequestParam String openId) {
        return Result.success(exchange.createWechatPay(id, StpUtil.getLoginIdAsLong(), openId));
    }

    /** 主动向微信核对付款结果。 */
    @GetMapping("/orders/{id}/status")
    public Result<MallOrder> orderStatus(@PathVariable Long id) {
        return Result.success(exchange.syncPayment(id, StpUtil.getLoginIdAsLong()));
    }

    /** 取消未支付订单。 */
    @PostMapping("/orders/{id}/cancel")
    public Result<MallOrder> cancel(@PathVariable Long id) {
        return Result.success(exchange.cancel(id, StpUtil.getLoginIdAsLong()));
    }

    /** 微信支付通知，租户路径仅用于选择验签密钥。 */
    @PostMapping("/wechat-pay/notify/{tenantId}")
    @SaIgnore
    public Map<String, String> paymentNotify(@PathVariable Long tenantId, HttpServletRequest request,
            @RequestBody String body) {
        try {
            TenantScope.run(tenantId, () -> exchange.paymentNotify(notifyRequest(tenantId, request, body)));
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception invalid) {
            return Map.of("code", "FAIL", "message", "支付通知处理失败");
        }
    }

    /** 微信退款通知。 */
    @PostMapping("/wechat-pay/refund-notify/{tenantId}")
    @SaIgnore
    public Map<String, String> refundNotify(@PathVariable Long tenantId, HttpServletRequest request,
            @RequestBody String body) {
        try {
            TenantScope.run(tenantId, () -> exchange.refundNotify(notifyRequest(tenantId, request, body)));
            return Map.of("code", "SUCCESS", "message", "成功");
        } catch (Exception invalid) {
            return Map.of("code", "FAIL", "message", "退款通知处理失败");
        }
    }

    private WeChatNotifyParseDTO notifyRequest(Long tenantId, HttpServletRequest request, String body) {
        return WeChatNotifyParseDTO.builder().tenantId(tenantId).requestBody(body)
                .signature(request.getHeader("Wechatpay-Signature"))
                .timestamp(request.getHeader("Wechatpay-Timestamp"))
                .nonce(request.getHeader("Wechatpay-Nonce"))
                .serial(request.getHeader("Wechatpay-Serial")).build();
    }
}
