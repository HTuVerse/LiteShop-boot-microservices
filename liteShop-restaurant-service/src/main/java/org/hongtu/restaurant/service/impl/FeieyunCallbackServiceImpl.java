package org.hongtu.restaurant.service.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.Sign;
import cn.hutool.crypto.asymmetric.SignAlgorithm;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.config.FeieyunPrinterConfig;
import org.hongtu.restaurant.service.FeieyunCallbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 飞鹅打印机回调服务实现
 *
 * @author hongtu
 */
@Slf4j
@Service
public class FeieyunCallbackServiceImpl implements FeieyunCallbackService {

    @Resource
    private FeieyunPrinterConfig config;

    // 注入你的订单服务（根据实际项目调整）
    // @Autowired
    // private RestaurantOrderService orderService;

    // 注入消息通知服务（根据实际项目调整）
    // @Autowired
    // private MessageService messageService;

    // 缓存签名验证对象，避免重复创建（Hutool Sign对象）
    private volatile Sign cachedSign;

    /**
     * 处理打印回调
     *
     * 业务逻辑：
     * 1. 更新订单打印状态
     * 2. 打印成功：通知顾客"后厨已接单"
     * 3. 打印失败：记录日志、自动重试或人工介入
     * 4. 更新订单时间线
     *
     * @param orderId 打印订单ID（格式：订单号_时间戳_序列号）
     * @param sn 打印机编号
     * @param status 打印状态（1-成功，0-失败）
     * @param time 打印时间戳
     * @param msg 错误信息
     */
    @Override
    public void handlePrintCallback(String orderId, String sn, Integer status, Long time, String msg) {
        log.info("======== 处理打印回调 ========");
        log.info("打印订单ID: {}", orderId);
        log.info("打印机编号: {}", sn);
        log.info("打印状态: {}", status == 1 ? "成功" : "失败");
        log.info("打印时间: {}", formatTimestamp(time));

        try {
            // 从打印订单ID中提取业务订单号
            // 打印订单ID格式：订单号_时间戳_序列号
            String businessOrderNo = extractOrderNo(orderId);
            log.info("业务订单号: {}", businessOrderNo);

            if (status == 1) {
                // ========== 打印成功处理 ==========
                log.info("✅ 打印成功");

                // 1. 更新订单打印状态
                // orderService.updatePrintStatus(businessOrderNo, true, orderId);

                // 2. 更新订单状态为"后厨已接单"
                // orderService.updateOrderStatus(businessOrderNo, OrderStatusEnum.KITCHEN_ACCEPTED);

                // 3. 通知顾客
                // messageService.sendOrderAcceptedNotification(businessOrderNo);

                // 4. 记录订单时间线
                // orderService.addOrderTimeline(businessOrderNo, "订单已打印到后厨");

                log.info("订单 {} 打印成功，已更新订单状态", businessOrderNo);

            } else {
                // ========== 打印失败处理 ==========
                log.error("❌ 打印失败：{}", msg);

                // 1. 记录打印失败信息
                // orderService.updatePrintStatus(businessOrderNo, false, msg);

                // 2. 判断是否需要自动重试
                // if (shouldRetryPrint(businessOrderNo)) {
                //     log.info("自动重试打印订单: {}", businessOrderNo);
                //     orderService.retryPrint(businessOrderNo);
                // }

                // 3. 通知商家打印失败（重要订单）
                // if (isImportantOrder(businessOrderNo)) {
                //     messageService.sendPrintFailureAlert(businessOrderNo, sn, msg);
                // }

                log.error("订单 {} 打印失败，原因: {}", businessOrderNo, msg);
            }

        } catch (Exception e) {
            log.error("处理打印回调异常", e);
        }

        log.info("==============================");
    }

    /**
     * 处理扫码回调
     *
     * 业务逻辑：
     * 1. 记录扫码用户信息
     * 2. 根据二维码内容执行对应业务
     * 3. 会员营销：发放优惠券/积分
     * 4. 用户行为分析
     *
     * @param openId 微信OpenID
     * @param sn 打印机编号
     * @param orderId 订单ID
     * @param time 扫码时间戳
     * @param content 二维码内容
     */
    @Override
    public void handleScanCallback(String openId, String sn, String orderId, Long time, String content) {
        log.info("======== 处理扫码回调 ========");
        log.info("微信OpenID: {}", openId);
        log.info("打印机编号: {}", sn);
        log.info("订单ID: {}", orderId);
        log.info("扫码时间: {}", formatTimestamp(time));
        log.info("二维码内容: {}", content);

        try {
            // 从打印订单ID中提取业务订单号
            String businessOrderNo = extractOrderNo(orderId);

            // ========== 场景1：自助评价 ==========
            if (content != null && content.contains("evaluate")) {
                log.info("场景：自助评价");

                // 1. 生成评价页面链接
                // String evaluateUrl = generateEvaluateUrl(businessOrderNo, openId);

                // 2. 发送模板消息给用户，引导评价
                // messageService.sendEvaluateInvitation(openId, businessOrderNo, evaluateUrl);

                log.info("已向用户发送评价邀请");
            }

            // ========== 场景2：会员营销 ==========
            else if (content != null && content.contains("coupon")) {
                log.info("场景：领取优惠券");

                // 1. 检查是否已领取
                // boolean hasClaimed = couponService.hasClaimedPrintCoupon(openId, businessOrderNo);

                // 2. 发放优惠券
                // if (!hasClaimed) {
                //     couponService.grantPrintCoupon(openId);
                //     messageService.sendCouponNotification(openId);
                //     log.info("已为用户发放优惠券");
                // }

            }

            // ========== 场景3：桌边服务 ==========
            else if (content != null && content.contains("service")) {
                log.info("场景：呼叫服务");

                // 1. 获取桌号信息
                // String tableNo = extractTableNo(content);

                // 2. 通知服务员
                // messageService.notifyWaiter(tableNo, "顾客呼叫服务");

                log.info("已通知服务员");
            }

            // ========== 场景4：用户行为分析 ==========
            // 记录扫码行为，用于数据分析
            // userBehaviorService.recordScanAction(openId, businessOrderNo, sn, content);

            log.info("扫码回调处理完成");

        } catch (Exception e) {
            log.error("处理扫码回调异常", e);
        }

        log.info("==============================");
    }

    /**
     * 验证回调签名（使用Hutool简化实现）
     *
     * 根据飞鹅云官方文档，签名验证步骤：
     * 1. 取出签名值 sign
     * 2. 将签名参数（sign）使用 base64 解码为签名值字节码串
     * 3. 使用飞鹅云公钥、待验证签名字符串、签名值字节码串进行 SHA256WithRSA 验证签名值是否正确
     *
     * @param dataString 待验证的数据字符串（回调参数拼接后的字符串）
     * @param sign Base64编码的签名字符串
     * @return true-验证通过，false-验证失败
     */
    @Override
    public boolean verifySignature(String dataString, String sign) {
        if (dataString == null || sign == null) {
            log.error("签名验证失败：参数不能为空 - dataString: {}, sign: {}",
                    dataString != null, sign != null);
            return false;
        }

        try {
            log.debug("======== 开始验证签名 ========");
            log.debug("待验证字符串: {}", dataString);
            log.debug("Base64签名: {}", sign);

            // 1. 获取签名验证对象（Hutool封装）
            Sign signObj = getSignObject();
            if (signObj == null) {
                log.error("创建签名对象失败");
                return false;
            }

            // 2. 使用Hutool进行签名验证（内部自动处理Base64解码和SHA256WithRSA验证）
            boolean isValid = signObj.verify(
                    dataString.getBytes(StandardCharsets.UTF_8),  // 待验证的数据
                    SecureUtil.decode(sign)                        // Base64解码签名
            );

            log.debug("签名验证结果: {}", isValid ? "✅ 通过" : "❌ 失败");
            log.debug("==============================");

            return isValid;

        } catch (Exception e) {
            log.error("签名验证异常", e);
            return false;
        }
    }

    /**
     * 获取签名验证对象（使用Hutool简化）
     *
     * 使用双重检查锁定模式缓存Sign对象
     *
     * @return Hutool Sign对象
     */
    private Sign getSignObject() {
        if (cachedSign == null) {
            synchronized (this) {
                if (cachedSign == null) {
                    try {
                        // 获取公钥字符串
                        String publicKeyStr = config.getPublicKey();
                        if (publicKeyStr == null || publicKeyStr.isEmpty()) {
                            log.error("公钥配置为空");
                            return null;
                        }

                        log.debug("创建Hutool签名对象...");

                        // 使用Hutool创建RSA签名验证对象
                        // SignAlgorithm.SHA256withRSA 对应官方文档的 SHA256WithRSA
                        cachedSign = SecureUtil.sign(SignAlgorithm.SHA256withRSA, null, publicKeyStr);

                        log.info("Hutool签名对象创建成功");

                    } catch (Exception e) {
                        log.error("创建签名对象失败", e);
                        return null;
                    }
                }
            }
        }
        return cachedSign;
    }


    /**
     * 从打印订单ID中提取业务订单号
     *
     * @param printOrderId 打印订单ID（格式：订单号_时间戳_序列号）
     * @return 业务订单号
     */
    private String extractOrderNo(String printOrderId) {
        if (printOrderId == null || !printOrderId.contains("_")) {
            return printOrderId;
        }
        // 打印订单ID格式：订单号_时间戳_序列号
        // 提取第一部分作为业务订单号
        return printOrderId.split("_")[0];
    }

    /**
     * 格式化时间戳
     *
     * @param timestamp 时间戳（秒）
     * @return 格式化后的时间字符串
     */
    private String formatTimestamp(Long timestamp) {
        if (timestamp == null) {
            return "未知";
        }
        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(timestamp),
                ZoneId.systemDefault()
        );
        return dateTime.toString();
    }
}

