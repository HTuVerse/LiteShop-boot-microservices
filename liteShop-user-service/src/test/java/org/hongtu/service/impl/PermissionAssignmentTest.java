package org.hongtu.service.impl;

import org.hongtu.DTO.SysPermission;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.enums.StatusEnum;
import org.hongtu.service.support.PermissionHierarchy;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PermissionAssignmentTest {
    private SysPermission row(long id, long parent, PermissionTypeEnum type) {
        return new SysPermission().setId(id).setParentId(parent).setPermissionType(type).setStatus(StatusEnum.NORMAL);
    }
    private List<SysPermission> tree() {
        return List.of(row(1, 0, PermissionTypeEnum.MENU), row(2, 1, PermissionTypeEnum.MENU),
                row(3, 2, PermissionTypeEnum.BUTTON), row(4, 2, PermissionTypeEnum.BUTTON));
    }
    @Test void pageSelectionDoesNotIncludeButtons() {
        assertEquals(List.of(2L, 1L), PermissionHierarchy.assignment(tree(), List.of(2L)));
    }
    @Test void actionSelectionIncludesOnlyItsAncestors() {
        assertEquals(List.of(3L, 2L, 1L), PermissionHierarchy.assignment(tree(), List.of(3L)));
    }
    @Test void missingOrDisabledParentsCannotBecomeGranted() {
        assertThrows(IllegalArgumentException.class, () -> PermissionHierarchy.assignment(tree(), List.of(99L)));
        var nodes = tree(); nodes.get(1).setStatus(StatusEnum.DISABLED);
        assertThrows(IllegalArgumentException.class, () -> PermissionHierarchy.assignment(nodes, List.of(3L)));
    }
    @Test void emptySelectionRevokesAllAndDuplicateSelectionIsIdempotent() {
        assertEquals(List.of(), PermissionHierarchy.assignment(tree(), List.of()));
        assertEquals(List.of(3L, 2L, 1L), PermissionHierarchy.assignment(tree(), List.of(3L, 3L)));
    }
}
