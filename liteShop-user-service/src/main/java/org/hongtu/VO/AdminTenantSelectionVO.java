package org.hongtu.VO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** 管理端第一阶段登录结果；此时尚未创建正式登录会话。 */
@Data
@AllArgsConstructor
public class AdminTenantSelectionVO {

    private boolean tenantSelectionRequired;
    private String selectionTicket;
    private int expiresInSeconds;
    private List<AdminTenantOptionVO> tenants;
}
