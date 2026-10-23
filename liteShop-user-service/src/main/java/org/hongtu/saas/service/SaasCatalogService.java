package org.hongtu.saas.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.hongtu.exception.BusinessException;
import org.hongtu.saas.constant.SaasFeatureKeys;
import org.hongtu.saas.constant.SaasFeatureType;
import org.hongtu.saas.domain.*;
import org.hongtu.saas.mapper.*;
import org.hongtu.saas.view.PublishedPlanOptionView;
import org.hongtu.saas.view.PlanVersionConfigurationRequest;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class SaasCatalogService {
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9._-]{1,99}$");
    @Resource private SaasFeatureMapper features;
    @Resource private SaasDisplayFeatureMapper displayFeatures;
    @Resource private SaasPlanMapper plans;
    @Resource private SaasPlanVersionMapper versions;
    @Resource private SaasPlanEntitlementMapper planEntitlements;
    @Resource private SaasAddonMapper addons;
    @Resource private SaasAddonEntitlementMapper addonEntitlements;
    @Resource private ObjectMapper objectMapper;

    public void requirePlatform() {
        if (!TenantContext.isSuperAdmin() || TenantContext.getTenantIdOrNull() != null) {
            throw new BusinessException(403, "请退出商户作用域后在平台范围管理套餐");
        }
    }

    public List<SaasFeature> listFeatures() {
        requirePlatform();
        return features.selectList(new LambdaQueryWrapper<SaasFeature>()
                .orderByDesc(SaasFeature::getStatus).orderByAsc(SaasFeature::getFeatureCode));
    }

    public SaasFeature saveFeature(SaasFeature row) {
        requirePlatform();
        if (row == null || row.getFeatureCode() == null || row.getFeatureName() == null) {
            throw new BusinessException(400, "功能编码和名称不能为空");
        }
        String code = row.getFeatureCode().trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) throw new BusinessException(400, "功能编码格式不正确");
        requireSellableFeature(code);
        normalizeIntegratedFeature(row, code);
        String featureType = SaasFeatureType.normalize(row.getFeatureType());
        if (!SaasFeatureType.supported(featureType)) {
            throw new BusinessException(400, "功能类型只能是 BOOLEAN、NUMBER、STRING 或 ENUM");
        }
        if (SaasFeatureType.numeric(featureType) && !Set.of("RESOURCE", "PERIODIC", "RATE", "CONCURRENT").contains(normalize(row.getQuotaMode(), ""))) {
            throw new BusinessException(400, "配额模式不正确");
        }
        String usageSource = normalize(row.getUsageSource(), SaasFeatureType.numeric(featureType) ? "COUNTER" : "NONE");
        if (!Set.of("NONE", "COUNTER", "REALTIME").contains(usageSource)) throw new BusinessException(400, "用量来源不正确");
        if ("REALTIME".equals(usageSource) && (row.getUsageMetric() == null || row.getUsageMetric().isBlank())) {
            throw new BusinessException(400, "实时用量功能必须配置 usageMetric");
        }
        if ("ENUM".equals(featureType) && (row.getAllowedValues() == null || row.getAllowedValues().isBlank())) {
            throw new BusinessException(400, "枚举功能必须配置 allowedValues JSON 数组");
        }
        if ("ENUM".equals(featureType)) {
            List<String> allowedValues = parseAllowedValues(row.getAllowedValues());
            String defaultTextValue = trimToNull(row.getDefaultTextValue());
            if (defaultTextValue != null && !allowedValues.contains(defaultTextValue)) {
                throw new BusinessException(400, "枚举默认值不在 allowedValues 中");
            }
        }
        SaasFeature duplicate = features.selectOne(new LambdaQueryWrapper<SaasFeature>()
                .eq(SaasFeature::getFeatureCode, code).ne(row.getId() != null, SaasFeature::getId, row.getId()));
        if (duplicate != null) throw new BusinessException(409, "功能编码已存在");
        LocalDateTime now = LocalDateTime.now();
        row.setFeatureCode(code).setFeatureName(row.getFeatureName().trim()).setFeatureType(featureType)
                .setUnit(SaasFeatureType.numeric(featureType) ? normalize(row.getUnit(), "COUNT") : null)
                .setQuotaMode(SaasFeatureType.numeric(featureType) ? normalize(row.getQuotaMode(), "RESOURCE") : null)
                .setUsageSource(usageSource).setUsageMetric("REALTIME".equals(usageSource) ? row.getUsageMetric().trim() : null)
                .setDefaultTextValue(SaasFeatureType.textual(featureType) ? trimToNull(row.getDefaultTextValue()) : null)
                .setAllowedValues("ENUM".equals(featureType) ? row.getAllowedValues() : null)
                .setStatus(row.getStatus() == null ? 1 : row.getStatus()).setUpdateTime(now);
        if (!SaasFeatureType.numeric(featureType) && !"BOOLEAN".equals(featureType)) row.setDefaultValue(null);
        if (row.getId() == null) {
            row.setCreateTime(now);
            features.insert(row);
        } else {
            SaasFeature old = features.selectById(row.getId());
            if (old == null) throw new BusinessException(404, "功能不存在");
            if (!Objects.equals(old.getFeatureCode(), code)) throw new BusinessException(409, "已创建功能的编码不可修改");
            row.setCreateTime(old.getCreateTime());
            features.updateById(row);
        }
        return row;
    }

    public List<SaasPlan> listPlans() {
        requirePlatform();
        return plans.selectList(new LambdaQueryWrapper<SaasPlan>().orderByDesc(SaasPlan::getStatus)
                .orderByAsc(SaasPlan::getSortOrder).orderByAsc(SaasPlan::getPlanCode));
    }

    /** 新租户只允许选择每个启用套餐最新的已发布版本。 */
    public List<PublishedPlanOptionView> listPublishedPlanOptions() {
        requirePlatform();
        List<SaasPlan> activePlans = plans.selectList(new LambdaQueryWrapper<SaasPlan>()
                .eq(SaasPlan::getStatus, 1).orderByAsc(SaasPlan::getSortOrder).orderByAsc(SaasPlan::getPlanCode));
        if (activePlans.isEmpty()) return List.of();
        List<Long> planIds = activePlans.stream().map(SaasPlan::getId).toList();
        Map<Long, SaasPlanVersion> latestByPlan = new HashMap<>();
        versions.selectList(new LambdaQueryWrapper<SaasPlanVersion>()
                        .in(SaasPlanVersion::getPlanId, planIds)
                        .eq(SaasPlanVersion::getStatus, "PUBLISHED")
                        .orderByDesc(SaasPlanVersion::getVersionNo))
                .forEach(version -> latestByPlan.putIfAbsent(version.getPlanId(), version));
        return activePlans.stream().map(plan -> {
                    SaasPlanVersion version = latestByPlan.get(plan.getId());
                    return version == null ? null : new PublishedPlanOptionView()
                            .setPlanId(plan.getId()).setPlanVersionId(version.getId())
                            .setPlanCode(plan.getPlanCode()).setPlanName(plan.getPlanName())
                            .setDescription(plan.getDescription()).setVersionNo(version.getVersionNo())
                            .setMonthlyPrice(version.getMonthlyPrice()).setYearlyPrice(version.getYearlyPrice())
                            .setCurrency(version.getCurrency());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public SaasPlan savePlan(SaasPlan row) {
        requirePlatform();
        if (row == null || row.getPlanCode() == null || row.getPlanName() == null) throw new BusinessException(400, "套餐编码和名称不能为空");
        String code = row.getPlanCode().trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) throw new BusinessException(400, "套餐编码格式不正确");
        if (plans.selectCount(new LambdaQueryWrapper<SaasPlan>().eq(SaasPlan::getPlanCode, code)
                .ne(row.getId() != null, SaasPlan::getId, row.getId())) > 0) throw new BusinessException(409, "套餐编码已存在");
        LocalDateTime now = LocalDateTime.now();
        row.setPlanCode(code).setPlanName(row.getPlanName().trim())
                .setTargetCustomer(trimToNull(row.getTargetCustomer()))
                .setCoreBenefits(trimToNull(row.getCoreBenefits()))
                .setDescription(trimToNull(row.getDescription()))
                .setRecommended(Integer.valueOf(1).equals(row.getRecommended()) ? 1 : 0)
                .setSortOrder(row.getSortOrder() == null ? 0 : row.getSortOrder())
                .setStatus(row.getStatus() == null ? 1 : row.getStatus()).setUpdateTime(now);
        if (row.getRecommended() == 1) {
            plans.update(new LambdaUpdateWrapper<SaasPlan>()
                    .ne(row.getId() != null, SaasPlan::getId, row.getId())
                    .set(SaasPlan::getRecommended, 0));
        }
        if (row.getId() == null) { row.setCreateTime(now); plans.insert(row); }
        else {
            SaasPlan old = plans.selectById(row.getId());
            if (old == null) throw new BusinessException(404, "套餐不存在");
            if (!Objects.equals(old.getPlanCode(), code)) throw new BusinessException(409, "套餐编码不可修改");
            row.setCreateTime(old.getCreateTime()); plans.updateById(row);
        }
        return row;
    }

    public List<SaasDisplayFeature> listDisplayFeatures() {
        requirePlatform();
        return displayFeatures.selectList(new LambdaQueryWrapper<SaasDisplayFeature>()
                .orderByDesc(SaasDisplayFeature::getStatus)
                .orderByAsc(SaasDisplayFeature::getModuleCode)
                .orderByAsc(SaasDisplayFeature::getSortOrder)
                .orderByAsc(SaasDisplayFeature::getFeatureCode));
    }

    public SaasDisplayFeature saveDisplayFeature(SaasDisplayFeature row) {
        requirePlatform();
        if (row == null || row.getModuleCode() == null || row.getModuleName() == null
                || row.getFeatureCode() == null || row.getFeatureName() == null) {
            throw new BusinessException(400, "模块和功能信息不能为空");
        }
        String moduleCode = row.getModuleCode().trim().toLowerCase(Locale.ROOT);
        String featureCode = row.getFeatureCode().trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(moduleCode).matches() || !CODE.matcher(featureCode).matches()) {
            throw new BusinessException(400, "模块编码或功能编码格式不正确");
        }
        if (displayFeatures.selectCount(new LambdaQueryWrapper<SaasDisplayFeature>()
                .eq(SaasDisplayFeature::getFeatureCode, featureCode)
                .ne(row.getId() != null, SaasDisplayFeature::getId, row.getId())) > 0) {
            throw new BusinessException(409, "展示功能编码已存在");
        }
        LocalDateTime now = LocalDateTime.now();
        row.setModuleCode(moduleCode).setModuleName(row.getModuleName().trim())
                .setFeatureCode(featureCode).setFeatureName(row.getFeatureName().trim())
                .setDescription(trimToNull(row.getDescription()))
                .setSortOrder(row.getSortOrder() == null ? 0 : row.getSortOrder())
                .setStatus(row.getStatus() == null ? 1 : row.getStatus()).setUpdateTime(now);
        if (row.getId() == null) {
            row.setCreateTime(now);
            displayFeatures.insert(row);
        } else {
            SaasDisplayFeature old = displayFeatures.selectById(row.getId());
            if (old == null) throw new BusinessException(404, "展示功能不存在");
            if (!Objects.equals(old.getFeatureCode(), featureCode)) {
                throw new BusinessException(409, "已创建展示功能的编码不可修改");
            }
            row.setCreateTime(old.getCreateTime());
            displayFeatures.updateById(row);
        }
        return row;
    }

    public List<SaasPlanVersion> listVersions(Long planId) {
        requirePlatform();
        return versions.selectList(new LambdaQueryWrapper<SaasPlanVersion>()
                .eq(SaasPlanVersion::getPlanId, planId).orderByDesc(SaasPlanVersion::getVersionNo));
    }

    /** 一次读取平台全部套餐版本，避免按套餐逐个请求。 */
    public List<SaasPlanVersion> listAllVersions() {
        requirePlatform();
        return versions.selectList(new LambdaQueryWrapper<SaasPlanVersion>()
                .orderByAsc(SaasPlanVersion::getPlanId).orderByDesc(SaasPlanVersion::getVersionNo));
    }

    /** 批量读取对比表所需版本的权益。 */
    public List<SaasPlanEntitlement> listEntitlementsForVersions(List<Long> versionIds) {
        requirePlatform();
        if (versionIds == null || versionIds.isEmpty()) return List.of();
        return planEntitlements.selectList(new LambdaQueryWrapper<SaasPlanEntitlement>()
                .in(SaasPlanEntitlement::getPlanVersionId, versionIds)
                .orderByAsc(SaasPlanEntitlement::getPlanVersionId)
                .orderByAsc(SaasPlanEntitlement::getFeatureId));
    }

    /** 同一事务保存价格和权益，任何校验或写入失败都回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public SaasPlanVersion saveVersionConfiguration(Long versionId, PlanVersionConfigurationRequest request) {
        if (request == null || request.version() == null) {
            throw new BusinessException(400, "套餐版本配置不能为空");
        }
        SaasPlanVersion version = saveVersionCommercial(versionId, request.version());
        saveEntitlements(versionId, request.entitlements());
        return version;
    }

    @Transactional(rollbackFor = Exception.class)
    public SaasPlanVersion createVersion(Long planId, Long copyFromVersionId) {
        requirePlatform();
        if (plans.selectById(planId) == null) throw new BusinessException(404, "套餐不存在");
        int next = listVersions(planId).stream().map(SaasPlanVersion::getVersionNo).filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
        LocalDateTime now = LocalDateTime.now();
        SaasPlanVersion row = new SaasPlanVersion().setPlanId(planId).setVersionNo(next).setStatus("DRAFT")
                .setCurrency("CNY").setCreateTime(now).setUpdateTime(now);
        versions.insert(row);
        if (copyFromVersionId != null) {
            SaasPlanVersion source = versions.selectById(copyFromVersionId);
            if (source == null || !Objects.equals(source.getPlanId(), planId)) throw new BusinessException(400, "复制来源不属于当前套餐");
            row.setMonthlyPrice(source.getMonthlyPrice()).setYearlyPrice(source.getYearlyPrice()).setCurrency(source.getCurrency());
            versions.updateById(row);
            for (SaasPlanEntitlement origin : entitlements(copyFromVersionId)) {
                planEntitlements.insert(new SaasPlanEntitlement().setPlanVersionId(row.getId()).setFeatureId(origin.getFeatureId())
                        .setEnabled(origin.getEnabled()).setQuotaValue(origin.getQuotaValue()).setTextValue(origin.getTextValue()).setUnlimited(origin.getUnlimited())
                        .setResetPeriod(origin.getResetPeriod()).setEnforcement(origin.getEnforcement()).setCreateTime(now).setUpdateTime(now));
            }
        }
        return row;
    }

    public SaasPlanVersion saveVersionCommercial(Long versionId, SaasPlanVersion request) {
        requirePlatform();
        SaasPlanVersion row = versions.selectById(versionId);
        if (row == null) throw new BusinessException(404, "套餐版本不存在");
        if (!"DRAFT".equals(row.getStatus())) throw new BusinessException(409, "已发布套餐版本的价格不可修改，请创建新版本");
        BigDecimal monthly = nonNegative(request == null ? null : request.getMonthlyPrice(), "月价格");
        BigDecimal yearly = nonNegative(request == null ? null : request.getYearlyPrice(), "年价格");
        String currency = normalize(request == null ? null : request.getCurrency(), "CNY");
        if (!currency.matches("^[A-Z]{3}$")) throw new BusinessException(400, "币种必须是三位 ISO 代码");
        row.setMonthlyPrice(monthly).setYearlyPrice(yearly).setCurrency(currency).setUpdateTime(LocalDateTime.now());
        versions.updateById(row);
        return row;
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDraftVersion(Long versionId) {
        requirePlatform();
        SaasPlanVersion row = versions.selectById(versionId);
        if (row == null) throw new BusinessException(404, "套餐版本不存在");
        if (!"DRAFT".equals(row.getStatus())) throw new BusinessException(409, "仅草稿版本可以删除");
        planEntitlements.delete(new LambdaQueryWrapper<SaasPlanEntitlement>()
                .eq(SaasPlanEntitlement::getPlanVersionId, versionId));
        versions.deleteById(versionId);
    }

    public List<SaasPlanEntitlement> entitlements(Long versionId) {
        requirePlatform();
        return planEntitlements.selectList(new LambdaQueryWrapper<SaasPlanEntitlement>()
                .eq(SaasPlanEntitlement::getPlanVersionId, versionId).orderByAsc(SaasPlanEntitlement::getFeatureId));
    }

    @Transactional(rollbackFor = Exception.class)
    public List<SaasPlanEntitlement> saveEntitlements(Long versionId, List<SaasPlanEntitlement> rows) {
        requirePlatform();
        SaasPlanVersion version = versions.selectById(versionId);
        if (version == null) throw new BusinessException(404, "套餐版本不存在");
        if (!"DRAFT".equals(version.getStatus())) throw new BusinessException(409, "已发布套餐版本不可修改，请创建新版本");
        List<SaasPlanEntitlement> safe = rows == null ? List.of() : rows;
        if (safe.stream().map(SaasPlanEntitlement::getFeatureId).anyMatch(Objects::isNull)
                || safe.stream().map(SaasPlanEntitlement::getFeatureId).distinct().count() != safe.size()) {
            throw new BusinessException(400, "功能不能为空或重复");
        }
        Set<Long> ids = new HashSet<>(safe.stream().map(SaasPlanEntitlement::getFeatureId).toList());
        if (!ids.isEmpty() && features.selectCount(new LambdaQueryWrapper<SaasFeature>().in(SaasFeature::getId, ids).eq(SaasFeature::getStatus, 1)) != ids.size()) {
            throw new BusinessException(400, "包含不存在或已停用的功能");
        }
        planEntitlements.delete(new LambdaQueryWrapper<SaasPlanEntitlement>().eq(SaasPlanEntitlement::getPlanVersionId, versionId));
        LocalDateTime now = LocalDateTime.now();
        Map<Long, SaasFeature> featureById = new HashMap<>();
        if (!ids.isEmpty()) features.selectBatchIds(ids).forEach(feature -> featureById.put(feature.getId(), feature));
        for (SaasPlanEntitlement row : safe) {
            requireSellableFeature(featureById.get(row.getFeatureId()));
            normalizeEntitlement(row, featureById.get(row.getFeatureId()));
            row.setId(null).setPlanVersionId(versionId).setCreateTime(now).setUpdateTime(now);
            planEntitlements.insert(row);
        }
        return entitlements(versionId);
    }

    public SaasPlanVersion publish(Long versionId) {
        requirePlatform();
        SaasPlanVersion row = versions.selectById(versionId);
        if (row == null) throw new BusinessException(404, "套餐版本不存在");
        if ("PUBLISHED".equals(row.getStatus())) return row;
        if (!"DRAFT".equals(row.getStatus())) throw new BusinessException(409, "当前版本不可发布");
        if (planEntitlements.selectCount(new LambdaQueryWrapper<SaasPlanEntitlement>().eq(SaasPlanEntitlement::getPlanVersionId, versionId)) == 0) {
            throw new BusinessException(409, "请先配置至少一项套餐权益");
        }
        List<SaasPlanEntitlement> rules = planEntitlements.selectList(new LambdaQueryWrapper<SaasPlanEntitlement>()
                .eq(SaasPlanEntitlement::getPlanVersionId, versionId));
        List<Long> featureIds = rules.stream().map(SaasPlanEntitlement::getFeatureId).distinct().toList();
        features.selectBatchIds(featureIds).forEach(this::requireSellableFeature);
        SaasFeature storeCount = features.selectOne(new LambdaQueryWrapper<SaasFeature>()
                .eq(SaasFeature::getFeatureCode, SaasFeatureKeys.STORE_COUNT));
        boolean hasStoreQuota = storeCount != null && rules.stream().anyMatch(rule ->
                Objects.equals(rule.getFeatureId(), storeCount.getId())
                        && Integer.valueOf(1).equals(rule.getEnabled())
                        && (Integer.valueOf(1).equals(rule.getUnlimited())
                        || (rule.getQuotaValue() != null && rule.getQuotaValue().compareTo(BigDecimal.ONE) >= 0)));
        if (!hasStoreQuota) {
            throw new BusinessException(409, "套餐必须配置至少 1 家门店或不限门店；基本功能不需要配置为权益");
        }
        LocalDateTime now = LocalDateTime.now();
        versions.update(new LambdaUpdateWrapper<SaasPlanVersion>().eq(SaasPlanVersion::getId, versionId)
                .set(SaasPlanVersion::getStatus, "PUBLISHED").set(SaasPlanVersion::getPublishedAt, now).set(SaasPlanVersion::getUpdateTime, now));
        row.setStatus("PUBLISHED").setPublishedAt(now).setUpdateTime(now);
        return row;
    }

    public List<SaasAddon> listAddons() {
        requirePlatform();
        return addons.selectList(new LambdaQueryWrapper<SaasAddon>().orderByDesc(SaasAddon::getStatus)
                .orderByAsc(SaasAddon::getSortOrder).orderByAsc(SaasAddon::getAddonCode));
    }

    public SaasAddon saveAddon(SaasAddon row) {
        requirePlatform();
        if (row == null || row.getAddonCode() == null || row.getAddonName() == null) throw new BusinessException(400, "加购项编码和名称不能为空");
        String code = row.getAddonCode().trim().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) throw new BusinessException(400, "加购项编码格式不正确");
        if (addons.selectCount(new LambdaQueryWrapper<SaasAddon>().eq(SaasAddon::getAddonCode, code)
                .ne(row.getId() != null, SaasAddon::getId, row.getId())) > 0) throw new BusinessException(409, "加购项编码已存在");
        LocalDateTime now = LocalDateTime.now();
        row.setAddonCode(code).setAddonName(row.getAddonName().trim()).setMaxQuantity(row.getMaxQuantity() == null ? 1 : row.getMaxQuantity())
                .setMonthlyPrice(nonNegative(row.getMonthlyPrice(), "加购月价格")).setYearlyPrice(nonNegative(row.getYearlyPrice(), "加购年价格"))
                .setCurrency(normalize(row.getCurrency(), "CNY")).setSortOrder(row.getSortOrder() == null ? 0 : row.getSortOrder())
                .setStatus(row.getStatus() == null ? 1 : row.getStatus()).setUpdateTime(now);
        if (row.getMaxQuantity() < 1) throw new BusinessException(400, "最大购买数量必须大于0");
        if (!row.getCurrency().matches("^[A-Z]{3}$")) throw new BusinessException(400, "币种必须是三位 ISO 代码");
        if (row.getId() == null) { row.setCreateTime(now); addons.insert(row); }
        else { SaasAddon old = addons.selectById(row.getId()); if (old == null) throw new BusinessException(404, "加购项不存在"); row.setCreateTime(old.getCreateTime()); addons.updateById(row); }
        return row;
    }

    public List<SaasAddonEntitlement> addonEntitlements(Long addonId) {
        requirePlatform();
        return addonEntitlements.selectList(new LambdaQueryWrapper<SaasAddonEntitlement>().eq(SaasAddonEntitlement::getAddonId, addonId));
    }

    @Transactional(rollbackFor = Exception.class)
    public List<SaasAddonEntitlement> saveAddonEntitlements(Long addonId, List<SaasAddonEntitlement> rows) {
        requirePlatform();
        if (addons.selectById(addonId) == null) throw new BusinessException(404, "加购项不存在");
        List<SaasAddonEntitlement> safe = rows == null ? List.of() : rows;
        if (safe.stream().map(SaasAddonEntitlement::getFeatureId).anyMatch(Objects::isNull)
                || safe.stream().map(SaasAddonEntitlement::getFeatureId).distinct().count() != safe.size()) throw new BusinessException(400, "加购功能不能为空或重复");
        addonEntitlements.delete(new LambdaQueryWrapper<SaasAddonEntitlement>().eq(SaasAddonEntitlement::getAddonId, addonId));
        for (SaasAddonEntitlement row : safe) {
            if (!Set.of("ENABLE", "ADD", "SET").contains(row.getOperation())) throw new BusinessException(400, "加购权益操作不正确");
            SaasFeature feature = features.selectById(row.getFeatureId());
            if (feature == null || !Integer.valueOf(1).equals(feature.getStatus())) throw new BusinessException(400, "包含不存在或已停用的功能");
            requireSellableFeature(feature);
            validateOperation(feature, row.getOperation(), row.getQuotaValue(), row.getTextValue(), "加购权益");
            row.setId(null).setAddonId(addonId).setCreateTime(LocalDateTime.now()); addonEntitlements.insert(row);
        }
        return addonEntitlements(addonId);
    }

    private void normalizeEntitlement(SaasPlanEntitlement row, SaasFeature feature) {
        row.setEnabled(row.getEnabled() == null ? 0 : row.getEnabled()).setUnlimited(row.getUnlimited() == null ? 0 : row.getUnlimited())
                .setResetPeriod(normalize(row.getResetPeriod(), "NONE")).setEnforcement(normalize(row.getEnforcement(), "HARD"));
        if (row.getQuotaValue() != null && row.getQuotaValue().signum() < 0) throw new BusinessException(400, "配额不能为负数");
        if (feature == null) throw new BusinessException(400, "功能不存在");
        if (SaasFeatureType.numeric(feature.getFeatureType())) {
            row.setTextValue(null);
            if (row.getEnabled() == 1 && row.getUnlimited() != 1 && row.getQuotaValue() == null) throw new BusinessException(400, feature.getFeatureName() + " 必须配置额度或不限量");
            if (SaasFeatureKeys.STORE_COUNT.equals(feature.getFeatureCode())) {
                row.setResetPeriod("NONE").setEnforcement("HARD");
                if (row.getEnabled() == 1 && row.getUnlimited() != 1
                        && (!positiveInteger(row.getQuotaValue()))) {
                    throw new BusinessException(400, "门店数量必须是大于 0 的整数，或设置为不限量");
                }
            } else if (SaasFeatureKeys.AI_MANAGER_CALLS.equals(feature.getFeatureCode())) {
                row.setUnlimited(0).setResetPeriod("BILLING_CYCLE").setEnforcement("HARD");
                if (row.getEnabled() == 1 && !positiveInteger(row.getQuotaValue())) {
                    throw new BusinessException(400, "AI 店长生成次数必须是大于 0 的整数");
                }
            }
        } else {
            row.setQuotaValue(null).setUnlimited(0).setResetPeriod("NONE");
            if (SaasFeatureType.textual(feature.getFeatureType()) && row.getEnabled() == 1 && trimToNull(row.getTextValue()) == null) {
                throw new BusinessException(400, feature.getFeatureName() + " 必须配置文本/枚举值");
            }
            validateTextValue(feature, row.getTextValue());
        }
    }

    void validateOperation(SaasFeature feature, String operation, BigDecimal numberValue, String textValue, String label) {
        if (SaasFeatureKeys.STORE_COUNT.equals(feature.getFeatureCode())) {
            if ("加购权益".equals(label) && (!"ADD".equals(operation) || !positiveInteger(numberValue))) {
                throw new BusinessException(400, "门店加购项只能增加大于 0 的整数门店数");
            }
            if ("ADD".equals(operation) && !positiveInteger(numberValue)) {
                throw new BusinessException(400, label + "：增加的门店数必须是大于 0 的整数");
            }
            if ("SET".equals(operation) && (numberValue == null || numberValue.signum() < 0
                    || numberValue.stripTrailingZeros().scale() > 0)) {
                throw new BusinessException(400, label + "：门店数量必须是非负整数");
            }
        }
        if (SaasFeatureKeys.AI_MANAGER_CALLS.equals(feature.getFeatureCode())) {
            if ("加购权益".equals(label) && (!"ADD".equals(operation) || !positiveInteger(numberValue))) {
                throw new BusinessException(400, "AI 店长次数包只能增加大于 0 的整数次数");
            }
            if ("ADD".equals(operation) && !positiveInteger(numberValue)) {
                throw new BusinessException(400, label + "：增加次数必须是大于 0 的整数");
            }
            if ("SET".equals(operation) && (numberValue == null || numberValue.signum() < 0
                    || numberValue.stripTrailingZeros().scale() > 0)) {
                throw new BusinessException(400, label + "：设置次数必须是非负整数");
            }
        }
        if ("ADD".equals(operation) && !SaasFeatureType.numeric(feature.getFeatureType())) throw new BusinessException(400, label + "：非数值功能不能 ADD");
        if ("BOOLEAN".equals(SaasFeatureType.normalize(feature.getFeatureType())) && !Set.of("ENABLE", "DISABLE").contains(operation)) {
            throw new BusinessException(400, label + "：开关功能只能 ENABLE 或 DISABLE");
        }
        if (SaasFeatureType.textual(feature.getFeatureType()) && !"SET".equals(operation)) {
            throw new BusinessException(400, label + "：文本或枚举功能只能 SET");
        }
        if (SaasFeatureType.numeric(feature.getFeatureType()) && !"ENABLE".equals(operation)
                && (numberValue == null || numberValue.signum() < 0)) throw new BusinessException(400, label + "额度不能为空或负数");
        if (SaasFeatureType.textual(feature.getFeatureType()) && "SET".equals(operation) && trimToNull(textValue) == null) {
            throw new BusinessException(400, label + "文本值不能为空");
        }
        validateTextValue(feature, textValue);
    }

    public void requireSellableFeature(SaasFeature feature) {
        if (feature == null) throw new BusinessException(400, "功能不存在");
        requireSellableFeature(feature.getFeatureCode());
    }

    public void requireSellableFeature(String featureCode) {
        if (!SaasFeatureKeys.isSellable(featureCode)) {
            throw new BusinessException(409, "功能 " + featureCode + " 尚未接入业务后端，不能配置为套餐或加购权益");
        }
    }

    /** 技术字段由业务接入代码定义，不能在后台改成不受控的统计或软能力。 */
    private void normalizeIntegratedFeature(SaasFeature row, String featureCode) {
        if (SaasFeatureKeys.STORE_COUNT.equals(featureCode)) {
            row.setFeatureType("NUMBER").setUnit("STORE").setQuotaMode("RESOURCE")
                    .setUsageSource("REALTIME").setUsageMetric(SaasFeatureKeys.STORE_COUNT)
                    .setDefaultValue(null).setDefaultTextValue(null).setAllowedValues(null);
        } else if (SaasFeatureKeys.RESOURCE_STORAGE_BYTES.equals(featureCode)) {
            row.setFeatureType("NUMBER").setUnit("BYTE").setQuotaMode("RESOURCE")
                    .setUsageSource("REALTIME").setUsageMetric(SaasFeatureKeys.RESOURCE_STORAGE_BYTES)
                    .setDefaultValue(BigDecimal.valueOf(1073741824L)).setDefaultTextValue(null).setAllowedValues(null);
        } else if (SaasFeatureKeys.AI_MANAGER.equals(featureCode)) {
            // AI 店长只控制租户是否开通；模型 Key 和额度仍由平台统一管理。
            row.setFeatureType("BOOLEAN").setUnit(null).setQuotaMode(null)
                    .setUsageSource("NONE").setUsageMetric(null)
                    .setDefaultValue(BigDecimal.ZERO).setDefaultTextValue(null).setAllowedValues(null);
        } else if (SaasFeatureKeys.AI_MANAGER_CALLS.equals(featureCode)) {
            // 调用次数按订阅周期原子累计，套餐和加购可统一叠加。
            row.setFeatureType("NUMBER").setUnit("REQUEST").setQuotaMode("PERIODIC")
                    .setUsageSource("COUNTER").setUsageMetric(null)
                    .setDefaultValue(null).setDefaultTextValue(null).setAllowedValues(null);
        }
    }

    private boolean positiveInteger(BigDecimal value) {
        return value != null && value.signum() > 0 && value.stripTrailingZeros().scale() <= 0;
    }

    private void validateTextValue(SaasFeature feature, String textValue) {
        if (!"ENUM".equals(SaasFeatureType.normalize(feature.getFeatureType())) || trimToNull(textValue) == null) return;
        if (!parseAllowedValues(feature.getAllowedValues()).contains(textValue.trim())) {
            throw new BusinessException(400, feature.getFeatureName() + " 的值不在 allowedValues 中");
        }
    }

    private List<String> parseAllowedValues(String json) {
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<>() {});
            if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new BusinessException(400, "allowedValues 必须是非空字符串数组");
            }
            return values;
        } catch (BusinessException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(400, "allowedValues 必须是 JSON 字符串数组", error);
        }
    }

    private BigDecimal nonNegative(BigDecimal value, String label) {
        if (value != null && value.signum() < 0) throw new BusinessException(400, label + "不能为负数");
        return value;
    }

    private String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    private String normalize(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT); }
}
