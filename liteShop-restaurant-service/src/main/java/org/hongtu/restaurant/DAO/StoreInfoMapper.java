package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.hongtu.restaurant.DTO.StoreInfo;
import org.hongtu.restaurant.DTO.TenantStoreCount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p>
 * 门店信息表 Mapper 接口
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Mapper
public interface StoreInfoMapper extends BaseMapper<StoreInfo> {

    /** 按租户游标分批发布存量门店基线。 */
    @Select("SELECT tenant_id AS tenantId, COUNT(*) AS storeCount FROM store_info " +
            "WHERE tenant_id > #{afterTenantId} GROUP BY tenant_id ORDER BY tenant_id LIMIT 100")
    List<TenantStoreCount> countByTenantAfter(@Param("afterTenantId") Long afterTenantId);

    /** 批量检查门店是否仍被业务、配置或历史记录引用；有引用时禁止物理删除。 */
    @Select("""
            <script>
            SELECT s.id FROM store_info s
            WHERE s.tenant_id = #{tenantId}
              AND s.id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
              AND (
                EXISTS (SELECT 1 FROM restaurant_order x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM table_info x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM table_reservation x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM table_session x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM store_dish x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM store_menu_policy x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM dish x WHERE x.store_id = s.id OR x.owner_store_id = s.id) OR
                EXISTS (SELECT 1 FROM dish_category x WHERE x.store_id = s.id OR x.owner_store_id = s.id) OR
                EXISTS (SELECT 1 FROM pricing_campaign_scope x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM member_dish_price x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM store_group_member x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM menu_change_record x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM print_job x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM printer_info x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM print_template x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM analysis_store_daily x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM analysis_store_dish_daily x WHERE x.store_id = s.id) OR
                EXISTS (SELECT 1 FROM order_dish_change_log x WHERE x.store_id = s.id)
              )
            LIMIT 1
            </script>
            """)
    Long findReferencedStoreId(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);

    /** 只有全部门店仍存在时才允许调用方确认批量删除成功。 */
    @Delete("""
            <script>
            DELETE FROM store_info WHERE tenant_id = #{tenantId} AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteStores(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);

}
