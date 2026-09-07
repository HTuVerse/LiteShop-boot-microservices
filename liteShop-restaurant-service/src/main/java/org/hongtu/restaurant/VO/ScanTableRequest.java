package org.hongtu.restaurant.VO;

import lombok.Data;

import java.io.Serializable;

/**
 * 扫码加入餐桌请求
 * 统一处理开桌和加入逻辑
 *
 * @author Ht
 * @since 2025-11-05
 */
@Data
public class ScanTableRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 桌位ID
     */
    private Long tableId;

    /** 管理端办理预约到店时传入。 */
    private Long reservationId;

    /**
     * 就餐人数（可选）
     * 仅在创建新会话时使用，默认为1
     */
    private Integer peopleCount;

    /**
     * 用户ID（可选）
     * 用于记录是谁扫码加入的
     */
    private Long userId;

    /**
     * 用户名称（可选）
     * 用于WebSocket推送显示
     */
    private String userName;

    /**
     * 备注（管理员可选）
     */
    private String remark;
}

