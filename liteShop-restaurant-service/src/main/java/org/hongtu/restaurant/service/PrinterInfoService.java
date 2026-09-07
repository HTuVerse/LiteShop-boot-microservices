package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.restaurant.DTO.PrinterInfo;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 飞鹅打印机信息表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-01-26
 */
public interface PrinterInfoService extends IService<PrinterInfo> {

    /**
     * 添加打印机（本地数据库 + 飞鹅云）
     * 
     * @param printerInfo 打印机信息
     * @return 添加结果
     */
    Map<String, Object> addPrinter(PrinterInfo printerInfo);

    /**
     * 查询打印机状态
     * 
     * @param sn 打印机编号
     * @return 状态信息
     */
    Map<String, Object> queryPrinterStatus(String sn);

    /**
     * 分页查询打印机列表
     * 
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param storeId 门店ID（可选）
     * @param status 状态（可选）
     * @return 分页结果
     */
    Page<PrinterInfo> getPrinterPage(Integer currentPage, Integer pageSize, Long storeId, Integer status);

    /**
     * 根据门店ID查询打印机列表
     * 
     * @param storeId 门店ID
     * @return 打印机列表
     */
    List<PrinterInfo> getPrinterListByStore(Long storeId);

    /**
     * 根据打印机编号查询
     * 
     * @param sn 打印机编号
     * @return 打印机信息
     */
    PrinterInfo getPrinterBySn(String sn);

    /**
     * 更新打印机信息
     * 
     * @param printerInfo 打印机信息
     * @return 是否成功
     */
    boolean updatePrinter(PrinterInfo printerInfo);

    /**
     * 删除打印机
     * 
     * @param id 打印机ID
     * @return 是否成功
     */
    boolean deletePrinter(Long id);

    /**
     * 启用/禁用打印机
     * 
     * @param id 打印机ID
     * @param enabled 是否启用
     * @return 是否成功
     */
    boolean updatePrinterEnabled(Long id, Integer enabled);

    /**
     * 同步所有打印机状态
     * 
     * @return 同步结果
     */
    Map<String, Object> syncAllPrinterStatus();

    /**
     * 查询打印机某天的订单统计
     * 
     * @param sn 打印机编号
     * @param date 日期（格式：yyyy-MM-dd）
     * @return 订单统计信息
     */
    Map<String, Object> queryOrderInfoByDate(String sn, String date);

    /**
     * 获取门店默认打印机
     *
     * @param storeId 门店ID
     * @return 打印机信息
     */
    PrinterInfo getDefaultPrinterByStore(Long storeId);

    /**
     * 获取门店所有启用的打印机列表
     *
     * @param storeId 门店ID
     * @return 启用的打印机列表
     */
    List<PrinterInfo> getEnabledPrintersByStore(Long storeId);
}
