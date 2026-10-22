package org.hongtu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.hongtu.DAO.*;
import org.hongtu.DTO.*;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** Platform templates are copied into tenant-owned schemes. No mutable levels are shared across tenants. */
@Service
public class UserLevelSchemeService {
    @Resource private UserLevelSchemeMapper schemes;
    @Resource private UserLevelMapper levels;
    @Resource private UserLevelTemplateGrantMapper grants;
    @Resource private SysTenantMapper tenants;
    @Resource private MemberRewardRuleService rewardRules;

    public List<UserLevelScheme> owned() {
        Long tenant = TenantContext.getTenantIdOrNull();
        if (tenant == null && !TenantContext.isSuperAdmin()) throw new BusinessException(403, "请选择租户");
        return schemes.selectList(new LambdaQueryWrapper<UserLevelScheme>()
            .eq(tenant != null, UserLevelScheme::getTenantId, tenant).isNull(tenant == null, UserLevelScheme::getTenantId)
            .orderByDesc(UserLevelScheme::getIsActive).orderByDesc(UserLevelScheme::getIsDefault).orderByAsc(UserLevelScheme::getId));
    }
    public List<UserLevelScheme> templates() {
        Long tenant = TenantContext.getTenantIdOrNull();
        if (tenant != null) return grants.available(tenant);
        requirePlatform();
        return TenantIgnore.get(() -> schemes.selectList(new LambdaQueryWrapper<UserLevelScheme>()
            .isNull(UserLevelScheme::getTenantId).eq(UserLevelScheme::getStatus, 1)
            .orderByDesc(UserLevelScheme::getIsDefault).orderByAsc(UserLevelScheme::getId)));
    }
    private void requirePlatform() {
        if (!TenantContext.isSuperAdmin() || TenantContext.getTenantIdOrNull() != null)
            throw new BusinessException(403, "请退出商户作用域后在平台范围分配等级体系");
    }
    private void requireTemplateAccess(UserLevelScheme template) {
        Long tenant = TenantContext.getTenantIdOrNull();
        if (tenant == null) { requirePlatform(); return; }
        if (!Integer.valueOf(1).equals(template.getIsDefault()) && grants.count(template.getId(),tenant)==0)
            throw new BusinessException(403, "平台尚未向当前租户分配该等级体系");
    }
    public List<SysTenant> assignedTenants(Long id) {
        requirePlatform(); writable(id);
        return grants.assignedTenants(id);
    }
    @Transactional(rollbackFor = Exception.class)
    public void assignTenants(Long id, List<Long> tenantIds) {
        requirePlatform(); lockTenant();
        var template = writable(id);
        if (!Integer.valueOf(1).equals(template.getStatus())) throw new BusinessException(400, "停用的等级体系不能分配");
        if (tenantIds == null || tenantIds.size()>1000 || tenantIds.stream().anyMatch(t -> t==null || t<=0))
            throw new BusinessException(400, "请选择有效租户，一次最多分配1000个租户");
        var ids = tenantIds.stream().distinct().toList();
        if (!ids.isEmpty() && tenants.selectCount(new LambdaQueryWrapper<SysTenant>().in(SysTenant::getId,ids)) != ids.size())
            throw new BusinessException(400, "部分租户已不存在，请刷新租户列表");
        grants.deleteForTemplate(id);
        if (!ids.isEmpty() && grants.insertAll(id,ids) != ids.size()) throw new IllegalStateException("分配等级体系失败");
    }
    /** Tenant selection copies an accessible template and activates it atomically. */
    @Transactional(rollbackFor = Exception.class)
    public UserLevelScheme useTemplate(Long id) {
        TenantContext.getTenantId(); lockTenant();
        var template = templates().stream().filter(t -> Objects.equals(t.getId(),id)).findFirst()
            .orElseThrow(() -> new BusinessException(403, "该等级体系未分配给当前租户或已停用"));
        var copy = create(template.getName(),"采用平台分配体系，商户独立维护",id);
        activate(copy.getId());
        copy.setIsActive(1);
        return copy;
    }
    public UserLevelScheme writable(Long id) {
        if (id == null) throw new BusinessException(400, "请选择等级方案");
        UserLevelScheme scheme = schemes.selectById(id);
        if (scheme == null || !Objects.equals(scheme.getTenantId(), TenantContext.getTenantIdOrNull())
                || (scheme.getTenantId() == null && !TenantContext.isSuperAdmin()))
            throw new BusinessException(403, "等级方案不存在或不属于当前租户");
        return scheme;
    }
    public List<UserLevel> schemeLevels(Long id) {
        if (id == null) throw new BusinessException(400, "请选择等级方案");
        var template = TenantIgnore.get(() -> schemes.selectOne(new LambdaQueryWrapper<UserLevelScheme>()
                .eq(UserLevelScheme::getId,id).isNull(UserLevelScheme::getTenantId).eq(UserLevelScheme::getStatus,1)));
        if (template != null) requireTemplateAccess(template);
        if (template != null) return TenantIgnore.get(() -> levels.selectList(new LambdaQueryWrapper<UserLevel>()
                .eq(UserLevel::getSchemeId,id).isNull(UserLevel::getTenantId)
                .orderByAsc(UserLevel::getRequiredExp).orderByAsc(UserLevel::getLevelCode)));
        var scheme = writable(id);
        return levels.selectList(new LambdaQueryWrapper<UserLevel>().eq(UserLevel::getSchemeId, scheme.getId())
            .orderByAsc(UserLevel::getRequiredExp).orderByAsc(UserLevel::getLevelCode));
    }
    public void lockTenant() {
        Long tenant = TenantContext.getTenantIdOrNull();
        if (tenant == null && TenantContext.isSuperAdmin()) TenantIgnore.get(() -> schemes.lockTemplates());
        if (tenant != null && schemes.lockTenant(tenant) == null) throw new BusinessException(404, "租户不存在");
    }
    public void validateLevels(List<UserLevel> rows) {
        var enabled = rows.stream().filter(l -> Integer.valueOf(1).equals(l.getStatus())).toList();
        if (enabled.stream().noneMatch(l -> Integer.valueOf(0).equals(l.getRequiredExp())))
            throw new BusinessException(400, "方案必须有一个经验值为 0 的启用等级");
        Set<Integer> codes = new HashSet<>(), exps = new HashSet<>();
        for (var level : rows) {
            if (level.getLevelCode() == null || level.getLevelCode() < 0 || level.getRequiredExp() == null || level.getRequiredExp() < 0)
                throw new BusinessException(400, "等级编码和经验值必须为非负整数");
            if (!codes.add(level.getLevelCode())) throw new BusinessException(400, "同一方案内等级编码不能重复");
            if (Integer.valueOf(1).equals(level.getStatus()) && !exps.add(level.getRequiredExp()))
                throw new BusinessException(400, "启用等级的经验门槛不能重复");
        }
    }
    @Transactional(rollbackFor = Exception.class)
    public UserLevelScheme create(String name, String description, Long copyFromId) {
        lockTenant();
        if (name == null || name.isBlank() || name.trim().length() > 80) throw new BusinessException(400, "方案名称必填且不能超过80字");
        Long tenant = TenantContext.getTenantIdOrNull();
        if (tenant == null && !TenantContext.isSuperAdmin()) throw new BusinessException(403, "请选择租户");
        List<UserLevel> source = List.of();
        Long sourceTemplateId = null;
        if (copyFromId != null) {
            // Only an explicitly identified platform template or an owned scheme may be copied.
            UserLevelScheme template = TenantIgnore.get(() -> schemes.selectOne(new LambdaQueryWrapper<UserLevelScheme>()
                    .eq(UserLevelScheme::getId, copyFromId).isNull(UserLevelScheme::getTenantId).eq(UserLevelScheme::getStatus,1)));
            if (template != null) {
                requireTemplateAccess(template);
                sourceTemplateId = template.getId();
                source = TenantIgnore.get(() -> levels.selectList(new LambdaQueryWrapper<UserLevel>()
                    .eq(UserLevel::getSchemeId, copyFromId).isNull(UserLevel::getTenantId)));
            } else { source = schemeLevels(writable(copyFromId).getId()); }
            validateLevels(source);
        }
        var now = LocalDateTime.now();
        var scheme = new UserLevelScheme().setTenantId(tenant).setName(name.trim()).setDescription(description)
            .setSourceTemplateId(sourceTemplateId).setIsActive(0).setIsDefault(0).setStatus(1).setCreateTime(now).setUpdateTime(now);
        if (schemes.insert(scheme) != 1) throw new IllegalStateException("创建等级方案失败");
        if (source.isEmpty()) source = List.of(new UserLevel().setLevelCode(0).setLevelName("普通会员").setRequiredExp(0).setStatus(1).setSort(0));
        for (UserLevel origin : source) {
            var row = new UserLevel();
            org.springframework.beans.BeanUtils.copyProperties(origin, row);
            row.setId(null).setTenantId(tenant).setSchemeId(scheme.getId()).setCreateTime(now).setUpdateTime(now);
            if (levels.insert(row) != 1) throw new IllegalStateException("复制会员等级失败");
        }
        if (copyFromId != null) rewardRules.copy(copyFromId, scheme.getId(), tenant);
        return scheme;
    }
    @Transactional(rollbackFor = Exception.class)
    public UserLevelScheme initializeTenant() {
        lockTenant();
        TenantContext.getTenantId();
        var existing = owned();
        var active = existing.stream().filter(s -> Integer.valueOf(1).equals(s.getIsActive()) && Integer.valueOf(1).equals(s.getStatus())).findFirst();
        if (active.isPresent()) return active.get();
        return useDefaultTemplate();
    }
    /** Copy and activate in one transaction; a failed copy leaves the previous scheme intact. */
    @Transactional(rollbackFor = Exception.class)
    public UserLevelScheme useDefaultTemplate() {
        TenantContext.getTenantId();
        lockTenant();
        var templates = templates();
        Long source = templates.stream().filter(s -> Integer.valueOf(1).equals(s.getIsDefault())).map(UserLevelScheme::getId).findFirst().orElse(null);
        if (source == null) throw new BusinessException(409, "平台尚未设置默认等级体系，请先在平台会员等级页面设置");
        var scheme = create("默认会员等级", "复制平台默认体系，商户独立维护", source);
        activate(scheme.getId());
        scheme.setIsActive(1);
        return scheme;
    }
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        Long tenant = TenantContext.getTenantId();
        lockTenant();
        var scheme = writable(id);
        if (!Integer.valueOf(1).equals(scheme.getStatus())) throw new BusinessException(400, "方案已停用");
        validateLevels(schemeLevels(id));
        schemes.update(new LambdaUpdateWrapper<UserLevelScheme>().eq(UserLevelScheme::getTenantId, tenant).set(UserLevelScheme::getIsActive, 0));
        if (schemes.update(new LambdaUpdateWrapper<UserLevelScheme>().eq(UserLevelScheme::getId, id).set(UserLevelScheme::getIsActive,1)
            .set(UserLevelScheme::getUpdateTime,LocalDateTime.now())) != 1) throw new IllegalStateException("切换方案失败");
        schemes.recalculateUsers(tenant, id);
    }
    public void recalculateIfActive(UserLevelScheme scheme) {
        if (scheme.getTenantId() != null && Integer.valueOf(1).equals(scheme.getIsActive())) schemes.recalculateUsers(scheme.getTenantId(), scheme.getId());
    }
    @Transactional(rollbackFor = Exception.class)
    public void setDefaultTemplate(Long id) {
        if (!TenantContext.isSuperAdmin() || TenantContext.getTenantIdOrNull() != null) throw new BusinessException(403, "请在平台范围设置模板");
        lockTenant();
        var scheme = writable(id);
        if (!Integer.valueOf(1).equals(scheme.getStatus())) throw new BusinessException(400, "停用的等级体系不能设为默认");
        validateLevels(schemeLevels(id));
        if (Integer.valueOf(1).equals(scheme.getIsDefault())) return;
        schemes.update(new LambdaUpdateWrapper<UserLevelScheme>().isNull(UserLevelScheme::getTenantId).set(UserLevelScheme::getIsDefault,0));
        if (schemes.update(new LambdaUpdateWrapper<UserLevelScheme>().eq(UserLevelScheme::getId,scheme.getId())
                .isNull(UserLevelScheme::getTenantId).set(UserLevelScheme::getIsDefault,1)
                .set(UserLevelScheme::getUpdateTime,LocalDateTime.now())) != 1)
            throw new IllegalStateException("设置默认等级体系失败");
    }
    @Transactional(rollbackFor = Exception.class)
    public void rename(Long id, String name, String description) {
        lockTenant();
        writable(id);
        if (name == null || name.isBlank() || name.length()>80) throw new BusinessException(400,"请输入80字以内的方案名称");
        schemes.update(new LambdaUpdateWrapper<UserLevelScheme>().eq(UserLevelScheme::getId,id).set(UserLevelScheme::getName,name.trim())
            .set(UserLevelScheme::getDescription,description).set(UserLevelScheme::getUpdateTime,LocalDateTime.now()));
    }
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        lockTenant(); var scheme = writable(id);
        if (Integer.valueOf(1).equals(scheme.getIsActive()) || Integer.valueOf(1).equals(scheme.getIsDefault()))
            throw new BusinessException(409,"当前启用方案或默认模板不能删除");
        if (scheme.getTenantId() == null) grants.deleteForTemplate(id);
        rewardRules.delete(id);
        levels.delete(new LambdaQueryWrapper<UserLevel>().eq(UserLevel::getSchemeId,id));
        schemes.deleteById(id);
    }
}
