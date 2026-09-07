package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.enums.MenuScopeEnum;
import org.hongtu.enums.StatusEnum;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 系统权限表
 * </p>
 *
 * @author Honghong
 * @since 2025-05-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_permission")
public class SysPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 权限ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 权限名称
     */
    @TableField("permission_name")
    private String permissionName;

    /**
     * 权限标识（如：system:user:add）
     */
    @TableField("permission_code")
    private String permissionCode;

    /** 0=ALL / 1=PLATFORM / 2=TENANT；仅控制导航范围，不代替接口鉴权。 */
    @TableField("menu_scope")
    private MenuScopeEnum menuScope;

    /**
     * 父权限ID（0表示顶级）
     */
    @TableField("parent_id")
    private Long parentId;

    /**
     * 权限类型（1-菜单 2-按钮 3-API）
     */
    @TableField("permission_type")
    private PermissionTypeEnum permissionType;

    /**
     * 组件地址
     * */
    @TableField("component_address")
    private String componentAddress;

    /**
     * 路由地址
     * */
    @TableField("router_address")
    private String routerAddress;

    /**
     * 排序号
     */
    @TableField("sort")
    private Integer sort;

    /**
     * 状态（1-启用 0-禁用）
     */
    @TableField("status")
    private StatusEnum status;

    /**
     * 是否在菜单中隐藏（1-是 0-否）
     */
    @TableField("is_hide")
    private Boolean isHide;

    /**
     * 是否为iframe（1-是 0-否）
     */
    @TableField("is_iframe")
    private Boolean isIframe;


    /**
     * 是否为缓存（1-是 0-否）
     */
    @TableField("keep_alive")
    private Boolean keepAlive;

    /**
     * 链接
     */
    @TableField("link")
    private String link;

    /**
     * 图标
     */
    @TableField("icon")
    private String icon;

    /**
     * 创建时间
     */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;


}
