package org.hongtu.restaurant.service;

import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.Dish;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.restaurant.VO.DishVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 菜品表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
public interface DishService extends IService<Dish> {

    /**
     * 添加菜品
     * @param dish 菜品信息
     * @return 是否成功
     */
    boolean addDish(Dish dish);

    /**
     * 更新菜品信息
     * @param dish 菜品信息
     * @return 是否成功
     */
    boolean updateDish(Dish dish);

    /**
     * 删除菜品
     * @param id 菜品ID
     * @return 是否成功
     */
    boolean deleteDish(Long id);

    /**
     * 批量删除菜品
     * @param ids 菜品ID列表
     * @return 是否成功
     */
    boolean batchDeleteDish(List<Long> ids);

    /**
     * 根据ID查询菜品详情
     * @param id 菜品ID
     * @return 菜品详情
     */
    DishVO getDishDetail(Long id);

    /**
     * 分页查询菜品列表
     * @param params 查询参数
     * @param page 分页参数
     * @return 分页菜品列表
     */
    Page<DishVO> getDishPage(Map<String, Object> params, Page<Dish> page);

    /**
     * 不分页查询菜品列表
     * @param searchKey 搜索关键字
     * @param isListBySort 是否按分类查询：0-否，1-是
     * @param isRecommend 是否推荐：1-是，0-否
     * @param isHot 是否热门：1-是，0-否
     * @param isNew 是否新品：1-是，0-否
     * @return 菜品列表（当isListBySort=0时）或按分类分组的菜品列表（当isListBySort=1时）
     */
    Result listDishNoPage(String searchKey, Integer isListBySort, Integer isRecommend, Integer isHot, Integer isNew);

    /**
     * 减少菜品库存
     * @param dishId 菜品ID
     * @param quantity 减少的数量
     * @return 是否成功
     */
    boolean decreaseStock(Long dishId, Integer quantity);

    /**
     * 批量减少菜品库存
     * @param dishStockMap 菜品ID和对应减少数量的映射
     * @return 是否成功
     */
    boolean batchDecreaseStock(Map<Long, Integer> dishStockMap);

    /**
     * 退菜时批量加回库存并减少销量
     * @param dishQuantityMap 菜品ID和对应加回数量的映射
     * @return 是否成功
     */
    boolean batchIncreaseStockAndDecreaseSales(Map<Long, Integer> dishQuantityMap);

    /**
     * 更新菜品销量
     * @param id 菜品ID
     * @param count 增加的销量
     * @return 是否成功
     */
//    boolean updateDishSalesCount(Long id, Integer count);
}
