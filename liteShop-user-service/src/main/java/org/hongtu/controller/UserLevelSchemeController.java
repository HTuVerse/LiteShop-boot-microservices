package org.hongtu.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import jakarta.annotation.Resource;
import org.hongtu.Result.Result;
import org.hongtu.DTO.*;
import org.hongtu.service.UserLevelSchemeService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/user-level/schemes")
@SaCheckPermission(value="system:level", orRole="SUPER_ADMIN")
public class UserLevelSchemeController {
    @Resource private UserLevelSchemeService service;
    public record SchemeRequest(String name, String description, Long copyFromId) {}
    @GetMapping public Result list() { return Result.success(Map.of("schemes",service.owned(),"templates",service.templates())); }
    @GetMapping("/{id}/levels") public Result levels(@PathVariable Long id) { return Result.success(service.schemeLevels(id)); }
    @PostMapping @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:level:scheme-add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result create(@RequestBody SchemeRequest body) { return Result.success(service.create(body.name(),body.description(),body.copyFromId())); }
    @PutMapping("/{id}") @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:level:scheme-edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result rename(@PathVariable Long id,@RequestBody SchemeRequest body) { service.rename(id,body.name(),body.description());return Result.success("方案已更新"); }
    @GetMapping("/{id}/tenants") public Result assignedTenants(@PathVariable Long id) { return Result.success(service.assignedTenants(id)); }
    @PutMapping("/{id}/tenants") public Result assignTenants(@PathVariable Long id,@RequestBody List<Long> tenantIds) { service.assignTenants(id,tenantIds);return Result.success("租户可选等级体系已更新"); }
    @PostMapping("/{id}/use-template") @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:level:scheme-add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result useTemplate(@PathVariable Long id) {return Result.success(service.useTemplate(id));}
    @PostMapping("/use-default") @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:level:scheme-add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result useDefault() { return Result.success(service.useDefaultTemplate()); }
    @PostMapping("/{id}/activate") @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:level:scheme-activate"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result activate(@PathVariable Long id) {service.activate(id);return Result.success("方案已启用，会员等级已按原经验值重新计算");}
    @PostMapping("/{id}/default") public Result setDefault(@PathVariable Long id) {service.setDefaultTemplate(id);return Result.success("默认模板已更新");}
    @DeleteMapping("/{id}") @cn.dev33.satoken.annotation.SaCheckPermission(value = {"system:level:scheme-delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result delete(@PathVariable Long id) {service.delete(id);return Result.success("方案已删除");}
}
