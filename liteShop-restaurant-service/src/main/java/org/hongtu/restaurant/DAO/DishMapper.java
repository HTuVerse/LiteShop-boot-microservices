package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.hongtu.restaurant.DTO.Dish;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * <p>
 * 菜品表 Mapper 接口
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Mapper
public interface DishMapper extends BaseMapper<Dish> {

    /**
     * 根据ID查询菜品详情
     * @param id 菜品ID
     * @return 菜品信息
     */
    @Select("SELECT * FROM dish WHERE id = #{id}")
    Dish selectDishById(@Param("id") Long id);

    /**
     * 根据分类ID查询菜品列表
     * @param categoryId 分类ID
     * @return 菜品列表
     */
    @Select("SELECT * FROM dish WHERE category_id = #{categoryId} ORDER BY sort_order ASC")
    List<Dish> selectDishListByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 查询推荐菜品列表
     * @param storeId 门店ID
     * @return 推荐菜品列表
     */
    @Select("SELECT * FROM dish WHERE store_id = #{storeId} AND is_recommend = 1 ORDER BY sort_order ASC")
    List<Dish> selectRecommendDishList(@Param("storeId") Long storeId);

    /**
     * 更新菜品销量
     * @param id 菜品ID
     * @param count 增加的销量
     * @return 影响行数
     */
    @Update("UPDATE dish SET sales_count = sales_count + #{count}, updated_at = NOW() WHERE id = #{id}")
    int updateDishSalesCount(@Param("id") Long id, @Param("count") Integer count);

    /**
     * 减少菜品库存并增加销量
     * @param dishId 菜品ID
     * @param quantity 减少的库存数量
     * @return 影响行数
     */
    @Update("UPDATE dish SET stock = stock - #{quantity}, sales_count = sales_count + #{quantity}, updated_at = NOW() WHERE id = #{dishId} AND stock >= #{quantity}")
    int decreaseStock(@Param("dishId") Long dishId, @Param("quantity") Integer quantity);

    /**
     * 退菜时加回库存并减少销量
     * @param dishId 菜品ID
     * @param quantity 加回的数量
     * @return 影响行数
     */
    @Update("UPDATE dish SET stock = stock + #{quantity}, sales_count = GREATEST(0, sales_count - #{quantity}), updated_at = NOW() WHERE id = #{dishId}")
    int increaseStockAndDecreaseSales(@Param("dishId") Long dishId, @Param("quantity") Integer quantity);

}
