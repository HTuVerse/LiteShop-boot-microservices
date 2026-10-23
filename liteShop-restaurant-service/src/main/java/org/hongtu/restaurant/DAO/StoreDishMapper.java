package org.hongtu.restaurant.DAO;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.hongtu.restaurant.DTO.StoreDish;

/**
 * 门店菜品配置数据访问接口。
 */
@Mapper
public interface StoreDishMapper extends BaseMapper<StoreDish> {

    /** 首次访问门店时按批写入缺失关联，唯一索引处理并发创建。 */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            <script>
            INSERT INTO store_dish (id, tenant_id, store_id, dish_id, listing_status, sold_out,
                inventory_mode, stock, sales_count, inherited_version, config_version, deleted,
                created_at, updated_at)
            VALUES
            <foreach collection="rows" item="row" separator=",">
                (#{row.id}, #{tenantId}, #{row.storeId}, #{row.dishId}, #{row.listingStatus},
                 #{row.soldOut}, #{row.inventoryMode}, #{row.stock}, #{row.salesCount},
                 #{row.inheritedVersion}, #{row.configVersion}, #{row.deleted},
                 #{row.createdAt}, #{row.updatedAt})
            </foreach>
            ON DUPLICATE KEY UPDATE id=id
            </script>
            """)
    int insertMissingBindings(@Param("tenantId") Long tenantId, @Param("rows") java.util.List<StoreDish> rows);

    /** 在数据库联表过滤后分页，避免先把全部菜品 ID 拉到 JVM 再拼大型 IN。 */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT sd.* FROM store_dish sd
            INNER JOIN dish d ON d.id=sd.dish_id AND d.tenant_id=sd.tenant_id
            WHERE sd.tenant_id=#{tenantId} AND sd.store_id=#{storeId} AND sd.deleted=0 AND d.deleted=0
              AND (d.source_type=0 OR d.owner_store_id=#{storeId}
                   OR (d.source_type IS NULL AND d.store_id=#{storeId}))
            <if test="params.categoryId != null">AND d.category_id=#{params.categoryId}</if>
            <if test="params.dishName != null and params.dishName != ''">
                AND d.dish_name LIKE CONCAT('%',#{params.dishName},'%')
            </if>
            <if test="params.isRecommend != null">AND d.is_recommend=#{params.isRecommend}</if>
            <if test="params.isHot != null">AND d.is_hot=#{params.isHot}</if>
            <if test="params.isNew != null">AND d.is_new=#{params.isNew}</if>
            <if test="params.sceneType != null">AND d.scene_type=#{params.sceneType}</if>
            <if test="params.sourceType != null">AND d.source_type=#{params.sourceType}</if>
            <choose>
              <when test="params.saleStatus == 0">
                AND (sd.listing_status=0 OR COALESCE(d.master_status,1) != 1)
              </when>
              <when test="params.saleStatus != null">
                AND sd.listing_status=#{params.saleStatus}
                <if test="params.saleStatus == 1">AND d.master_status=1</if>
              </when>
            </choose>
            ORDER BY sd.sort_order_override ASC, sd.id DESC
            </script>
            """)
    Page<StoreDish> selectStoreDishPage(Page<StoreDish> page, @Param("tenantId") Long tenantId,
                                        @Param("storeId") Long storeId,
                                        @Param("params") java.util.Map<String, Object> params);

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
