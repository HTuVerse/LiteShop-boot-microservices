package org.hongtu.saas.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.saas.service.SaasEntitlementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/saas/entitlements")
@SaCheckLogin
public class SaasEntitlementController {
    @Resource private SaasEntitlementService service;

    /** 当前租户只读配额。配额变更只允许内部接口按原消费事件释放。 */
    @GetMapping("/me") public Result<?> current() { return Result.success(service.resolveCurrent()); }
}
