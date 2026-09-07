package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.io.Serializable;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.VO.SpecValue;
import org.hongtu.restaurant.utils.SpecValueConverter;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.StatusEnum;

/**
 * <p>
 * 菜品规格选项表
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("dish_spec_option")
public class DishSpecOption implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 菜品ID
     */
    private Long dishId;

    /**
     * 规格名称（如：分量、做法、温度）
     */
    private String specName;

    /**
     * 可选值JSON数组（如：["大份","小份"] 或 [{"value":"大份","extra_price":10},{"value":"小份","extra_price":0}]）
     */
    private String specValues;

    /**
     * 规格值列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<SpecValue> specValueList;

    /**
     * 是否必选：0-否，1-是
     */
    private BooleanEnum isRequired;

    /**
     * 排序值
     */
    private Integer sortOrder;

    /**
     *是否多选：0-否，1-是
     * */
    private BooleanEnum moreSelect;

    /**
     * 状态：0-禁用，1-启用
     */
    private StatusEnum status;

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


    /**
     * 获取规格值列表
     * @return 规格值列表
     */
    public List<SpecValue> getSpecValueList() {
        if (specValueList != null) {
            return specValueList;
        }
        // 使用SpecValueConverter工具类解析规格值
        specValueList = SpecValueConverter.convertToList(specValues);
        return specValueList;
    }

/*
    *//**
     * 设置规格值列表并转换为JSON字符串
     * @param specValueList 规格值列表
     *//*
    public void setSpecValueList(List<SpecValue> specValueList) {
        this.specValueList = specValueList;

        // 使用SpecValueConverter工具类转换规格值
        this.specValues = SpecValueConverter.convertToString(specValueList);
    }*/
}
