package org.hongtu.restaurant.VO;

import lombok.Data;

import java.util.List;

/**
 * 门店分组保存请求。
 */
@Data
public class StoreGroupSaveRequest {
    private Long id;
    private String groupCode;
    private String groupName;
    private String description;
    private Integer status;
    private List<Long> storeIds;
}
