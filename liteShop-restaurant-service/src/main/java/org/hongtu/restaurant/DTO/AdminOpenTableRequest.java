package org.hongtu.restaurant.DTO;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员开桌/加入餐桌请求
 * 用于收银前台操作
 *
 * @author Ht
 * @since 2026-02-18
 */
@Data
public class AdminOpenTableRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 桌位ID（必填）
     */
    private Long tableId;

    /** 从预约办理到店时传入；开桌成功后预约自动转为已到店。 */
    private Long reservationId;

    /**
     * 就餐人数（可选）
     * 仅在创建新会话时使用，默认为1
     */
    private Integer peopleCount;

    /**
     * 操作人ID（管理员ID）
     * 用于记录是哪个管理员操作的
     */
    private Long operatorId;

    /**
     * 操作人姓名（管理员姓名）
     * 用于日志记录和显示
     */
    private String operatorName;

    /**
     * 备注信息（可选）
     * 例如：预定信息、特殊要求等
     */
    private String remark;
}
