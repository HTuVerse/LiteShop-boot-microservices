package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.BooleanEnum;

/**
 * <p>
 * 菜品分类表
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dish_category")
public class DishCategory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 分类来源：0-总部分类，1-门店自建分类。
     */
    private Integer sourceType;

    /**
     * 门店分类所属门店；总部分类为空。
     */
    private Long ownerStoreId;

    /**
     * 逻辑删除：0-正常，1-删除。
     */
    private Integer deleted;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 分类描述
     */
    private String categoryDesc;

    /**
     * 分类图片
     */
    private String categoryPic;

    /**
     * 父分类ID，0表示顶级分类
     */
    private Long parentId;

    /**
     * 分类层级：1-一级，2-二级
     */
    private Integer level;

    /**
     * 排序值，数字越小越靠前
     */
    private Integer sortOrder;

    /**
     * 是否显示：0-隐藏，1-显示
     */
    private BooleanEnum isShow;

    /**
     * 分类icon
     * */
    private String categoryIcon;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;


}
