package org.hongtu.restaurant.VO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 按分类分组的菜品VO
 * @author Ht
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DishByCategoryVO {

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 分类描述
     */
    private String categoryDesc;

    /**
     * 分类icon
     * */
    private String categoryIcon;

    /**
     * 该分类下的菜品列表
     */
    private List<DishVO> dishes;
}

