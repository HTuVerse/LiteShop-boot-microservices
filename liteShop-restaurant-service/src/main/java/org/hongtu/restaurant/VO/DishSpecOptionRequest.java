package org.hongtu.restaurant.VO;

import lombok.Data;
import org.hongtu.restaurant.DTO.DishSpecOption;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.StatusEnum;

import java.util.List;


/**
 * 菜品规格选项请求DTO
 * @author Ht
 * @since 2025-09-19
 */
@Data
public class DishSpecOptionRequest {

    /**
     * 菜品ID
     */
    private Long dishId;

    /**
     * 规格名称（如：分量、做法、温度）
     */
    private String specName;

    /**
     * 规格值列表
     */
    private List<SpecValue> specValueList;

    /**
     * 是否必选：0-否，1-是
     */
    private BooleanEnum isRequired;

    /**
     *是否多选：0-否，1-是
     * */
    private BooleanEnum moreSelect;

    /**
     * 排序值
     */
    private Integer sortOrder;

    /**
     * 转换为DishSpecOption对象
     * @return DishSpecOption对象
     */
    public DishSpecOption toDishSpecOption() {
        DishSpecOption specOption = new DishSpecOption();
        specOption.setDishId(this.dishId);
        specOption.setSpecName(this.specName);
        specOption.setSpecValueList(this.specValueList);
        specOption.setIsRequired(this.isRequired);
        specOption.setSortOrder(this.sortOrder != null ? this.sortOrder : 0);
        specOption.setMoreSelect(this.moreSelect);
        specOption.setStatus(StatusEnum.NORMAL);
        return specOption;
    }
} 