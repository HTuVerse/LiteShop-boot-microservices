package org.hongtu.restaurant.VO;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 总部批量发布门店菜品配置请求。
 */
@Data
public class MenuChangeBatchRequest {
    private Long groupId;
    private List<Long> storeIds;
    private Long dishId;
    private StoreDishConfigRequest config;
    private LocalDateTime effectiveAt;
    private String changeReason;
}
