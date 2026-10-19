package org.hongtu.storage;

import lombok.RequiredArgsConstructor;
import org.hongtu.DAO.FileUploadMapper;
import org.hongtu.DTO.FileUpload;
import org.hongtu.api.dto.ResourceBinding;
import org.hongtu.api.dto.ResourceBindingPlan;
import org.hongtu.api.dto.ResourceBindingRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 在文件库维护统一引用、URL 精确匹配和并发删除状态。 */
@Service
@RequiredArgsConstructor
public class ResourceReferenceService {
    private static final Pattern KEY_TAIL = Pattern.compile("(?:^|/)([0-9]{8}/[a-f0-9]{32}\\.[a-z0-9]{1,10})$");
    private static final Map<String, Set<String>> FIELDS = Map.of(
            "BANNER", Set.of("imageUrl"), "DISH", Set.of("dishPics"),
            "CATEGORY", Set.of("categoryPic", "categoryIcon"), "STORE", Set.of("storeImages"),
            "TEMPLATE", Set.of("logoUrl"), "USER", Set.of("avatar"),
            "SPEC", Set.of("specValues"), "WEBSITE", Set.of("configValue"));

    private final JdbcTemplate jdbc;
    private final FileUploadMapper files;
    private final StorageManager storage;

    /** 在业务写入前锁定并登记所有资源；外部 URL 保持原值且不建引用。 */
    @Transactional(rollbackFor = Exception.class)
    public ResourceBindingPlan prepare(ResourceBindingRequest request) {
        validate(request);
        List<Long> previous = new ArrayList<>();
        for (String field : request.fields()) {
            previous.addAll(jdbc.queryForList("SELECT id FROM file_resource_reference WHERE tenant_id=? "
                    + "AND business_type=? AND business_id=? AND field_name=?",
                    Long.class, request.tenantId(), request.businessType(), request.businessId(), field));
        }
        List<ResourceBinding> resolved = new ArrayList<>();
        Set<String> positions = new HashSet<>();
        for (ResourceBinding slot : request.bindings()) {
            if (!request.fields().contains(slot.fieldName()) || slot.sortOrder() == null || slot.sortOrder() < 0
                    || !positions.add(slot.fieldName() + ":" + slot.sortOrder()))
                throw new IllegalArgumentException("资源位置不合法或重复");
            Long id = slot.resourceId() == null ? matchExactPublicUrl(slot.url()) : slot.resourceId();
            if (id == null) {
                resolved.add(new ResourceBinding(slot.fieldName(), slot.sortOrder(), null, slot.url()));
                continue;
            }
            String state = jdbc.queryForObject("SELECT deletion_state FROM file_upload WHERE id=? FOR UPDATE",
                    String.class, id);
            FileUpload file = files.findAcrossTenants(id);
            if (file == null) throw new IllegalArgumentException("资源不存在：" + id);
            if (!"ACTIVE".equals(state) || !"PUBLIC".equals(storage.visibility(file)))
                throw new IllegalStateException("资源正在删除或不是公开资源：" + id);
            if (!Long.valueOf(0).equals(file.getTenantId()) && !request.tenantId().equals(file.getTenantId()))
                throw new SecurityException("不能引用其他租户的资源");
            String publicUrl = storage.publicUrl(file);
            if (publicUrl == null || publicUrl.isBlank())
                throw new IllegalStateException("资源缺少可访问的公开地址：" + id);
            jdbc.update("INSERT INTO file_resource_reference "
                    + "(resource_id,tenant_id,business_type,business_id,field_name,sort_order) "
                    + "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                    id, request.tenantId(), request.businessType(), request.businessId(),
                    slot.fieldName(), slot.sortOrder());
            resolved.add(new ResourceBinding(slot.fieldName(), slot.sortOrder(), id, publicUrl));
        }
        return new ResourceBindingPlan(request, resolved, previous);
    }

    /** 仅删除准备阶段已存在且不再需要的行，不触碰并发事务后来登记的引用。 */
    @Transactional(rollbackFor = Exception.class)
    public void complete(ResourceBindingPlan plan) {
        if (plan == null) throw new IllegalArgumentException("引用计划不能为空");
        validate(plan.request());
        Set<String> wanted = new HashSet<>();
        for (ResourceBinding slot : plan.bindings()) {
            if (slot.resourceId() != null)
                wanted.add(slot.fieldName() + ":" + slot.sortOrder() + ":" + slot.resourceId());
        }
        for (Long referenceId : plan.previousReferenceIds()) {
            List<Map<String, Object>> rows = jdbc.queryForList("SELECT field_name,sort_order,resource_id "
                    + "FROM file_resource_reference WHERE id=? AND tenant_id=? AND business_type=? AND business_id=?",
                    referenceId, plan.request().tenantId(), plan.request().businessType(), plan.request().businessId());
            if (rows.isEmpty()) continue;
            Map<String, Object> row = rows.get(0);
            String key = row.get("field_name") + ":" + row.get("sort_order") + ":" + row.get("resource_id");
            if (!wanted.contains(key)) jdbc.update("DELETE FROM file_resource_reference WHERE id=?", referenceId);
        }
    }

    /** 根据业务主键读取引用；业务响应继续保留原 URL 字段。 */
    public List<ResourceBinding> references(Long tenantId, String type, Long businessId) {
        List<ResourceBinding> result = new ArrayList<>();
        jdbc.query("SELECT field_name,sort_order,resource_id FROM file_resource_reference "
                        + "WHERE tenant_id=? AND business_type=? AND business_id=? "
                        + "ORDER BY field_name,sort_order,updated_at DESC,id DESC",
                row -> {
                    FileUpload file = files.findAcrossTenants(row.getLong("resource_id"));
                    if (file != null && "ACTIVE".equals(file.getDeletionState())
                            && "PUBLIC".equals(storage.visibility(file))) {
                        String publicUrl = storage.publicUrl(file);
                        if (publicUrl != null && !publicUrl.isBlank())
                            result.add(new ResourceBinding(row.getString("field_name"), row.getInt("sort_order"),
                                    file.getId(), publicUrl));
                    }
                }, tenantId, type, businessId);
        return result;
    }

    /** 超级管理员查询该资源被哪些业务和图片位置引用。 */
    public List<Map<String, Object>> usages(Long resourceId, int offset, int size) {
        return jdbc.queryForList("SELECT id,tenant_id,business_type,business_id,field_name,sort_order,created_at "
                + "FROM file_resource_reference WHERE resource_id=? ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                resourceId, size, offset);
    }

    public long count(Long resourceId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM file_resource_reference WHERE resource_id=?",
                Long.class, resourceId);
        return count == null ? 0 : count;
    }

    /** 与登记使用同一条文件行锁，先封锁新引用，再执行外部存储删除。 */
    @Transactional(rollbackFor = Exception.class)
    public void beginDelete(Long resourceId) {
        String state = jdbc.queryForObject("SELECT deletion_state FROM file_upload WHERE id=? FOR UPDATE",
                String.class, resourceId);
        if (!"ACTIVE".equals(state)) throw new IllegalStateException("资源正在删除或等待恢复");
        requireMigrationReady(files.findAcrossTenants(resourceId));
        if (count(resourceId) > 0) throw new IllegalStateException("资源仍被业务引用，请先移除关联");
        jdbc.update("UPDATE file_upload SET deletion_state='DELETING' WHERE id=?", resourceId);
    }

    /** 权限迁移期间拒绝新公开引用，防止公开转私有时产生竞争。 */
    @Transactional(rollbackFor = Exception.class)
    public void beginVisibilityChange(Long resourceId) {
        String state = jdbc.queryForObject("SELECT deletion_state FROM file_upload WHERE id=? FOR UPDATE",
                String.class, resourceId);
        if (!"ACTIVE".equals(state)) throw new IllegalStateException("资源正在操作中");
        if (count(resourceId) > 0) throw new IllegalStateException("资源仍被业务引用，请先移除关联");
        jdbc.update("UPDATE file_upload SET deletion_state='UPDATING' WHERE id=?", resourceId);
    }

    public void finishVisibilityChange(Long resourceId) {
        jdbc.update("UPDATE file_upload SET deletion_state='ACTIVE' WHERE id=? AND deletion_state='UPDATING'",
                resourceId);
    }

    public void markVisibilityFailed(Long resourceId) {
        jdbc.update("UPDATE file_upload SET deletion_state='CHANGE_FAILED' WHERE id=? AND deletion_state='UPDATING'",
                resourceId);
    }

    /** 回填保护只覆盖控制记录建立前的旧文件；之后上传的文件仍检查引用表和业务 URL。 */
    public void requireMigrationReady(FileUpload file) {
        if (file == null) throw new IllegalArgumentException("资源不存在");
        Boolean allowed = jdbc.queryForObject(
                "SELECT deletion_enabled,updated_at FROM file_resource_reference_control WHERE id=1",
                (rs, rowNum) -> {
                    if (rs.getInt("deletion_enabled") == 1) return true;
                    var cutoff = rs.getTimestamp("updated_at");
                    return cutoff != null && file.getCreatedAt() != null
                            && file.getCreatedAt().isAfter(cutoff.toLocalDateTime());
                });
        if (!Boolean.TRUE.equals(allowed))
            throw new IllegalStateException("资源引用回填尚未验证，暂不可删除或转为私有");
    }

    /** 对象删除失败后保留记录供人工核对与重试。 */
    public void markDeleteFailed(Long resourceId) {
        jdbc.update("UPDATE file_upload SET deletion_state='DELETE_FAILED' WHERE id=? AND deletion_state='DELETING'",
                resourceId);
    }

    /** 外键会阻止意外存在引用时删除文件记录。 */
    public void finishDelete(Long resourceId) {
        if (jdbc.update("DELETE FROM file_upload WHERE id=? AND deletion_state='DELETING'", resourceId) != 1)
            throw new IllegalStateException("资源删除状态已改变");
    }

    /** 只接受数据库中的旧完整 URL 或当前受信任域名生成的完整 URL。 */
    public Long matchExactPublicUrl(String url) {
        if (url == null || url.isBlank()) return null;
        List<Long> exact = jdbc.queryForList("SELECT id FROM file_upload WHERE url=? AND BINARY url=BINARY ? "
                + "AND visibility='PUBLIC' LIMIT 2", Long.class, url, url);
        if (exact.size() > 1) return null;
        if (exact.size() == 1) return exact.get(0);
        String base = storage.currentSettings().getPublicDeliveryBaseUrl();
        if (base != null && !base.isBlank()) {
            String prefix = base.replaceAll("/+$", "") + "/";
            if (url.startsWith(prefix) && url.substring(prefix.length()).matches("[0-9]+")) {
                try {
                    Long id = Long.valueOf(url.substring(prefix.length()));
                    FileUpload file = files.findAcrossTenants(id);
                    if (file != null && url.equals(storage.publicUrl(file))) return id;
                } catch (NumberFormatException ignored) { return null; }
            }
        }
        Matcher tail = KEY_TAIL.matcher(url);
        if (!tail.find()) return null;
        List<Long> candidates = jdbc.queryForList("SELECT id FROM file_upload WHERE storage_key=? "
                + "AND visibility='PUBLIC' LIMIT 20", Long.class, tail.group(1));
        Long matched = null;
        for (Long id : candidates) {
            FileUpload file = files.findAcrossTenants(id);
            if (file != null && url.equals(storage.originPublicUrl(file))) {
                if (matched != null) return null;
                matched = id;
            }
        }
        return matched;
    }

    /** 回填预览和旧客户端 URL 输入共用同一套安全匹配规则。 */
    public Long matchForTenant(Long tenantId, String url) {
        if (tenantId == null || tenantId < 0) return null;
        Long id = matchExactPublicUrl(url);
        if (id == null) return null;
        FileUpload file = files.findAcrossTenants(id);
        if (file == null || !"ACTIVE".equals(file.getDeletionState())
                || !"PUBLIC".equals(storage.visibility(file))) return null;
        return Long.valueOf(0).equals(file.getTenantId()) || tenantId.equals(file.getTenantId()) ? id : null;
    }

    private void validate(ResourceBindingRequest request) {
        if (request == null || request.tenantId() == null || request.tenantId() < 0
                || request.businessId() == null || request.businessId() <= 0
                || request.fields() == null || request.bindings() == null
                || request.fields().isEmpty() || request.bindings().size() > 100
                || !FIELDS.containsKey(request.businessType())
                || !FIELDS.get(request.businessType()).containsAll(request.fields()))
            throw new IllegalArgumentException("资源引用参数不合法");
    }
}
