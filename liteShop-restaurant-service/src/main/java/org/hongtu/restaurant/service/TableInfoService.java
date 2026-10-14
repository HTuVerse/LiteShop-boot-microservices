package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.restaurant.DTO.TableInfo;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.restaurant.VO.TableInfoVO;


import java.util.List;
import java.util.Map;

/**
 * <p>
 * 餐桌信息表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
public interface TableInfoService extends IService<TableInfo> {

    /**
     * 查询桌位详情
     * @param id 桌位ID
     * @return 桌位详情
     */
    TableInfoVO getTableDetail(Long id);

    /**
     * 添加桌位
     * @param tableInfo 桌位信息
     * @return 是否成功
     */
    boolean addTable(TableInfo tableInfo);

    /**
     * 更新桌位信息
     * @param tableInfo 桌位信息
     * @return 是否成功
     */
    boolean updateTable(TableInfo tableInfo);

    /**
     * 删除桌位
     * @param id 桌位ID
     * @return 是否成功
     */
    boolean deleteTable(Long id);

    /**
     * 批量删除桌位
     * @param ids 桌位ID列表
     * @return 是否成功
     */
    boolean batchDeleteTable(List<Long> ids);

    /**
     * 分页查询桌位列表
     * @param storeId 门店ID
     * @param tableNumber 桌位编号
     * @param tableName 桌位名称
     * @param status 状态
     * @param page 分页对象
     * @return 分页桌位列表(包含门店名称)
     */
    Page<TableInfoVO> getTablePage(Long storeId, String tableNumber, String tableName, Integer status, Page<TableInfo> page);

    /**
     * 更新桌位状态
     * @param id 桌位ID
     * @param status 状态
     * @return 是否成功
     */
    boolean updateTableStatus(Long id, Integer status);

    /**
     * 轮换扫码票据，使已发出的桌台二维码失效。
     */
    String rotateScanToken(Long id);

}
