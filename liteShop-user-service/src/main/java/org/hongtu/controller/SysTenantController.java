package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.hongtu.DTO.SysTenant;
import org.hongtu.Result.Result;
import org.hongtu.service.SysTenantService;
import org.hongtu.saas.domain.TenantCreateRequest;
import org.hongtu.tenant.TenantConstants;
import org.hongtu.tenant.TenantAccessValidator;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/sys-tenant")
@SaCheckRole("SUPER_ADMIN")
public class SysTenantController {

    @Resource
    private SysTenantService tenantService;

    @Resource
    private TenantAccessValidator tenantAccessValidator;

    @GetMapping("/page")
    public Result<Page<SysTenant>> page(@RequestParam(defaultValue = "1") Integer currentPage,
                                        @RequestParam(defaultValue = "10") Integer pageSize,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Integer status) {
        return tenantService.pageTenants(currentPage, pageSize, keyword, status);
    }

    @GetMapping("/{id}")
    public Result<SysTenant> detail(@PathVariable Long id) {
        SysTenant tenant = tenantService.getById(id);
        return tenant == null ? Result.error("租户不存在") : Result.success(tenant);
    }

    @PostMapping
    public Result<SysTenant> add(@RequestBody TenantCreateRequest request) {
        if (request == null || StrUtil.isBlank(request.getTenantCode()) || StrUtil.isBlank(request.getTenantName())) {
            return Result.error("商户编码和商户名称不能为空");
        }
        if (request.getPlanVersionId() == null) {
            return Result.error("创建商户时必须选择套餐");
        }
        return tenantService.addTenant(request);
    }

    @PutMapping
    public Result<SysTenant> update(@RequestBody SysTenant tenant) {
        if (tenant == null || tenant.getId() == null
                || StrUtil.isBlank(tenant.getTenantCode()) || StrUtil.isBlank(tenant.getTenantName())) {
            return Result.error("租户信息不完整");
        }
        return tenantService.updateTenant(tenant);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return tenantService.updateStatus(id, status);
    }

    /** 超级管理员进入某个租户的业务作用域；平台账号本身仍不属于该租户。 */
    @PostMapping("/{id}/enter")
    public Result<SysTenant> enterTenant(@PathVariable Long id) {
        SysTenant tenant = tenantService.getById(id);
        if (tenant == null) {
            return Result.error("租户不存在");
        }
        if (!Integer.valueOf(1).equals(tenant.getStatus())) {
            return Result.error("租户已停用");
        }
        if (tenant.getExpireTime() != null && tenant.getExpireTime().isBefore(LocalDateTime.now())) {
            return Result.error("租户服务已到期");
        }
        // 必须在写入会话前执行与请求拦截器一致的完整校验，避免无效租户作用域污染会话。
        if (!tenantAccessValidator.isActive(id)) {
            return Result.error(Result.CODE_FORBIDDEN, "商户未分配有效套餐或套餐已失效");
        }
        StpUtil.getSession().set(TenantConstants.SESSION_TENANT_ID, id);
        return Result.success("已进入租户作用域", tenant);
    }

    /** 退出租户作用域，回到平台全局视角。 */
    @PostMapping("/exit")
    public Result<Void> exitTenant() {
        StpUtil.getSession().delete(TenantConstants.SESSION_TENANT_ID);
        return Result.success("已退出租户作用域", null);
    }
}
