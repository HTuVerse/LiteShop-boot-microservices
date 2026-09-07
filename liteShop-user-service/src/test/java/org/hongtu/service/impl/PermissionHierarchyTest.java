package org.hongtu.service.impl;

import org.hongtu.DTO.SysPermission;
import org.hongtu.DTO.PermissionHierarchyRequest;
import org.hongtu.DAO.SysPermissionMapper;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.enums.StatusEnum;
import org.hongtu.Result.Result;
import org.hongtu.service.support.PermissionHierarchy;
import org.hongtu.controller.SysPermissionController;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PermissionHierarchyTest {
    private SysPermission row(long id, long parent, String path, boolean directory) {
        return new SysPermission().setId(id).setParentId(parent).setSort((int) id)
                .setRouterAddress(path).setComponentAddress(directory ? "Layout" : "system/user/index")
                .setPermissionType(PermissionTypeEnum.MENU).setStatus(StatusEnum.NORMAL);
    }
    private List<SysPermission> rows() {
        return List.of(row(1,0,"/one",true), row(2,1,"group",true),
                row(3,2,"detail/:id",false), row(4,0,"/two",true),
                row(5,3,null,false).setPermissionType(PermissionTypeEnum.BUTTON));
    }
    private PermissionHierarchyRequest.Entry entry(SysPermission row) {
        var e = new PermissionHierarchyRequest.Entry();
        e.setId(row.getId()); e.setParentId(row.getParentId()); e.setSort(row.getSort()); return e;
    }
    private PermissionHierarchyRequest request(List<SysPermission> rows) {
        var r = new PermissionHierarchyRequest();
        r.setOriginal(new ArrayList<>(rows.stream().map(this::entry).toList()));
        r.setNodes(new ArrayList<>(rows.stream().map(this::entry).toList()));
        return r;
    }
    @Test void movingDirectoryPreservesRelativeDescendantUrlsAndActionParents() {
        var rows = rows(); var r = request(rows); r.getNodes().get(1).setParentId(4L);
        r.getNodes().get(1).setSort(10);
        var changes = PermissionHierarchy.plan(rows, r);
        var moved = changes.stream().filter(c -> c.id() == 2L).findFirst().orElseThrow();
        assertEquals(4L, moved.parentId()); assertEquals("/one/group", moved.routerAddress());
        assertEquals("/one/group/detail/:id", changes.stream().filter(c -> c.id() == 3L)
                .findFirst().orElseThrow().routerAddress());
        assertTrue(changes.stream().noneMatch(c -> c.id() == 5L));
        assertEquals(1L, rows.get(1).getParentId());
    }
    @Test void supportsTopLevelMoveAndSiblingReorder() {
        var rows = rows(); var r = request(rows);
        r.getNodes().get(2).setParentId(0L); r.getNodes().get(3).setSort(0);
        var changes = PermissionHierarchy.plan(rows, r);
        assertTrue(changes.stream().anyMatch(c -> c.id() == 3 && c.parentId() == 0));
        assertTrue(changes.stream().anyMatch(c -> c.id() == 4 && c.sort() == 0));
    }
    @Test void rejectsSelfCyclesDescendantCyclesAndInvalidParents() {
        for (long target : List.of(1L, 2L, 3L, 5L, 999L)) {
            var rows = rows(); var r = request(rows); r.getNodes().get(0).setParentId(target);
            assertThrows(IllegalArgumentException.class, () -> PermissionHierarchy.plan(rows,r));
        }
        var rows=rows(); var r=request(rows); r.getNodes().get(4).setParentId(0L);
        assertThrows(IllegalArgumentException.class, () -> PermissionHierarchy.plan(rows,r));
    }
    @Test void rejectsStaleDeletedDuplicateAndNegativeSnapshotsBeforeWriting() {
        var rows=rows(); var stale=request(rows); stale.getOriginal().get(0).setSort(99);
        assertThrows(IllegalArgumentException.class, () -> PermissionHierarchy.plan(rows,stale));
        var missing=request(rows); missing.getNodes().remove(0);
        assertThrows(IllegalArgumentException.class, () -> PermissionHierarchy.plan(rows,missing));
        var duplicate=request(rows); duplicate.getNodes().add(duplicate.getNodes().get(0));
        assertThrows(IllegalArgumentException.class, () -> PermissionHierarchy.plan(rows,duplicate));
        var negative=request(rows); negative.getNodes().get(0).setSort(-1);
        assertThrows(IllegalArgumentException.class, () -> PermissionHierarchy.plan(rows,negative));
    }
    @Test void navigationIncludesOnlyDatabaseAncestorsOfGrantedLeaves() {
        var rows=new ArrayList<>(rows()); rows.add(row(6,2,"unassigned",false));
        var visible=PermissionHierarchy.navigation(List.of(rows.get(2),rows.get(4)),rows);
        assertEquals(List.of(1L,2L,3L,5L),visible.stream().map(SysPermission::getId).toList());
        rows.get(1).setStatus(StatusEnum.DISABLED);
        assertTrue(PermissionHierarchy.navigation(List.of(rows.get(2)),rows).isEmpty());
    }
    @Test void ungrantedPageParentCannotBeAddedByAnAssignedButton() {
        var rows=rows();
        assertTrue(PermissionHierarchy.navigation(List.of(rows.get(4)),rows).isEmpty());
        rows.get(0).setParentId(2L);
        assertTrue(PermissionHierarchy.navigation(List.of(rows.get(2)),rows).isEmpty());
    }
    @Test void serviceValidatesBeforeUpdateAndThrowsOnFailedWriteForRollback() {
        SysPermissionMapper mapper=mock(SysPermissionMapper.class);
        SysPermissionServiceImpl service=new SysPermissionServiceImpl();
        ReflectionTestUtils.setField(service,"baseMapper",mapper);
        var rows=rows(); when(mapper.selectHierarchyForUpdate()).thenReturn(rows);
        var invalid=request(rows); invalid.getNodes().get(0).setParentId(1L);
        assertNotEquals(Result.CODE_SUCCESS,service.updateHierarchy(invalid).getCode());
        verify(mapper,never()).updateHierarchy(anyLong(),anyLong(),anyInt(),any());
        var valid=request(rows); valid.getNodes().get(1).setParentId(4L);
        when(mapper.updateHierarchy(anyLong(),anyLong(),anyInt(),any())).thenReturn(1);
        assertEquals(Result.CODE_SUCCESS,service.updateHierarchy(valid).getCode());
        verify(mapper).updateHierarchy(2L,4L,2,"/one/group");
        when(mapper.updateHierarchy(anyLong(),anyLong(),anyInt(),any())).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> service.updateHierarchy(valid));
    }
    @Test void stringLongIdsBindAndHierarchyEndpointRequiresPlatformRole() throws Exception {
        String json="{\"original\":[{\"id\":\"9223372036854775807\",\"parentId\":\"0\",\"sort\":1}],\"nodes\":[]}";
        var req=new ObjectMapper().readValue(json,PermissionHierarchyRequest.class);
        assertEquals(Long.MAX_VALUE,req.getOriginal().get(0).getId());
        var method=SysPermissionController.class.getMethod("updateHierarchy",PermissionHierarchyRequest.class);
        assertArrayEquals(new String[]{"SUPER_ADMIN"},method.getAnnotation(SaCheckRole.class).value());
        assertEquals("/orders/:id",new SysPermissionServiceImpl().convertToMenuRoute(
                row(99,0,"/orders/:id",false)).getPath());
    }
}

