package org.hongtu.VO;

import lombok.Data;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.enums.MenuScopeEnum;
import org.hongtu.enums.StatusEnum;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 功能：
 * 作者：
 * 时间：2025/5/6 下午4:39
 */
@Data
public class SysPermissionVO {

    private Long id;
    private String permissionName;
    private String permissionCode;

    /** 0=ALL / 1=PLATFORM / 2=TENANT；仅控制导航范围，不代替接口鉴权。 */
    private MenuScopeEnum menuScope;
    private Long parentId;
    private PermissionTypeEnum permissionType;
    private Integer sort;
    private StatusEnum status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private Boolean isAssigned;
    private List<SysPermissionVO> children;

}
