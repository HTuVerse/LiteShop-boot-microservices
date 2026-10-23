package org.hongtu.saas.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.saas.domain.*;
import org.hongtu.saas.service.SaasCatalogService;
import org.hongtu.saas.service.SaasEntitlementService;
import org.hongtu.saas.service.SaasSubscriptionService;
import org.hongtu.saas.view.PlanVersionConfigurationRequest;
import org.hongtu.saas.view.PublishedPlanOptionView;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/saas/admin")
@SaCheckRole("SUPER_ADMIN")
public class SaasAdminController {
    @Resource private SaasCatalogService catalog;
    @Resource private SaasSubscriptionService subscriptions;
    @Resource private SaasEntitlementService entitlements;

    @GetMapping("/features") public Result<List<SaasFeature>> features() { return Result.success(catalog.listFeatures()); }
    @PostMapping("/features") public Result<SaasFeature> addFeature(@RequestBody SaasFeature row) { row.setId(null); return Result.success(catalog.saveFeature(row)); }
    @PutMapping("/features") public Result<SaasFeature> updateFeature(@RequestBody SaasFeature row) { return Result.success(catalog.saveFeature(row)); }
    @GetMapping("/display-features") public Result<List<SaasDisplayFeature>> displayFeatures() { return Result.success(catalog.listDisplayFeatures()); }
    @PostMapping("/display-features") public Result<SaasDisplayFeature> addDisplayFeature(@RequestBody SaasDisplayFeature row) { row.setId(null); return Result.success(catalog.saveDisplayFeature(row)); }
    @PutMapping("/display-features") public Result<SaasDisplayFeature> updateDisplayFeature(@RequestBody SaasDisplayFeature row) { return Result.success(catalog.saveDisplayFeature(row)); }

    @GetMapping("/plans") public Result<List<SaasPlan>> plans() { return Result.success(catalog.listPlans()); }
    @GetMapping("/plan-options") public Result<List<PublishedPlanOptionView>> planOptions() { return Result.success(catalog.listPublishedPlanOptions()); }
    @PostMapping("/plans") public Result<SaasPlan> addPlan(@RequestBody SaasPlan row) { row.setId(null); return Result.success(catalog.savePlan(row)); }
    @PutMapping("/plans") public Result<SaasPlan> updatePlan(@RequestBody SaasPlan row) { return Result.success(catalog.savePlan(row)); }
    @GetMapping("/plans/{planId}/versions") public Result<List<SaasPlanVersion>> versions(@PathVariable Long planId) { return Result.success(catalog.listVersions(planId)); }
    /** 批量读取所有套餐版本。 */
    @GetMapping("/plan-versions") public Result<List<SaasPlanVersion>> allVersions() { return Result.success(catalog.listAllVersions()); }
    /** 批量读取指定版本的权益。 */
    @PostMapping("/plan-versions/entitlements/batch") public Result<List<SaasPlanEntitlement>> batchEntitlements(@RequestBody List<Long> versionIds) { return Result.success(catalog.listEntitlementsForVersions(versionIds)); }
    @PostMapping("/plans/{planId}/versions") public Result<SaasPlanVersion> createVersion(@PathVariable Long planId, @RequestParam(required = false) Long copyFromVersionId) { return Result.success(catalog.createVersion(planId, copyFromVersionId)); }
    @PutMapping("/plan-versions/{versionId}") public Result<SaasPlanVersion> updateVersion(@PathVariable Long versionId, @RequestBody SaasPlanVersion row) { return Result.success(catalog.saveVersionCommercial(versionId, row)); }
    /** 原子保存套餐草稿价格及权益。 */
    @PutMapping("/plan-versions/{versionId}/configuration") public Result<SaasPlanVersion> saveVersionConfiguration(@PathVariable Long versionId, @RequestBody PlanVersionConfigurationRequest request) { return Result.success(catalog.saveVersionConfiguration(versionId, request)); }
    @DeleteMapping("/plan-versions/{versionId}") public Result<Void> deleteVersion(@PathVariable Long versionId) { catalog.deleteDraftVersion(versionId); return Result.success("套餐草稿已删除", null); }
    @GetMapping("/plan-versions/{versionId}/entitlements") public Result<List<SaasPlanEntitlement>> planRules(@PathVariable Long versionId) { return Result.success(catalog.entitlements(versionId)); }
    @PutMapping("/plan-versions/{versionId}/entitlements") public Result<List<SaasPlanEntitlement>> savePlanRules(@PathVariable Long versionId, @RequestBody List<SaasPlanEntitlement> rows) { return Result.success(catalog.saveEntitlements(versionId, rows)); }
    @PostMapping("/plan-versions/{versionId}/publish") public Result<SaasPlanVersion> publish(@PathVariable Long versionId) { return Result.success(catalog.publish(versionId)); }

    @GetMapping("/addons") public Result<List<SaasAddon>> addons() { return Result.success(catalog.listAddons()); }
    @PostMapping("/addons") public Result<SaasAddon> addAddon(@RequestBody SaasAddon row) { row.setId(null); return Result.success(catalog.saveAddon(row)); }
    @PutMapping("/addons") public Result<SaasAddon> updateAddon(@RequestBody SaasAddon row) { return Result.success(catalog.saveAddon(row)); }
    @GetMapping("/addons/{addonId}/entitlements") public Result<List<SaasAddonEntitlement>> addonRules(@PathVariable Long addonId) { return Result.success(catalog.addonEntitlements(addonId)); }
    @PutMapping("/addons/{addonId}/entitlements") public Result<List<SaasAddonEntitlement>> saveAddonRules(@PathVariable Long addonId, @RequestBody List<SaasAddonEntitlement> rows) { return Result.success(catalog.saveAddonEntitlements(addonId, rows)); }

    @GetMapping("/tenants/{tenantId}/subscriptions") public Result<List<SaasTenantSubscription>> tenantSubscriptions(@PathVariable Long tenantId) { return Result.success(subscriptions.listSubscriptions(tenantId)); }
    @PutMapping("/tenants/{tenantId}/subscription") public Result<SaasTenantSubscription> assignSubscription(@PathVariable Long tenantId, @RequestBody SaasTenantSubscription row) { return Result.success(subscriptions.assign(tenantId, row)); }
    @GetMapping("/tenants/{tenantId}/addons") public Result<List<SaasSubscriptionAddon>> tenantAddons(@PathVariable Long tenantId) { return Result.success(subscriptions.listAddons(tenantId)); }
    @PostMapping("/tenants/{tenantId}/addons") public Result<SaasSubscriptionAddon> addTenantAddon(@PathVariable Long tenantId, @RequestBody SaasSubscriptionAddon row) { return Result.success(subscriptions.addAddon(tenantId, row)); }
    @DeleteMapping("/tenants/{tenantId}/addons/{id}") public Result<Void> cancelTenantAddon(@PathVariable Long tenantId, @PathVariable Long id) { subscriptions.cancelAddon(tenantId, id); return Result.success("加购项已取消", null); }
    @GetMapping("/tenants/{tenantId}/overrides") public Result<List<SaasTenantEntitlementOverride>> tenantOverrides(@PathVariable Long tenantId) { return Result.success(subscriptions.listOverrides(tenantId)); }
    @PostMapping("/tenants/{tenantId}/overrides") public Result<SaasTenantEntitlementOverride> addOverride(@PathVariable Long tenantId, @RequestBody SaasTenantEntitlementOverride row) { return Result.success(subscriptions.saveOverride(tenantId, row)); }
    @DeleteMapping("/tenants/{tenantId}/overrides/{id}") public Result<Void> deleteOverride(@PathVariable Long tenantId, @PathVariable Long id) { subscriptions.deleteOverride(tenantId, id); return Result.success("权益调整已删除", null); }
    @GetMapping("/tenants/{tenantId}/entitlements") public Result<?> effective(@PathVariable Long tenantId) { return Result.success(entitlements.resolve(tenantId)); }
}
