package org.hongtu.DTO;

import lombok.Data;
import java.util.List;

/** Complete before/after snapshots prevent lost concurrent hierarchy edits. */
@Data
public class PermissionHierarchyRequest {
    private List<Entry> original;
    private List<Entry> nodes;

    @Data
    public static class Entry {
        private Long id;
        private Long parentId;
        private Integer sort;
    }
}
