package org.hongtu.restaurant.enums;

import lombok.Getter;

/**
 * 菜单变更流程状态。
 */
@Getter
public enum MenuChangeStatusEnum {
    PENDING(0, "待审批"),
    APPROVED(1, "已审批"),
    REJECTED(2, "已驳回"),
    CANCELLED(3, "已撤销"),
    SCHEDULED(4, "待生效"),
    APPLIED(5, "已生效"),
    FAILED(6, "执行失败");

    private final int code;
    private final String description;

    MenuChangeStatusEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }
}
