package org.hongtu.storage;

import lombok.RequiredArgsConstructor;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.dto.TenantResourceLibraryDTO;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

/** 租户根目录的授权；所有读写入口都在服务端按动作复核。 */
@Component
@RequiredArgsConstructor
public class ResourceLibraryAccess {
    public static final int VIEW = 1;
    public static final int SELECT = 2;
    public static final int EDIT = 4;
    public static final int UPLOAD = 8;
    private final JdbcTemplate jdbc;
    private final UserFeignClient users;

    public record Grant(Long tenantId, int permissions) { }
    public record Root(Long tenantId, String name, String mode, int permissions,
                       List<Grant> grants, int myPermissions, boolean selectableFolders) { }
    public record FolderAccess(String mode, int permissions, List<Grant> grants, int myPermissions) { }

    public List<Root> roots() {
        List<TenantResourceLibraryDTO> tenants = FeignResultUtil.unwrap(users.getResourceLibraryTenants());
        Map<Long, Map<String, Object>> settings = new HashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT tenant_id,access_mode,permissions FROM resource_library_root_access"))
            settings.put(((Number) row.get("tenant_id")).longValue(), row);
        Map<Long, List<Grant>> grants = new HashMap<>();
        jdbc.query("SELECT tenant_id,grantee_tenant_id,permissions FROM resource_library_root_grant",
                (RowCallbackHandler) rs -> grants.computeIfAbsent(rs.getLong(1), ignored -> new ArrayList<>())
                        .add(new Grant(rs.getLong(2), rs.getInt(3))));
        List<TenantResourceLibraryDTO> owners = new ArrayList<>();
        owners.add(new TenantResourceLibraryDTO(0L, "平台资源"));
        owners.addAll(tenants);
        return owners.stream().map(tenant -> {
                    Map<String, Object> setting = settings.get(tenant.id());
                    String mode = setting == null ? "PRIVATE" : (String) setting.get("access_mode");
                    int permissions = setting == null ? 0 : ((Number) setting.get("permissions")).intValue();
                    List<Grant> assigned = grants.getOrDefault(tenant.id(), List.of());
                    int mine = permissionsFor(tenant.id(), mode, permissions, assigned);
                    return new Root(tenant.id(), tenant.name(), mode, permissions, assigned, mine,
                            hasFolderAccess(tenant.id(), TenantContext.getTenantIdOrNull(), SELECT));
                })
                .filter(root -> root.myPermissions() != 0 || root.selectableFolders()
                        || hasFolderAccess(root.tenantId(), TenantContext.getTenantIdOrNull(), VIEW)
                        || hasFolderAccess(root.tenantId(), TenantContext.getTenantIdOrNull(), UPLOAD))
                .map(root -> platform() ? root : new Root(root.tenantId(), root.name(), root.mode(),
                        0, List.of(), root.myPermissions(), root.selectableFolders()))
                .toList();
    }

    public Root root(Long owner, String name) {
        List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                "SELECT access_mode,permissions FROM resource_library_root_access WHERE tenant_id=?", owner);
        String mode = rows.isEmpty() ? "PRIVATE" : (String) rows.get(0).get("access_mode");
        int permissions = rows.isEmpty() ? 0 : ((Number) rows.get(0).get("permissions")).intValue();
        List<Grant> grants = jdbc.query(
                "SELECT grantee_tenant_id,permissions FROM resource_library_root_grant WHERE tenant_id=? ORDER BY grantee_tenant_id",
                (rs, index) -> new Grant(rs.getLong(1), rs.getInt(2)), owner);
        return new Root(owner, name, mode, permissions, grants, permissionsFor(owner, mode, permissions, grants),
                hasFolderAccess(owner, TenantContext.getTenantIdOrNull(), SELECT));
    }

    public int permissionsFor(Long owner) {
        if (owner == null) return 0;
        if (platform()) return 15;
        return permissionsFor(owner, TenantContext.getTenantIdOrNull());
    }

    /** 内部资源引用接口用业务租户 ID 校验选用权限，避免依赖 Feign 传来的线程上下文。 */
    public int permissionsFor(Long owner, Long grantee) {
        if (owner == null || grantee == null) return 0;
        if (Objects.equals(owner, grantee) || grantee == 0L) return 15;
        List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                "SELECT access_mode,permissions FROM resource_library_root_access WHERE tenant_id=?", owner);
        if (rows.isEmpty()) return 0;
        String mode = (String) rows.get(0).get("access_mode");
        if ("ALL".equals(mode)) return effective(((Number) rows.get(0).get("permissions")).intValue());
        if (!"SELECTED".equals(mode)) return 0;
        List<Integer> grants = jdbc.queryForList(
                "SELECT permissions FROM resource_library_root_grant WHERE tenant_id=? AND grantee_tenant_id=?",
                Integer.class, owner, grantee);
        return grants.isEmpty() ? 0 : effective(grants.get(0));
    }

    /** 根策略只决定未分类资源和根目录操作；文件夹仅继承父文件夹的显式策略。 */
    public int permissionsForFolder(Long owner, Long folderId, Long grantee) {
        if (folderId == null) return permissionsFor(owner, grantee);
        if (grantee == null) return 0;
        if (grantee == 0L || Objects.equals(owner, grantee)) return 15;
        Long current = folderId;
        Set<Long> visited = new HashSet<>();
        while (current != null && visited.add(current)) {
            List<Map<String, Object>> folders = jdbc.queryForList(
                    "SELECT tenant_id,parent_id FROM resource_folder WHERE id=?", current);
            if (folders.isEmpty() || ((Number) folders.get(0).get("tenant_id")).longValue() != owner)
                return 0;
            List<Map<String, Object>> policies = jdbc.queryForList(
                    "SELECT access_mode,permissions FROM resource_library_folder_access WHERE folder_id=?", current);
            if (!policies.isEmpty()) {
                String mode = (String) policies.get(0).get("access_mode");
                if ("PRIVATE".equals(mode)) return 0;
                if ("ALL".equals(mode)) return effective(((Number) policies.get(0).get("permissions")).intValue());
                List<Integer> grants = jdbc.queryForList("SELECT permissions FROM resource_library_folder_grant "
                        + "WHERE folder_id=? AND grantee_tenant_id=?", Integer.class, current, grantee);
                return grants.isEmpty() ? 0 : effective(grants.get(0));
            }
            Object parent = folders.get(0).get("parent_id");
            current = parent == null ? null : ((Number) parent).longValue();
        }
        // 顶层文件夹没有显式授权时默认私密，不能继承根目录的未分类资源权限。
        return 0;
    }

    public int permissionsForFolder(Long owner, Long folderId) {
        if (platform()) return 15;
        return permissionsForFolder(owner, folderId, TenantContext.getTenantIdOrNull());
    }

    public int permissionsForFile(org.hongtu.DTO.FileUpload file, Long grantee) {
        return permissionsForFolder(file.getTenantId(), file.getFolderId(), grantee);
    }

    public void requireFolder(Long owner, Long folderId, int action) {
        if ((permissionsForFolder(owner, folderId) & action) != action)
            throw new SecurityException("无权操作该文件夹");
    }

    public List<Long> allowedFolders(Long owner, Long grantee, int action) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM resource_folder WHERE tenant_id=?", Long.class, owner);
        if (platform() || Objects.equals(grantee, owner) || Objects.equals(grantee, 0L)) return ids;
        return ids.stream().filter(id -> (permissionsForFolder(owner, id, grantee) & action) == action).toList();
    }

    public boolean hasFolderAccess(Long owner, Long grantee, int action) {
        return !allowedFolders(owner, grantee, action).isEmpty();
    }

    public FolderAccess folderAccess(Long owner, Long folderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT access_mode,permissions FROM resource_library_folder_access WHERE folder_id=? AND tenant_id=?",
                folderId, owner);
        String mode = rows.isEmpty() ? "INHERIT" : (String) rows.get(0).get("access_mode");
        int permissions = rows.isEmpty() ? 0 : ((Number) rows.get(0).get("permissions")).intValue();
        List<Grant> grants = jdbc.query("SELECT grantee_tenant_id,permissions FROM resource_library_folder_grant "
                        + "WHERE folder_id=? ORDER BY grantee_tenant_id",
                (rs, index) -> new Grant(rs.getLong(1), rs.getInt(2)), folderId);
        return new FolderAccess(mode, permissions, grants, permissionsForFolder(owner, folderId));
    }

    @Transactional
    public FolderAccess configureFolder(Long owner, Long folderId, String mode, int permissions, List<Grant> grants) {
        if (!platform()) throw new SecurityException("仅平台管理员可配置文件夹权限");
        if (!List.of("INHERIT", "PRIVATE", "ALL", "SELECTED").contains(mode)
                || permissions < 0 || (permissions & ~15) != 0 || grants == null)
            throw new IllegalArgumentException("文件夹权限参数不合法");
        List<Map<String, Object>> folder = jdbc.queryForList("SELECT tenant_id FROM resource_folder WHERE id=?", folderId);
        if (folder.isEmpty() || ((Number) folder.get(0).get("tenant_id")).longValue() != owner)
            throw new IllegalArgumentException("文件夹不存在");
        List<TenantResourceLibraryDTO> tenants = FeignResultUtil.unwrap(users.getResourceLibraryTenants());
        for (Grant grant : grants) {
            if (grant.tenantId() == null || Objects.equals(grant.tenantId(), owner)
                    || tenants.stream().noneMatch(t -> Objects.equals(t.id(), grant.tenantId()))
                    || grant.permissions() < 0 || (grant.permissions() & ~15) != 0)
                throw new IllegalArgumentException("授权租户或权限不合法");
        }
        jdbc.update("DELETE FROM resource_library_folder_grant WHERE folder_id=?", folderId);
        if ("INHERIT".equals(mode)) jdbc.update("DELETE FROM resource_library_folder_access WHERE folder_id=?", folderId);
        else jdbc.update("INSERT INTO resource_library_folder_access(folder_id,tenant_id,access_mode,permissions) "
                        + "VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE access_mode=VALUES(access_mode),permissions=VALUES(permissions)",
                folderId, owner, mode, "ALL".equals(mode) ? permissions : 0);
        if ("SELECTED".equals(mode)) for (Grant grant : grants)
            jdbc.update("INSERT INTO resource_library_folder_grant(folder_id,grantee_tenant_id,permissions) VALUES(?,?,?)",
                    folderId, grant.tenantId(), grant.permissions());
        return folderAccess(owner, folderId);
    }

    public void require(Long owner, int action) {
        if ((permissionsFor(owner) & action) != action) throw new SecurityException("无权操作该租户资源库");
    }

    public void requireExisting(Long owner) {
        if (owner == null || owner < 0) throw new IllegalArgumentException("租户 ID 不合法");
        if (owner == 0) return;
        if (Objects.equals(owner, TenantContext.getTenantIdOrNull())) return;
        if (FeignResultUtil.unwrap(users.getResourceLibraryTenants()).stream()
                .noneMatch(tenant -> Objects.equals(tenant.id(), owner)))
            throw new IllegalArgumentException("租户不存在");
    }

    public boolean platform() {
        return TenantContext.isSuperAdmin() && !TenantContext.hasTenant();
    }

    @Transactional
    public Root configure(Long owner, String mode, int permissions, List<Grant> grants) {
        if (!platform()) throw new SecurityException("仅平台管理员可配置资源库权限");
        if (!List.of("PRIVATE", "ALL", "SELECTED").contains(mode) || (permissions & ~15) != 0
                || permissions < 0 || grants == null) throw new IllegalArgumentException("资源库权限参数不合法");
        List<TenantResourceLibraryDTO> tenants = FeignResultUtil.unwrap(users.getResourceLibraryTenants());
        TenantResourceLibraryDTO target = Objects.equals(owner, 0L)
                ? new TenantResourceLibraryDTO(0L, "平台资源")
                : tenants.stream().filter(t -> Objects.equals(t.id(), owner))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("租户不存在"));
        for (Grant grant : grants) {
            if (grant.tenantId() == null || Objects.equals(grant.tenantId(), owner)
                    || tenants.stream().noneMatch(t -> Objects.equals(t.id(), grant.tenantId()))
                    || grant.permissions() < 0 || (grant.permissions() & ~15) != 0)
                throw new IllegalArgumentException("授权租户或权限不合法");
        }
        jdbc.update("INSERT INTO resource_library_root_access(tenant_id,access_mode,permissions) VALUES(?,?,?) " +
                "ON DUPLICATE KEY UPDATE access_mode=VALUES(access_mode),permissions=VALUES(permissions)",
                owner, mode, "ALL".equals(mode) ? permissions : 0);
        jdbc.update("DELETE FROM resource_library_root_grant WHERE tenant_id=?", owner);
        if ("SELECTED".equals(mode)) for (Grant grant : grants)
            jdbc.update("INSERT INTO resource_library_root_grant(tenant_id,grantee_tenant_id,permissions) VALUES(?,?,?)",
                    owner, grant.tenantId(), grant.permissions());
        return root(owner, target.name());
    }

    private int permissionsFor(Long owner, String mode, int permissions, List<Grant> grants) {
        if (Objects.equals(owner, TenantContext.getTenantIdOrNull()) || platform()) return 15;
        if ("ALL".equals(mode)) return effective(permissions);
        if (!"SELECTED".equals(mode)) return 0;
        return effective(grants.stream().filter(grant -> Objects.equals(grant.tenantId(), TenantContext.getTenantIdOrNull()))
                .mapToInt(Grant::permissions).findFirst().orElse(0));
    }

    private int effective(int permissions) {
        return (permissions & (SELECT | EDIT)) != 0 ? permissions | VIEW : permissions;
    }
}
