package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.restaurant.DTO.DishCategory;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * <p>
 * 菜品分类表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
public interface DishCategoryService extends IService<DishCategory> {

    /**
     * 分页查询分类，支持 searchKey、显隐、分类层级
     */
    Page<DishCategory> getCategoryPage(Integer currentPage, Integer pageSize, String searchKey,
                                       Integer isShow, Integer level, Long storeId, Integer sourceType);

}
