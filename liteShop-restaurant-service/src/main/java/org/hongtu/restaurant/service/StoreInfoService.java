package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.restaurant.DTO.StoreInfo;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * <p>
 * 门店信息表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
public interface StoreInfoService extends IService<StoreInfo> {

    /**
     * 分页查询门店列表
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param storeName 门店名称（模糊查询）
     * @param status 门店状态
     * @return 分页结果
     */
    Page<StoreInfo> getStorePage(Integer currentPage, Integer pageSize, String storeName, Integer status);

    /**
     * 新增门店
     * @param storeInfo 门店信息
     * @return 是否成功
     */
    boolean addStore(StoreInfo storeInfo);

    /**
     * 更新门店信息
     * @param storeInfo 门店信息
     * @return 是否成功
     */
    boolean updateStore(StoreInfo storeInfo);

    /**
     * 删除门店
     * @param id 门店ID
     * @return 是否成功
     */
    boolean deleteStore(Long id);

    /**
     * 批量删除门店
     * @param ids 门店ID列表
     * @return 是否成功
     */
    boolean deleteStoresBatch(List<Long> ids);

    /**
     * 修改门店状态
     * @param id 门店ID
     * @param status 状态
     * @return 是否成功
     */
    boolean updateStoreStatus(Long id, Integer status);

}
