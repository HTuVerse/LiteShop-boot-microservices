package org.hongtu.restaurant.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.annotation.SaIgnore;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.restaurant.DTO.PrinterInfo;
import org.hongtu.restaurant.service.PrinterInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.hongtu.restaurant.service.FeieyunCallbackService;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 飞鹅打印机管理与回调接口
 *
 * 功能说明：
 * 1. 打印机管理：添加、删除、查询打印机
 * 2. 状态查询：查询打印机在线状态
 * 3. 打印回调：打印机完成打印后，飞鹅云主动通知打印结果
 * 4. 扫码回调：设备扫描小票出餐码后更新订单状态
 *
 * 安全机制：
 * - 所有回调请求都包含 sign 参数，用于验证消息合法性
 * - sign 验证规则：使用飞鹅云公钥进行 RSA SHA256 签名验证
 * - 验证失败的请求会被拒绝，防止恶意攻击
 *
 * 配置步骤：
 * 1. 登录飞鹅云后台
 * 2. 进入"开发者中心" → "回调设置"
 * 3. 配置公网 HTTPS 回调地址：/feieyunApi/callback/print 和 /feieyunApi/callback/scan
 *
 * @author hongtu
 */
@Slf4j
@RestController
@RequestMapping("/feieyunApi")
public class FeieyunCallbackController {

    @Resource
    private FeieyunCallbackService callbackService;

    @Resource
    private PrinterInfoService printerInfoService;

    // ==================== 打印机管理接口 ====================

    /**
     * 添加打印机
     *
     * 将打印机添加到飞鹅云并保存到本地数据库
     *
     * @param printerInfo 打印机信息
     *                    - sn: 打印机编号（必填）
     *                    - printerKey: 打印机识别码（必填）
     *                    - remark: 备注名称（选填）
     *                    - cardNum: 流量卡号码（选填）
     *                    - storeId: 关联门店ID（选填）
     *                    - printerType: 打印机类型：1-小票机，2-标签机（选填，默认1）
     * @return 添加结果
     */
    @PostMapping("/printer/add")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:printer:add"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> addPrinter(@RequestBody PrinterInfo printerInfo) {
        log.info("添加打印机请求：sn={}, remark={}", printerInfo.getSn(), printerInfo.getRemark());

        
            Map<String, Object> result = printerInfoService.addPrinter(printerInfo);
            boolean success = (boolean) result.getOrDefault("success", false);

            if (success) {
                return Result.success(result);
            } else {
                return Result.error((String) result.getOrDefault("msg", "添加失败"));
            }
        
    }


    /**
     * 查询打印机状态
     *
     * 从飞鹅云查询打印机实时状态
     *
     * @param sn 打印机编号
     * @return 状态信息
     *         - status: 状态描述（在线、离线、缺纸）
     *         - statusCode: 状态码（0-离线，1-在线，2-缺纸）
     */
    @GetMapping("/printer/status/{sn}")
    @SaCheckPermission(value = {"restaurant:printer"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> queryPrinterStatus(@PathVariable("sn") String sn) {
        log.info("查询打印机状态：sn={}", sn);

        
            Map<String, Object> result = printerInfoService.queryPrinterStatus(sn);
            boolean success = (boolean) result.getOrDefault("success", false);

            if (success) {
                return Result.success(result);
            } else {
                return Result.error((String) result.getOrDefault("msg", "查询失败"));
            }
        
    }

    /**
     * 分页查询打印机列表
     *
     * @param currentPage 当前页（默认1）
     * @param pageSize 每页大小（默认10）
     * @param storeId 门店ID（可选）
     * @param status 状态（可选）：0-离线，1-在线，2-缺纸
     * @return 打印机分页列表
     */
    @GetMapping("/printer/page")
    @SaCheckPermission(value = {"restaurant:printer"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Page<PrinterInfo>> getPrinterPage(
            @RequestParam(value = "currentPage", defaultValue = "1") Integer currentPage,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
            @RequestParam(value = "storeId", required = false) Long storeId,
            @RequestParam(value = "status", required = false) Integer status) {

        log.info("分页查询打印机列表：page={}, size={}, storeId={}, status={}",
                currentPage, pageSize, storeId, status);

        
            Page<PrinterInfo> page = printerInfoService.getPrinterPage(currentPage, pageSize, storeId, status);
            return Result.success(page);
        
    }

    /**
     * 根据门店ID查询打印机列表
     *
     * @param storeId 门店ID
     * @return 打印机列表
     */
    @GetMapping("/printer/listByStore/{storeId}")
    @SaCheckPermission(value = {"restaurant:printer", "operations:templates", "restaurant:order", "operations:direct-cashier", "operations:table-cashier"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<List<PrinterInfo>> getPrinterListByStore(@PathVariable("storeId") Long storeId) {
        log.info("根据门店ID查询打印机列表：storeId={}", storeId);

        
            List<PrinterInfo> list = printerInfoService.getPrinterListByStore(storeId);
            return Result.success(list);
        
    }

    /**
     * 根据打印机编号查询打印机信息
     *
     * @param sn 打印机编号
     * @return 打印机信息
     */
    @GetMapping("/printer/getBySn/{sn}")
    @SaCheckPermission(value = {"restaurant:printer"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<PrinterInfo> getPrinterBySn(@PathVariable("sn") String sn) {
        log.info("根据编号查询打印机：sn={}", sn);

        
            PrinterInfo printer = printerInfoService.getPrinterBySn(sn);
            if (printer != null) {
                return Result.success(printer);
            } else {
                return Result.error("打印机不存在");
            }
        
    }

    /**
     * 更新打印机信息
     *
     * @param printerInfo 打印机信息
     * @return 更新结果
     */
    @PutMapping("/printer/update")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:printer:edit"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updatePrinter(@RequestBody PrinterInfo printerInfo) {
        log.info("更新打印机信息：id={}", printerInfo.getId());

        
            boolean success = printerInfoService.updatePrinter(printerInfo);
            return success ? Result.success(true) : Result.error("更新失败");
        
    }

    /**
     * 删除打印机
     *
     * @param id 打印机ID
     * @return 删除结果
     */
    @DeleteMapping("/printer/delete/{id}")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:printer:delete"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> deletePrinter(@PathVariable("id") Long id) {
        log.info("删除打印机：id={}", id);

        
            boolean success = printerInfoService.deletePrinter(id);
            return success ? Result.success(true) : Result.error("删除失败");
        
    }


    /**
     * 启用/禁用打印机
     *
     * @param id 打印机ID
     * @param enabled 是否启用：0-禁用，1-启用
     * @return 操作结果
     */
    @PutMapping("/printer/updateEnabled")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:printer:status"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Boolean> updatePrinterEnabled(
            @RequestParam("id") Long id,
            @RequestParam("enabled") Integer enabled) {
        log.info("更新打印机启用状态：id={}, enabled={}", id, enabled);

        
            boolean success = printerInfoService.updatePrinterEnabled(id, enabled);
            return success ? Result.success(true) : Result.error("操作失败");
        
    }

    /**
     * 同步所有打印机状态
     *
     * @return 同步结果
     */
    @PostMapping("/printer/syncAllStatus")
    @cn.dev33.satoken.annotation.SaCheckPermission(value = {"restaurant:printer:sync"}, mode = cn.dev33.satoken.annotation.SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> syncAllPrinterStatus() {
        log.info("同步所有打印机状态");

        
            Map<String, Object> result = printerInfoService.syncAllPrinterStatus();
            return Result.success(result);
        
    }

    /**
     * 查询打印机某天的订单统计
     *
     * @param sn 打印机编号
     * @param date 日期（格式：yyyy-MM-dd）
     * @return 订单统计信息
     */
    @GetMapping("/printer/orderInfo")
    @SaCheckPermission(value = {"restaurant:printer"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> queryOrderInfoByDate(
            @RequestParam("sn") String sn,
            @RequestParam("date") String date) {
        log.info("查询打印机订单统计：sn={}, date={}", sn, date);

        
            Map<String, Object> result = printerInfoService.queryOrderInfoByDate(sn, date);
            boolean success = (boolean) result.getOrDefault("success", false);

            if (success) {
                return Result.success(result);
            } else {
                return Result.error((String) result.getOrDefault("msg", "查询失败"));
            }
        
    }

    // ==================== 回调接口 ====================

    /**
     * 打印回调接口
     *
     * 场景：打印机完成打印后，飞鹅云会调用此接口通知打印结果
     *
     * 飞鹅以表单 POST 发送 orderId、status、stime、sign；验签后记录打印任务状态。
     *
     * @param params 飞鹅表单参数
     * @return SUCCESS 表示已处理
     */
    @PostMapping("/callback/print")
    @SaIgnore
    public String printCallback(@RequestParam Map<String, String> params) {
        try {
            String orderId = params.get("orderId");
            String status = params.get("status");
            Long time = callbackTime(params);
            if (orderId == null || status == null || time == null
                    || !callbackService.verifySignature(orderId, signedData(params), params.get("sign"))) return "FAIL";
            callbackService.handlePrintCallback(orderId, null, Integer.valueOf(status), time, null);
            return "SUCCESS";
        } catch (Exception e) {
            log.error("处理打印回调失败", e);
            return "FAIL";
        }
    }

    /**
     * 扫码回调接口
     *
     * 设备扫描正式小票的二维码后，验证设备、签名和打印任务并标记订单制作完成。
     *
     * @param params 飞鹅表单参数
     * @return SUCCESS 表示已处理
     */
    @PostMapping("/callback/scan")
    @SaIgnore
    public String scanCallback(@RequestParam Map<String, String> params) {
        try {
            String sn = params.get("sn");
            String orderId = params.get("orderId");
            String content = params.get("content");
            Long time = callbackTime(params);
            if (sn == null || content == null || time == null
                    || !callbackService.verifyScanSignature(sn, orderId, content,
                    signedData(params), params.get("sign"))) return "FAIL";
            callbackService.handleScanCallback(params.get("openId"), sn, orderId, time, content);
            return "SUCCESS";
        } catch (Exception e) {
            log.error("处理扫码回调失败", e);
            return "FAIL";
        }
    }

    /** 飞鹅签名串按非空表单参数键名排序，原值不做二次 URL 编码。 */
    private String signedData(Map<String, String> params) {
        return new TreeMap<>(params).entrySet().stream()
                .filter(entry -> !"sign".equals(entry.getKey()) && entry.getValue() != null
                        && !entry.getValue().isEmpty())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private Long callbackTime(Map<String, String> params) {
        String value = params.containsKey("stime") ? params.get("stime") : params.get("time");
        if (value == null) return null;
        long seconds = Long.parseLong(value);
        return Math.abs(System.currentTimeMillis() / 1000 - seconds) <= 600 ? seconds : null;
    }

    /**
     * 测试回调接口（用于开发测试）
     *
     * @return 测试信息
     */
    @GetMapping("/callback/test")
    @SaCheckPermission(value = {"restaurant:printer"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public JSONObject testCallback() {
        JSONObject result = new JSONObject();
        result.put("status", "ok");
        result.put("message", "飞鹅打印机回调接口正常");
        result.put("timestamp", System.currentTimeMillis());

        log.info("回调接口测试成功");
        return result;
    }
}

