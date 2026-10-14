package org.hongtu.restaurant.service;

import org.hongtu.restaurant.VO.RestaurantOrderVO;

import java.util.List;
import java.util.Map;

/**
 * 订单打印服务
 * 整合打印模板配置和飞鹅打印机接口
 *
 * @author hongtu
 */
public interface OrderPrintService {

    Map<String, Object> printOrder(Long orderId);

    Map<String, Object> printOrder(Long orderId, Long printerId);

    /**
     * 打印订单
     *
     * @param orderId 订单ID
     * @return 打印结果
     */
    Map<String, Object> printOrder(Long orderId,Long printerId,Long templateId);

    /**
     * 打印订单（使用订单VO对象）
     *
     * @param orderVO 订单VO
     * @param printerId 打印机ID
     * @param templateId 模板ID（可选）
     * @return 打印结果
     */
    Map<String, Object> printOrderVO(RestaurantOrderVO orderVO, Long printerId, Long templateId);

    /**
     * 批量打印订单
     *
     * @param orderIds 订单ID列表
     * @param printerId 打印机ID（可选）
     * @return 打印结果统计
     */
    Map<String, Object> batchPrintOrders(Long[] orderIds, Long printerId);

    /**
     * 测试打印（打印测试小票）
     *
     * @param printerId 打印机ID
     * @param templateId 模板ID（可选）
     * @return 打印结果
     */
    Map<String, Object> testPrint(Long printerId, Long templateId);

    /**
     * 仅打印加菜项（加菜单），不打印整单
     *
     * @param orderId 订单ID
     * @param orderItemIds 本批追加的订单项ID列表
     * @param printerId 打印机ID
     * @param templateId 模板ID（可选）
     * @return 打印结果
     */
    Map<String, Object> printAddItems(Long orderId, List<Long> orderItemIds, Long printerId, Long templateId);

    /** 把失败的打印任务重新放回待发送队列。 */
    boolean reprint(Long printJobId);

}
