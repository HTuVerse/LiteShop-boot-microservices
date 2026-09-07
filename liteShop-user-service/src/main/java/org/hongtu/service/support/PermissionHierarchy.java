package org.hongtu.service.support;

import org.hongtu.DTO.PermissionHierarchyRequest;
import org.hongtu.DTO.SysPermission;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.enums.StatusEnum;
import java.util.*;
import java.util.stream.Collectors;

/** Pure hierarchy validation and navigation ancestor resolution. */
public final class PermissionHierarchy {
    private PermissionHierarchy() {}
    /** Selecting an action includes its page; selecting a page never includes its actions. */
    public static List<Long> assignment(List<SysPermission> available, List<Long> selected) {
        Map<Long, SysPermission> byId = available.stream().collect(Collectors.toMap(SysPermission::getId, p -> p));
        Set<Long> result = new LinkedHashSet<>();
        for (Long id : selected) {
            Set<Long> visited = new HashSet<>();
            Long cursor = id;
            while (cursor != null && cursor != 0L) {
                SysPermission node = byId.get(cursor);
                if (node == null || node.getStatus() != StatusEnum.NORMAL || !visited.add(cursor))
                    throw new IllegalArgumentException("权限或上级菜单不可分配，请刷新后重试");
                result.add(cursor);
                cursor = node.getParentId();
            }
            if (id == null || id == 0L) throw new IllegalArgumentException("权限ID不能为空");
        }
        return new ArrayList<>(result);
    }
    public static boolean isDirectory(SysPermission row) {
        return row.getPermissionType() == PermissionTypeEnum.MENU
                && Set.of("Layout", "/index/index", "index/index", "index/index.vue")
                .contains(Objects.toString(row.getComponentAddress(), ""));
    }
    private static long parent(SysPermission row) {
        return row.getParentId() == null ? 0L : row.getParentId();
    }
    public record Change(Long id, Long parentId, Integer sort, String routerAddress) {}

    public static List<Change> plan(List<SysPermission> current, PermissionHierarchyRequest request) {
        if (request == null || request.getOriginal() == null || request.getNodes() == null)
            throw new IllegalArgumentException("菜单层级数据不完整，请刷新后重试");
        Map<Long, SysPermission> rows = current.stream().collect(Collectors.toMap(SysPermission::getId, p -> p));
        Map<Long, PermissionHierarchyRequest.Entry> original = entries(request.getOriginal());
        Map<Long, PermissionHierarchyRequest.Entry> proposed = entries(request.getNodes());
        if (!rows.keySet().equals(original.keySet()) || !rows.keySet().equals(proposed.keySet()))
            throw new IllegalArgumentException("菜单已被新增或删除，请刷新后重新调整");
        for (SysPermission row : current) {
            var before = original.get(row.getId());
            if (before.getParentId() != parent(row)
                    || !Objects.equals(before.getSort(), row.getSort() == null ? 0 : row.getSort()))
                throw new IllegalArgumentException("菜单层级已被其他操作修改，请刷新后重新调整");
            var next = proposed.get(row.getId());
            SysPermission target = rows.get(next.getParentId());
            if (next.getParentId() == 0) {
                if (row.getPermissionType() != PermissionTypeEnum.MENU)
                    throw new IllegalArgumentException("按钮和API权限不能移到顶层");
            } else if (target == null || target.getPermissionType() != PermissionTypeEnum.MENU) {
                throw new IllegalArgumentException("上级菜单不存在或不是菜单");
            } else if (row.getPermissionType() == PermissionTypeEnum.MENU && !isDirectory(target)) {
                throw new IllegalArgumentException("菜单只能放入目录，页面下只能添加按钮或API");
            }
            Set<Long> visited = new HashSet<>();
            Long cursor = row.getId();
            while (cursor != 0L) {
                if (!visited.add(cursor)) throw new IllegalArgumentException("不能将菜单移入自己或下级目录");
                if (visited.size() > 16) throw new IllegalArgumentException("菜单层级不能超过16层");
                var entry = proposed.get(cursor);
                if (entry == null) throw new IllegalArgumentException("上级菜单不存在");
                cursor = entry.getParentId();
            }
        }
        List<Change> changes = new ArrayList<>();
        for (SysPermission row : current) {
            var next = proposed.get(row.getId());
            // Preserve full URLs before moving directories, including relative descendants.
            String path = row.getPermissionType() == PermissionTypeEnum.MENU
                    ? fullPath(row, rows, new HashSet<>()) : row.getRouterAddress();
            if (next.getParentId() != parent(row) || !Objects.equals(next.getSort(), row.getSort())
                    || !Objects.equals(path, row.getRouterAddress()))
                changes.add(new Change(row.getId(), next.getParentId(), next.getSort(), path));
        }
        return changes;
    }
    private static Map<Long, PermissionHierarchyRequest.Entry> entries(List<PermissionHierarchyRequest.Entry> list) {
        Map<Long, PermissionHierarchyRequest.Entry> result = new HashMap<>();
        for (var entry : list) {
            if (entry == null || entry.getId() == null || entry.getId() <= 0
                    || entry.getParentId() == null || entry.getParentId() < 0 || entry.getSort() == null
                    || entry.getSort() < 0 || result.putIfAbsent(entry.getId(), entry) != null)
                throw new IllegalArgumentException("菜单ID、上级或排序数据不合法");
        }
        return result;
    }
    public static String fullPath(SysPermission row, Map<Long, SysPermission> rows, Set<Long> visited) {
        if (!visited.add(row.getId())) throw new IllegalArgumentException("原菜单存在循环层级，请先修复");
        String path = Objects.toString(row.getRouterAddress(), "");
        if (path.startsWith("/") || path.startsWith("https://") || path.startsWith("http://")) return path;
        SysPermission ancestor = rows.get(parent(row));
        String prefix = ancestor == null ? "" : fullPath(ancestor, rows, visited);
        return prefix.replaceAll("/+$", "") + "/" + path;
    }
    /** Add only directory ancestors of granted pages; sibling pages/actions stay ungranted. */
    public static List<SysPermission> navigation(List<SysPermission> granted, List<SysPermission> all) {
        Map<Long, SysPermission> rows = all.stream().collect(Collectors.toMap(SysPermission::getId, p -> p));
        Set<Long> grantedIds = granted.stream().map(SysPermission::getId).collect(Collectors.toSet());
        Map<Long, SysPermission> visible = new HashMap<>();
        for (SysPermission leaf : granted) {
            List<SysPermission> chain = new ArrayList<>();
            Set<Long> visited = new HashSet<>();
            SysPermission cursor = rows.getOrDefault(leaf.getId(), leaf);
            boolean valid = true;
            while (cursor != null) {
                if (!visited.add(cursor.getId()) || cursor.getStatus() != StatusEnum.NORMAL
                        || (!isDirectory(cursor) && !grantedIds.contains(cursor.getId()))) {
                    valid = false; break;
                }
                chain.add(cursor);
                if (parent(cursor) == 0L) break;
                cursor = rows.get(parent(cursor));
                if (cursor == null) valid = false;
            }
            if (valid) for (SysPermission item : chain) visible.put(item.getId(), item);
        }
        return visible.values().stream().sorted(Comparator
                .comparing((SysPermission p) -> p.getSort() == null ? 0 : p.getSort())
                .thenComparing(SysPermission::getId)).toList();
    }
}

