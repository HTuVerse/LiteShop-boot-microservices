package org.hongtu.restaurant.VO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.DTO.MenuChangeRecord;

/** 菜单变更列表展示对象，补充关联名称和状态语义字段。 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class MenuChangeRecordVO extends MenuChangeRecord {
    private String dishName;
    private String requestedByName;
    /** 仅驳回状态返回；数据来源为审批备注，避免数据库重复保存相同内容。 */
    private String rejectReason;
}
