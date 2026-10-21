package org.hongtu.mall.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.mall.dto.MallExchangeRequest;
import org.hongtu.mall.dto.MallOrder;
import org.hongtu.mall.dto.MallProduct;
import org.hongtu.mall.dto.MallProductRequest;
import org.hongtu.mall.service.MallCatalogService;
import org.hongtu.mall.service.MallExchangeService;
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
}
