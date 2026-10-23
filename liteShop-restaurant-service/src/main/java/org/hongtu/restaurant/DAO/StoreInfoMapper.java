package org.hongtu.restaurant.DAO;

import org.apache.ibatis.annotations.Mapper;
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

}
