package org.hongtu.VO;

import lombok.Data;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.enums.StatusEnum;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 功能：
 * 作者：
 * 时间：2025/9/4 下午5:20
 */
@Data
public class RoleMenuVO {

    /**
     * 菜单ID
     */
    private Long id;

    /**
     * 元数据
     */
    private Map<String, Object> meta;

    /**
     * 子菜单
     */
    private List<RoleMenuVO> children;

    /**
     * 权限码
     */
    private String permissionCode;

    /**
     * 状态
     */
    private StatusEnum status;

    /**
     * 图标
     */
    private String icon;

    /**
     * 排序号
     */
    private Integer sort;

    /**
     * 父级ID
     */
    private Long parentId;

    /**
     * 权限类型
     */
    private PermissionTypeEnum permissionType;

    /**
     * 是否分配
    * */
    private Boolean isAssigned;

    /**
     * 设置元数据
     * @param title 标题
     * @param icon 图标
     * @param keepAlive 是否缓存
     */
    public void setMeta(String title, String icon, Boolean keepAlive) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("title", title);
        meta.put("icon", icon);
        meta.put("keepAlive", keepAlive);
        this.meta = meta;
    }
}
