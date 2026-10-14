package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.hongtu.restaurant.DTO.StoreDish;

/**
 * 门店菜品配置数据访问接口。
 */
@Mapper
public interface StoreDishMapper extends BaseMapper<StoreDish> {

    /** 未支付预占：只减库存，不加销量。 */
    @Update("UPDATE store_dish SET stock = CASE WHEN inventory_mode = 1 THEN stock - #{quantity} ELSE stock END, " +
            "updated_at = NOW() WHERE store_id = #{storeId} AND dish_id = #{dishId} AND deleted = 0 " +
            "AND listing_status = 1 AND (inventory_mode = 0 OR stock >= #{quantity})")
    int reserveStock(@Param("storeId") Long storeId, @Param("dishId") Long dishId,
                     @Param("quantity") Integer quantity);

    /** 支付成功后把预占数量计入销量。 */
    @Update("UPDATE store_dish SET sales_count = sales_count + #{quantity}, updated_at = NOW() " +
            "WHERE store_id = #{storeId} AND dish_id = #{dishId} AND deleted = 0")
    int commitSales(@Param("storeId") Long storeId, @Param("dishId") Long dishId,
                    @Param("quantity") Integer quantity);

    /** 取消未支付订单时只归还预占库存。 */
    @Update("UPDATE store_dish SET stock = CASE WHEN inventory_mode = 1 THEN stock + #{quantity} ELSE stock END, " +
            "updated_at = NOW() WHERE store_id = #{storeId} AND dish_id = #{dishId} AND deleted = 0")
    int releaseReservedStock(@Param("storeId") Long storeId, @Param("dishId") Long dishId,
                             @Param("quantity") Integer quantity);

    /** 原子扣减指定门店菜品库存并累计销量。已支付加菜使用。 */
    @Update("UPDATE store_dish SET stock = CASE WHEN inventory_mode = 1 THEN stock - #{quantity} ELSE stock END, " +
            "sales_count = sales_count + #{quantity}, " +
            "updated_at = NOW() WHERE store_id = #{storeId} AND dish_id = #{dishId} AND deleted = 0 " +
            "AND listing_status = 1 AND (inventory_mode = 0 OR stock >= #{quantity})")
    int decreaseStock(@Param("storeId") Long storeId, @Param("dishId") Long dishId,
                      @Param("quantity") Integer quantity);

    /** 退菜时恢复指定门店库存并减少销量。 */
    @Update("UPDATE store_dish SET stock = CASE WHEN inventory_mode = 1 THEN stock + #{quantity} ELSE stock END, " +
            "sales_count = GREATEST(0, sales_count - #{quantity}), updated_at = NOW() " +
            "WHERE store_id = #{storeId} AND dish_id = #{dishId} AND deleted = 0")
    int restoreStock(@Param("storeId") Long storeId, @Param("dishId") Long dishId,
                     @Param("quantity") Integer quantity);
}
