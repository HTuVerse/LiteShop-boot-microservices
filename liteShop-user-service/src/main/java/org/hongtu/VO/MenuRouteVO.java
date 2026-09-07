package org.hongtu.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.PermissionTypeEnum;
import org.hongtu.enums.MenuScopeEnum;
import org.hongtu.enums.StatusEnum;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 前端路由菜单VO
 */
@Data
public class MenuRouteVO {
    
    /**
     * 菜单ID
     */
    private Long id;
    
    /**
     * 路由路径
     */
    private String path;
    
    /**
     * 路由名称
     */
    private String name;
    
    /**
     * 组件路径
     */
    private String component;
    
    /**
     * 元数据
     */
    private Map<String, Object> meta;
    
    /**
     * 子菜单
     */
    private List<MenuRouteVO> children;

    /**
     * 权限码
     */
    private String permissionCode;

    /** 0=ALL / 1=PLATFORM / 2=TENANT；仅控制导航范围，不代替接口鉴权。 */
    private MenuScopeEnum menuScope;

    /**
     * 状态
     */
    private StatusEnum status;

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
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;


    /**
     * 设置元数据
     * @param title 标题
     * @param icon 图标
     * @param keepAlive 是否缓存
     */
    public void setMeta(String title, String icon, String link ,BooleanEnum keepAlive, BooleanEnum isHide,BooleanEnum isIframe) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("title", title);
        meta.put("icon", icon);
        meta.put("keepAlive", keepAlive);
        meta.put("isHide",isHide);
        meta.put("link",link);
        meta.put("isIframe",isIframe);
        this.meta = meta;
    }
} 