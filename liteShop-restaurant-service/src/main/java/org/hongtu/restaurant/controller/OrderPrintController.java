package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.service.OrderPrintService;
import org.hongtu.restaurant.DTO.PrintTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单打印接口
 * 提供订单打印、批量打印、测试打印等功能
 *
 * @author hongtu
 */
@Slf4j
@RestController
@RequestMapping("/orderPrintApi")
public class OrderPrintController {

    @Resource
    private OrderPrintService orderPrintService;

    /**
     * 打印订单（使用门店默认打印机和模板）
     *
     * @param orderId 订单ID
     * @return 打印结果
     */
    @PostMapping("/printByOrderId")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:printer:print", "restaurant:order:print", "operations:direct-cashier:print", "operations:table-cashier:print"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> printOrder(
            @RequestParam("orderId") Long orderId,
            @RequestParam(required = false) Long printerId,
            @RequestParam(required = false) Long templateId
            ) {
        log.info("打印订单（默认打印机）：orderId={}", orderId);

        
            Map<String, Object> result = orderPrintService.printOrderManually(orderId,printerId,templateId);
            boolean success = (boolean) result.getOrDefault("success", false);
            
            if (success) {
                return Result.success(result);
            } else {
                return Result.error((String) result.getOrDefault("msg", "打印失败"));
            }
        
    }

    /**
     * 批量打印订单
     *
     * @param orderIds 订单ID数组
     * @param printerId 打印机ID（可选）
     * @return 批量打印结果统计
     */
    @PostMapping("/batchPrint")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:printer:print", "restaurant:order:print", "operations:direct-cashier:print", "operations:table-cashier:print"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> batchPrintOrders(
            @RequestBody Long[] orderIds,
            @RequestParam(value = "printerId", required = false) Long printerId) {
        
        log.info("批量打印订单：orderIds={}, printerId={}", orderIds, printerId);

        
            Map<String, Object> result = orderPrintService.batchPrintOrders(orderIds, printerId);
            return Result.success(result);
        
    }

    /**
     * 人工补打失败的打印任务。
     */
    @PostMapping("/reprint")
    @SaCheckPermission(value = {"restaurant:printer:print", "restaurant:order:print"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> reprint(@RequestParam Long printJobId) {
        return Result.success(orderPrintService.reprint(printJobId));
    }

    /**
     * 测试打印
     *
     * @param printerId 打印机ID
     * @param templateId 模板ID（可选）
     * @return 测试打印结果
     */
    @PostMapping("/test/{printerId}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:printer:print", "restaurant:order:print", "operations:direct-cashier:print", "operations:table-cashier:print"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> testPrint(
            @PathVariable("printerId") Long printerId,
            @RequestParam(value = "templateId", required = false) Long templateId,
            @RequestBody(required = false) PrintTemplate draftTemplate) {
        
        log.info("测试打印：printerId={}, templateId={}", printerId, templateId);

        
            Map<String, Object> result = orderPrintService.testPrint(printerId, templateId, draftTemplate);
            boolean success = (boolean) result.getOrDefault("success", false);
            
            if (success) {
                return Result.success(result);
            } else {
                return Result.error((String) result.getOrDefault("msg", "测试打印失败"));
            }
        
    }

}
