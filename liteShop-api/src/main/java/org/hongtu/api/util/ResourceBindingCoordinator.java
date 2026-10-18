package org.hongtu.api.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.api.client.FileResourceClient;
import org.hongtu.api.dto.ResourceBinding;
import org.hongtu.api.dto.ResourceBindingRequest;
import org.hongtu.api.dto.ResourceBindingPlan;
import org.hongtu.tenant.TenantContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;

/** 分库业务保存时先占用资源，提交后再释放旧引用；失败时保留引用以防误删。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceBindingCoordinator {
    private final FileResourceClient files;

    /** 使用显式租户上下文绑定业务，平台资源允许被任一租户引用。 */
    public ResourceBindingRequest request(String type, Long businessId, String field, List<ResourceBinding> bindings) {
        Long tenantId = TenantContext.getTenantIdOrNull();
        return request(tenantId == null ? 0L : tenantId, type, businessId, field, bindings);
    }

    public ResourceBindingRequest request(Long tenantId, String type, Long businessId,
                                          String field, List<ResourceBinding> bindings) {
        return new ResourceBindingRequest(tenantId, type, businessId,
                List.of(field), bindings);
    }

    /** 文件服务校验资源并先登记目标引用，同时返回按当前存储配置派生的地址。 */
    public ResourceBindingPlan prepare(ResourceBindingRequest request) {
        return FeignResultUtil.unwrap(files.prepare(request));
    }

    /** 单图同时支持显式资源 ID 和旧客户端仅提交的完整 URL。 */
    public ResourceBindingPlan prepareSingle(String type, Long id, String field, Long resourceId, String url) {
        return prepare(request(type, id, field, List.of(new ResourceBinding(field, 0, resourceId, url))));
    }

    public ResourceBindingPlan prepareSingle(Long tenantId, String type, Long id, String field, Long resourceId, String url) {
        return prepare(request(tenantId, type, id, field, List.of(new ResourceBinding(field, 0, resourceId, url))));
    }

    /** 多图按原始位置登记；没有资源 ID 的外部 URL 仍占一个位置。 */
    public ResourceBindingPlan prepareMulti(String type, Long id, String field, String ids, String urls) {
        Long tenantId = TenantContext.getTenantIdOrNull();
        return prepareMulti(tenantId == null ? 0L : tenantId, type, id, field, ids, urls);
    }

    public ResourceBindingPlan prepareMulti(Long tenantId, String type, Long id, String field, String ids, String urls) {
        List<String> imageUrls = splitUrls(urls);
        String[] imageIds = ids == null ? new String[0] : ids.split(",", -1);
        List<ResourceBinding> slots = new ArrayList<>();
        for (int i = 0; i < Math.max(imageUrls.size(), imageIds.length); i++) {
            String rawId = i < imageIds.length ? imageIds[i].trim() : "";
            String url = i < imageUrls.size() ? imageUrls.get(i) : "";
            if (rawId.isEmpty() && url.isBlank()) continue;
            slots.add(new ResourceBinding(field, i, rawId.isEmpty() ? null : Long.valueOf(rawId), url));
        }
        return prepare(request(tenantId, type, id, field, slots));
    }

    /** 将准备阶段返回的地址写入旧 URL 字段，存储平台地址不由客户端拼接。 */
    public String resolvedSingle(ResourceBindingPlan plan, String fallback) {
        return plan.bindings().isEmpty() ? fallback : plan.bindings().get(0).url();
    }

    public String resolvedMulti(ResourceBindingPlan plan, String original) {
        List<String> urls = splitUrls(original);
        for (ResourceBinding slot : plan.bindings()) {
            while (urls.size() <= slot.sortOrder()) urls.add("");
            urls.set(slot.sortOrder(), slot.url() == null ? "" : slot.url());
        }
        return joinUrls(urls, original);
    }

    /** 查询旧 URL 字段在响应中的当前地址和位置 ID。 */
    public List<ResourceBinding> fieldReferences(String type, Long id, String field) {
        LinkedHashMap<Integer, ResourceBinding> byPosition = new LinkedHashMap<>();
        for (ResourceBinding slot : references(type, id))
            if (field.equals(slot.fieldName())) byPosition.putIfAbsent(slot.sortOrder(), slot);
        return new ArrayList<>(byPosition.values());
    }

    public String currentSingle(String type, Long id, String field, String legacy) {
        return fieldReferences(type, id, field).stream().findFirst().map(ResourceBinding::url).orElse(legacy);
    }

    public Long currentSingleId(String type, Long id, String field) {
        return fieldReferences(type, id, field).stream().findFirst().map(ResourceBinding::resourceId).orElse(null);
    }

    public String currentMulti(String type, Long id, String field, String legacy) {
        List<String> urls = splitUrls(legacy);
        for (ResourceBinding slot : fieldReferences(type, id, field)) {
            while (urls.size() <= slot.sortOrder()) urls.add("");
            urls.set(slot.sortOrder(), slot.url());
        }
        return joinUrls(urls, legacy);
    }

    public String currentMultiIds(String type, Long id, String field, String legacy) {
        int count = splitUrls(legacy).size();
        List<ResourceBinding> refs = fieldReferences(type, id, field);
        for (ResourceBinding slot : refs) count = Math.max(count, slot.sortOrder() + 1);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) ids.add("");
        for (ResourceBinding slot : refs) ids.set(slot.sortOrder(), slot.resourceId().toString());
        return String.join(",", ids);
    }

    /** 回填预览只查询，不建立关系。 */
    public Long matchExact(Long tenantId, String url) {
        return FeignResultUtil.unwrap(files.match(tenantId, url));
    }

    public List<String> urlItems(String value) { return splitUrls(value); }

    public String firstUrl(String value) {
        List<String> items = splitUrls(value);
        return items.isEmpty() ? null : items.get(0);
    }

    private List<String> splitUrls(String value) {
        if (value == null || value.isBlank()) return new ArrayList<>();
        if (value.trim().startsWith("[")) {
            try { return new ArrayList<>(JSON.parseArray(value, String.class)); }
            catch (Exception error) { return new ArrayList<>(List.of(value)); }
        }
        return new ArrayList<>(List.of(value.split(",", -1)));
    }

    private String joinUrls(List<String> urls, String original) {
        if (original != null && original.trim().startsWith("[")) {
            try { return JSON.toJSONString(urls); }
            catch (Exception error) { throw new IllegalStateException("多图 URL 序列化失败", error); }
        }
        return String.join(",", urls);
    }

    /** 本地业务事务成功后才清理旧引用；清理失败仅造成保守的多余引用。 */
    public void completeAfterCommit(ResourceBindingPlan plan) {
        Runnable finish = () -> {
            try { FeignResultUtil.check(files.complete(plan)); }
            catch (RuntimeException error) {
                log.error("资源引用待对账：{} {}", plan.request().businessType(), plan.request().businessId(), error);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { finish.run(); }
            });
        } else finish.run();
    }

    /** 业务删除后移除指定字段的全部引用。 */
    public void clearAfterCommit(String type, Long businessId, String field) {
        completeAfterCommit(prepare(request(type, businessId, field, List.of())));
    }

    /** 平台管理员跨租户操作时显式指定被操作业务的租户。 */
    public void clearAfterCommit(Long tenantId, String type, Long businessId, String field) {
        completeAfterCommit(prepare(new ResourceBindingRequest(tenantId, type, businessId,
                List.of(field), List.of())));
    }

    /** 返回按位置排序的资源引用，供旧 URL 响应字段实时派生地址。 */
    public List<ResourceBinding> references(String type, Long businessId) {
        if (businessId == null) return List.of();
        Long tenantId = TenantContext.getTenantIdOrNull();
        return references(tenantId == null ? 0L : tenantId, type, businessId);
    }

    public List<ResourceBinding> references(Long tenantId, String type, Long businessId) {
        if (businessId == null) return List.of();
        try { return FeignResultUtil.unwrap(files.references(tenantId, type, businessId)); }
        catch (RuntimeException error) {
            // 读取故障时继续输出业务表的历史 URL；写入仍必须严格依赖文件服务。
            log.warn("资源引用读取失败，使用业务旧地址：{} {}", type, businessId, error);
            return List.of();
        }
    }
}
