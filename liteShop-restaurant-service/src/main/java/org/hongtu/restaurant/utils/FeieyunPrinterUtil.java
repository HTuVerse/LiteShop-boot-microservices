package org.hongtu.restaurant.utils;

import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.config.FeieyunPrinterConfig;
import org.hongtu.utils.HttpClientUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 飞鹅打印机工具类
 * 封装飞鹅云打印机API接口调用
 *
 * @author hongtu
 */
@Slf4j
@Component
public class FeieyunPrinterUtil {

    @Resource
    private FeieyunPrinterConfig config;

    /**
     * 添加打印机接口（支持批量添加）
     *
     * @param printerList 打印机列表，格式：打印机编号#打印机识别码#备注名称#流量卡号码
     *                    多台打印机请使用换行符(\n)分隔，每次最多100台
     *                    示例：sn1#key1#remark1#carnum1\nsn2#key2#remark2#carnum2
     * @return 返回JSON字符串
     *         成功示例：{"msg":"ok","ret":0,"data":{"ok":["sn#key#remark#carnum"],"no":[]},"serverExecutedTime":3}
     *         失败示例：{"msg":"参数错误 : 该帐号未注册.","ret":-2,"data":null,"serverExecutedTime":37}
     */
    public String addPrinter(String printerList) {
        log.info("调用添加打印机接口");

        try {
            // 构建请求参数（包含4个必要的公共参数：user、stime、sig、apiname）
            Map<String, String> params = buildBaseParams("Open_printerAddlist");

            // 添加业务参数
            params.put("printerContent", printerList);

            // 发送POST请求
            String result = HttpClientUtil.doPost(config.getApiUrl() + "printerAddlist", params);
            log.info("添加打印机接口返回结果：{}", result);

            return result;
        } catch (IOException e) {
            log.error("调用添加打印机接口失败", e);
            return buildErrorResponse("网络请求失败：" + e.getMessage());
        }
    }

    /**
     * 小票机打印订单接口
     *
     * @param sn 打印机编号
     * @param content 打印内容（支持特定标签格式化）
     * @param times 打印联数（默认1）
     * @return 返回JSON字符串
     *         成功示例：{"msg":"ok","ret":0,"data":"订单ID","serverExecutedTime":5}
     *         失败示例：{"msg":"错误描述","ret":非0,"data":"null","serverExecutedTime":5}
     */
    public String print(String sn, String content, int times) {
        log.info("调用小票机打印接口，打印机编号：{}，打印联数：{}", sn, times);

        try {
            // 构建请求参数（包含4个必要的公共参数：user、stime、sig、apiname）
            Map<String, String> params = buildBaseParams("Open_printMsg");

            // 添加业务参数
            params.put("sn", sn);
            params.put("content", content);
            params.put("times", String.valueOf(times));

            // 发送POST请求
            String result = HttpClientUtil.doPost(config.getApiUrl() + "printMsg", params);
            log.info("小票机打印接口返回结果：{}", result);

            return result;
        } catch (IOException e) {
            log.error("调用小票机打印接口失败", e);
            return buildErrorResponse("网络请求失败：" + e.getMessage());
        }
    }

    /**
     * 小票机打印订单接口（默认打印1联）
     *
     * @param sn 打印机编号
     * @param content 打印内容
     * @return 返回JSON字符串
     */
    public String print(String sn, String content) {
        return print(sn, content, 1);
    }

    /**
     * 小票机打印订单接口（使用配置中的默认打印机）
     *
     * @param content 打印内容
     * @param times 打印联数
     * @return 返回JSON字符串
     */
    public String print(String content, int times) {
        if (config.getSn() == null || config.getSn().isEmpty()) {
            log.error("配置中未设置默认打印机编号");
            return buildErrorResponse("配置中未设置默认打印机编号");
        }
        return print(config.getSn(), content, times);
    }

    /**
     * 小票机打印订单接口（使用配置中的默认打印机，打印1联）
     *
     * @param content 打印内容
     * @return 返回JSON字符串
     */
    public String print(String content) {
        return print(content, 1);
    }

    /**
     * 查询订单是否打印成功
     *
     * @param orderId 订单ID（调用打印接口返回的订单ID）
     * @return 返回JSON字符串
     *         成功示例：{"msg":"ok","ret":0,"data":true,"serverExecutedTime":2}
     *                  data: true-已打印, false-未打印
     *         失败示例：{"msg":"错误描述","ret":非0,"data":null,"serverExecutedTime":7}
     */
    public String queryOrderState(String orderId) {
        log.info("查询订单打印状态，订单ID：{}", orderId);

        try {
            // 构建请求参数（包含4个必要的公共参数：user、stime、sig、apiname）
            Map<String, String> params = buildBaseParams("Open_queryOrderState");

            // 添加业务参数
            params.put("orderid", orderId);

            // 发送POST请求
            String result = HttpClientUtil.doPost(config.getApiUrl() + "queryOrderState", params);
            log.info("查询订单状态返回结果：{}", result);

            return result;
        } catch (IOException e) {
            log.error("查询订单状态失败", e);
            return buildErrorResponse("网络请求失败：" + e.getMessage());
        }
    }

    /**
     * 查询指定打印机某天的订单统计数
     *
     * @param sn 打印机编号
     * @param date 查询日期，格式：yyyy-MM-dd（如：2024-10-30）
     * @return 返回JSON字符串
     *         成功示例：{"msg":"ok","ret":0,"data":{"print":6,"waiting":1},"serverExecutedTime":9}
     *                  print: 已打印数量, waiting: 等待打印数量
     *         失败示例：{"msg":"错误描述","ret":非0,"data":null,"serverExecutedTime":5}
     */
    public String queryOrderInfoByDate(String sn, String date) {
        log.info("查询打印机订单统计，打印机：{}，日期：{}", sn, date);

        try {
            // 构建请求参数（包含4个必要的公共参数：user、stime、sig、apiname）
            Map<String, String> params = buildBaseParams("Open_queryOrderInfoByDate");

            // 添加业务参数
            params.put("sn", sn);
            params.put("date", date);  // 格式：yyyy-MM-dd

            // 发送POST请求
            String result = HttpClientUtil.doPost(config.getApiUrl() + "queryOrderInfoByDate", params);
            log.info("查询订单统计返回结果：{}", result);

            return result;
        } catch (IOException e) {
            log.error("查询订单统计失败", e);
            return buildErrorResponse("网络请求失败：" + e.getMessage());
        }
    }

    /**
     * 查询指定打印机某天的订单统计数（使用默认打印机）
     *
     * @param date 查询日期，格式：yyyy-MM-dd
     * @return 返回JSON字符串
     */
    public String queryOrderInfoByDate(String date) {
        if (config.getSn() == null || config.getSn().isEmpty()) {
            log.error("配置中未设置默认打印机编号");
            return buildErrorResponse("配置中未设置默认打印机编号");
        }
        return queryOrderInfoByDate(config.getSn(), date);
    }

    /**
     * 查询打印机状态
     *
     * @param sn 打印机编号
     * @return 返回JSON字符串
     *         成功示例：{"msg":"ok","ret":0,"data":"在线","serverExecutedTime":4}
     *         可能的状态值：
     *           - "在线" - 打印机在线正常
     *           - "离线" - 打印机离线
     *           - "缺纸" - 打印机缺纸
     *         失败示例：{"msg":"错误描述","ret":非0,"data":null,"serverExecutedTime":5}
     */
    public String queryPrinterStatus(String sn) {
        log.info("查询打印机状态，打印机：{}", sn);

        try {
            // 构建请求参数（包含4个必要的公共参数：user、stime、sig、apiname）
            Map<String, String> params = buildBaseParams("Open_queryPrinterStatus");

            // 添加业务参数
            params.put("sn", sn);

            // 发送POST请求
            String result = HttpClientUtil.doPost(config.getApiUrl() + "queryPrinterStatus", params);
            log.info("查询打印机状态返回结果：{}", result);

            return result;
        } catch (IOException e) {
            log.error("查询打印机状态失败", e);
            return buildErrorResponse("网络请求失败：" + e.getMessage());
        }
    }

    /**
     * 查询打印机状态（使用默认打印机）
     *
     * @return 返回JSON字符串
     */
    public String queryPrinterStatus() {
        if (config.getSn() == null || config.getSn().isEmpty()) {
            log.error("配置中未设置默认打印机编号");
            return buildErrorResponse("配置中未设置默认打印机编号");
        }
        return queryPrinterStatus(config.getSn());
    }

    /**
     * 清空待打印队列
     *
     * 应用场景：
     * 1. 营业结束时清空未打印的测试订单
     * 2. 打印机故障恢复后清空积压订单
     * 3. 系统维护时清空队列
     *
     * @param sn 打印机编号
     * @return 返回JSON字符串
     *         成功示例：{"msg":"ok","ret":0,"data":true,"serverExecutedTime":2}
     *         失败示例：{"msg":"错误描述","ret":非0,"data":null,"serverExecutedTime":5}
     */
    public String clearPrinterQueue(String sn) {
        log.info("清空打印机待打印队列，打印机：{}", sn);

        try {
            // 构建请求参数（包含4个必要的公共参数：user、stime、sig、apiname）
            Map<String, String> params = buildBaseParams("Open_delPrinterSqs");

            // 添加业务参数
            params.put("sn", sn);

            // 发送POST请求
            String result = HttpClientUtil.doPost(config.getApiUrl() + "delPrinterSqs", params);
            log.info("清空打印队列返回结果：{}", result);

            return result;
        } catch (IOException e) {
            log.error("清空打印队列失败", e);
            return buildErrorResponse("网络请求失败：" + e.getMessage());
        }
    }

    /**
     * 清空待打印队列（使用默认打印机）
     *
     * @return 返回JSON字符串
     */
    public String clearPrinterQueue() {
        if (config.getSn() == null || config.getSn().isEmpty()) {
            log.error("配置中未设置默认打印机编号");
            return buildErrorResponse("配置中未设置默认打印机编号");
        }
        return clearPrinterQueue(config.getSn());
    }

    /**
     * 构建基础请求参数
     * 根据飞鹅云官方文档要求，所有接口必须包含4个公共请求参数：
     * 1. user - 用户账号（必填）
     * 2. stime - 当前UNIX时间戳，10位，精确到秒（必填）
     * 3. sig - 签名，SHA1(user+ukey+stime)（必填）
     * 4. apiname - 接口名称（必填）
     *
     * @param apiname 接口名称
     * @return 包含4个必要公共参数的Map
     */
    private Map<String, String> buildBaseParams(String apiname) {
        Map<String, String> params = new HashMap<>();

        // 1. user - 用户账号
        String user = config.getUser();
        params.put("user", user);

        // 2. stime - 当前UNIX时间戳（秒）
        String stime = String.valueOf(System.currentTimeMillis() / 1000);
        params.put("stime", stime);

        // 3. sig - 签名（SHA1加密）
        String signature = generateSignature(user, config.getUkey(), stime);
        params.put("sig", signature);

        // 4. apiname - 接口名称
        params.put("apiname", apiname);

        log.debug("构建打印请求参数 - apiname: {}", apiname);

        return params;
    }

    /**
     * 生成签名
     * 签名算法：SHA1(USER + UKEY + STIME)
     *
     * @param user 用户账号
     * @param ukey 用户密钥
     * @param stime 时间戳
     * @return 签名字符串
     */
    private String generateSignature(String user, String ukey, String stime) {
        String source = user + ukey + stime;
        return DigestUtil.sha1Hex(source);
    }

    /**
     * 构建错误响应
     *
     * @param errorMsg 错误信息
     * @return JSON格式的错误响应
     */
    private String buildErrorResponse(String errorMsg) {
        JSONObject response = new JSONObject();
        response.put("msg", errorMsg);
        response.put("ret", -1);
        response.put("data", null);
        return response.toJSONString();
    }

}

