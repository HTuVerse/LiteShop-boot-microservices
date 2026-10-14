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

/**
 * 飞鹅打印机管理与回调接口
 *
 * 功能说明：
 * 1. 打印机管理：添加、删除、查询打印机
 * 2. 状态查询：查询打印机在线状态
 * 3. 打印回调：打印机完成打印后，飞鹅云主动通知打印结果
 * 4. 扫码回调：顾客扫描小票上的二维码后，飞鹅云通知扫码信息
 *
 * 安全机制：
 * - 所有回调请求都包含 sign 参数，用于验证消息合法性
 * - sign 验证规则：使用飞鹅云公钥进行 RSA SHA256 签名验证
 * - 验证失败的请求会被拒绝，防止恶意攻击
 *
 * 配置步骤：
 * 1. 登录飞鹅云后台
 * 2. 进入"开发者中心" → "回调设置"
 * 3. 配置回调地址：http://your-domain.com/api/feieyun/callback/print
 *                  http://your-domain.com/api/feieyun/callback/scan
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
     * 官方回调参数（按照飞鹅云官方文档）：
     * - orderId: 订单ID（调用打印接口时返回的订单ID）
     * - status: 打印状态（1-打印成功，0-打印失败）
     * - time: 打印时间戳
     * - sign: 签名（用于验证消息合法性，必填）
     *
     * 签名验证规则：
     * 使用飞鹅云公钥进行 RSA SHA256 签名验证
     *
     * 使用场景：
     * 1. 更新订单状态为"后厨已接单"
     * 2. 通知顾客"订单已开始制作"
     * 3. 打印失败时自动重试或人工介入
     * 4. 统计打印成功率
     *
     * @param orderId 打印订单ID
     * @param status 打印状态（1-成功，0-失败）
     * @param time 打印时间戳
     * @param sign 签名（用于验证消息合法性）
     * @return 返回"ok"表示接收成功
     */
    @PostMapping("/callback/print")
    @SaIgnore
    public String printCallback(
            @RequestParam("orderId") String orderId,
            @RequestParam("status") Integer status,
            @RequestParam("time") Long time,
            @RequestParam("sign") String sign) {

        log.info("收到打印回调 - 订单ID: {}, 状态: {}, 时间: {}", orderId, status, time);

        try {
            if (time == null || Math.abs(System.currentTimeMillis() / 1000 - time) > 300) {
                log.warn("打印回调时间戳超出允许窗口: orderId={}", orderId);
                return "fail";
            }
            // 1. 构建待验证的数据字符串
            // 根据飞鹅云官方文档，将所有参数（除sign外）按字典序拼接
            StringBuilder dataString = new StringBuilder();
            dataString.append("orderId=").append(orderId);
            dataString.append("&status=").append(status);
            dataString.append("&time=").append(time);

            // 2. 验证签名，防止恶意请求
            if (!callbackService.verifySignature(dataString.toString(), sign)) {
                log.error("打印回调签名验证失败 - 订单ID: {}", orderId);
                // 签名验证失败，返回 fail 拒绝请求
                return "fail";
            }

            log.info("✅ 签名验证通过");

            // 3. 处理打印回调
            callbackService.handlePrintCallback(orderId, null, status, time, null);

            // 4. 返回"ok"表示接收成功（飞鹅云要求必须返回"ok"）
            return "ok";
        } catch (Exception e) {
            log.error("处理打印回调失败", e);
            // 处理失败返回 fail，让供应商重试，避免回调静默丢失。
            return "fail";
        }
    }

    /**
     * 扫码回调接口
     *
     * 场景：顾客扫描小票上的二维码后，飞鹅云会调用此接口通知扫码信息
     *
     * 官方回调参数（按照飞鹅云官方文档）：
     * - openId: 扫码用户的微信OpenID
     * - sn: 打印机编号
     * - orderId: 关联的打印订单ID
     * - time: 扫码时间戳
     * - content: 二维码内容
     * - sign: 签名（用于验证消息合法性，必填）
     *
     * 签名验证规则：
     * sign = MD5(user + UKEY + orderId)
     *
     * 使用场景：
     * 1. 自助评价：顾客扫码后跳转到评价页面
     * 2. 会员营销：扫码领取优惠券/积分
     * 3. 防伪溯源：查看菜品信息、原材料来源
     * 4. 桌边服务：呼叫服务员、加菜、买单
     * 5. 用户行为分析：统计扫码率、用户画像
     *
     * @param openId 微信OpenID
     * @param sn 打印机编号
     * @param orderId 订单ID
     * @param time 扫码时间
     * @param content 二维码内容
     * @param sign 签名（用于验证消息合法性）
     * @return 返回"ok"表示接收成功
     */
    @PostMapping("/callback/scan")
    @SaIgnore
    public String scanCallback(
            @RequestParam("openId") String openId,
            @RequestParam("sn") String sn,
            @RequestParam(value = "orderId", required = false) String orderId,
            @RequestParam("time") Long time,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam("sign") String sign) {

        log.info("收到扫码回调 - 打印机: {}, 订单ID: {}, 时间: {}", sn, orderId, time);

        try {
            if (time == null || Math.abs(System.currentTimeMillis() / 1000 - time) > 300) {
                return "fail";
            }
            // 1. 构建待验证的数据字符串
            // 根据飞鹅云官方文档，将所有参数（除sign外）拼接成字符串
            StringBuilder dataString = new StringBuilder();
            if (content != null && !content.isEmpty()) {
                dataString.append("content=").append(content).append("&");
            }
            dataString.append("openId=").append(openId);
            if (orderId != null && !orderId.isEmpty()) {
                dataString.append("&orderId=").append(orderId);
            }
            dataString.append("&sn=").append(sn);
            dataString.append("&time=").append(time);

            // 2. 验证签名，防止恶意请求
            if (!callbackService.verifySignature(dataString.toString(), sign)) {
                log.error("扫码回调签名验证失败: printerSn={}, orderId={}", sn, orderId);
                return "fail";
            }

            log.info("✅ 签名验证通过");

            // 3. 处理扫码回调
            callbackService.handleScanCallback(openId, sn, orderId, time, content);

            // 4. 返回"ok"表示接收成功
            return "ok";
        } catch (Exception e) {
            log.error("处理扫码回调失败", e);
            return "fail";
        }
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

