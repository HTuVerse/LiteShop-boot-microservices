package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.MenuChangeTypeEnum;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 门店菜单变更记录，同时承载审批、定时生效和审计留痕。
 */
@Data
@Accessors(chain = true)
@TableName("menu_change_record")
public class MenuChangeRecord implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID。 */
    private Long tenantId;

    /** 便于运营检索的变更单号。 */
    private String requestNo;

    /** 目标门店ID。 */
    private Long storeId;

    /** 目标菜品ID。 */
    private Long dishId;

    /** 变更类型：0门店菜品配置。 */
    private MenuChangeTypeEnum changeType;

    /** 变更前快照JSON。 */
    private String beforeSnapshot;

    /** 期望变更内容JSON。 */
    private String afterSnapshot;

    /** 状态：0待审批，1已审批，2驳回，3撤销，4待生效，5已生效，6执行失败。 */
    private Integer status;

    /** 发起原因。 */
    private String changeReason;

    /** 计划生效时间；为空表示审批后立即生效。 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime effectiveAt;

    /** 发起人用户ID。 */
    private Long requestedBy;

    /** 审批人用户ID。 */
    private Long reviewedBy;

    /** 审批备注。 */
    private String reviewRemark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime reviewedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime appliedAt;

    /** 定时执行失败原因。 */
    private String failureReason;

    /** 乐观锁版本。 */
    private Integer version;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
