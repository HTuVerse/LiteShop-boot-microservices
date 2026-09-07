package org.hongtu.restaurant.service;

import org.apache.ibatis.annotations.Param;
import org.hongtu.restaurant.DTO.DishSpecOption;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * <p>
 * 菜品规格选项表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
public interface DishSpecOptionService extends IService<DishSpecOption> {


    /**
     * 根据菜品ID查询启用规格选项列表
     * @param dishId 菜品ID
     * @return 规格选项列表
     */
    List<DishSpecOption> selectSpecOptionsByDishId(Long dishId);
}
