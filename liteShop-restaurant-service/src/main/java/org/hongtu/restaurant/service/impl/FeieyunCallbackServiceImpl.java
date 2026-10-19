package org.hongtu.restaurant.service.impl;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.asymmetric.Sign;
import cn.hutool.crypto.asymmetric.SignAlgorithm;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.restaurant.config.FeieyunPrinterConfig;
import org.hongtu.restaurant.service.FeieyunCallbackService;
import org.hongtu.restaurant.DAO.PrintJobMapper;
import org.hongtu.restaurant.DTO.PrintJob;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.DAO.RestaurantOrderMapper;
import org.hongtu.restaurant.DAO.PrinterInfoMapper;
import org.hongtu.restaurant.enums.OrderStatusEnum;
import org.hongtu.restaurant.service.RestaurantOrderService;
import org.hongtu.restaurant.utils.ReceiptScanCode;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Base64;
import javax.crypto.Cipher;

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

    @Resource
    private PrintJobMapper printJobMapper;

    @Resource
    private RestaurantOrderMapper restaurantOrderMapper;

    @Resource
    private PrinterInfoMapper printerInfoMapper;

    @Resource
    private RestaurantOrderService restaurantOrderService;

    /**
     * 处理打印回调
     *
     * 只更新打印任务；订单制作完成由设备扫码触发。
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
        log.info("打印状态: {}", Integer.valueOf(1).equals(status) ? "成功" : "失败");
        log.info("打印时间: {}", formatTimestamp(time));

        try {
            PrintJob printJob = TenantIgnore.get(() -> printJobMapper.selectOne(
                    new LambdaQueryWrapper<PrintJob>()
                            .eq(PrintJob::getProviderOrderId, orderId)
                            .last("LIMIT 1")));
            if (printJob == null || printJob.getTenantId() == null) {
                throw new IllegalArgumentException("打印任务不存在");
            }
            TenantScope.run(printJob.getTenantId(), () -> {
                PrintJob update = new PrintJob();
                update.setId(printJob.getId());
                update.setStatus(status != null && status == 1 ? 1 : 2);
                update.setLastError(status != null && status == 1 ? null : msg);
                update.setCallbackTime(LocalDateTime.now());
                update.setUpdatedAt(LocalDateTime.now());
                printJobMapper.updateById(update);
            });
            if (Integer.valueOf(1).equals(status)) {
                log.info("打印任务已标记成功: orderId={}", printJob.getOrderId());
            } else {
                log.error("打印任务已标记失败: orderId={}, reason={}", printJob.getOrderId(), msg);
            }

        } catch (Exception e) {
            log.error("处理打印回调异常", e);
            throw new IllegalStateException("打印回调业务处理失败", e);
        }

        log.info("==============================");
    }

    /** 核对扫码设备和已投递的小票，再按订单既有状态规则标记制作完成。 */
    @Override
    public void handleScanCallback(String openId, String sn, String orderId, Long time, String content) {
        PrintJob job = findScanJob(sn, orderId, content);
        if (job == null) throw new IllegalArgumentException("未找到匹配的小票打印任务");
        TenantScope.run(job.getTenantId(), () -> {
            RestaurantOrder order = restaurantOrderMapper.selectById(job.getOrderId());
            if (order == null || !Objects.equals(order.getStoreId(), job.getStoreId())) {
                throw new IllegalStateException("小票与订单门店不匹配");
            }
            // 重复回调和已经结束的订单不倒退状态。
            if (order.getStatus() == OrderStatusEnum.PREPARED
                    || order.getStatus() == OrderStatusEnum.COMPLETED
                    || order.getStatus() == OrderStatusEnum.CANCELLED) return;
            if (!restaurantOrderService.updateOrderStatus(job.getOrderId(), OrderStatusEnum.PREPARED)) {
                throw new IllegalStateException("更新订单出餐状态失败");
            }
            log.info("设备扫码完成出餐: orderId={}, printerSn={}", job.getOrderId(), sn);
        });
    }

    @Override
    public boolean verifyScanSignature(String sn, String orderId, String content, String dataString, String sign) {
        PrintJob job = findScanJob(sn, orderId, content);
        return job != null && verifyWithTenant(job.getTenantId(), dataString, sign);
    }

    /** 出餐宝上传 RSA 加密扫码内容，按设备归属租户解密后再精确匹配已打印票据。 */
    @Override
    public void handleEncryptedScanCallback(String sn, String result) {
        if (sn == null || sn.isBlank() || result == null || result.isBlank())
            throw new IllegalArgumentException("扫码回调参数不完整");
        List<Long> tenantIds = TenantIgnore.get(() -> printerInfoMapper.findTenantIdsBySn(sn));
        for (Long tenantId : tenantIds) {
            String privateKey = config.getScanPrivateKey(tenantId);
            if (privateKey == null || privateKey.isBlank()) continue;
            String content;
            try {
                content = decryptScanResult(result, privateKey);
            } catch (Exception ignored) {
                continue;
            }
            PrintJob job = findScanJob(sn, null, content);
            if (job == null || !tenantId.equals(job.getTenantId())) continue;
            handleScanCallback(null, sn, null, Instant.now().getEpochSecond(), content);
            return;
        }
        throw new IllegalArgumentException("扫码内容未匹配到本设备已打印的小票");
    }

    private String decryptScanResult(String result, String privateKey) throws Exception {
        String pem = privateKey.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem))));
        return new String(cipher.doFinal(Base64.getDecoder().decode(result)), StandardCharsets.UTF_8);
    }

    /** 扫码消息可能没有飞鹅打印单号，仍须精确匹配设备、订单和实际下发的二维码。 */
    private PrintJob findScanJob(String sn, String providerOrderId, String content) {
        Long businessOrderId = ReceiptScanCode.orderId(content);
        if (businessOrderId == null || sn == null || sn.isBlank()) return null;
        List<PrintJob> jobs = TenantIgnore.get(() -> printJobMapper.selectList(
                new LambdaQueryWrapper<PrintJob>()
                        .eq(PrintJob::getOrderId, businessOrderId)
                        .eq(PrintJob::getPrinterSn, sn)
                        .in(PrintJob::getStatus, 0, 1)));
        String printedQr = "<QR>" + content + "</QR>";
        return jobs.stream().filter(job -> job.getTenantId() != null
                        && job.getProviderOrderId() != null
                        && (providerOrderId == null || providerOrderId.isBlank()
                            || providerOrderId.equals(job.getProviderOrderId()))
                        && job.getContent() != null && job.getContent().contains(printedQr))
                .findFirst().orElse(null);
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
    public boolean verifySignature(String orderId, String dataString, String sign) {
        if (orderId == null) return false;
        try {
            PrintJob job = TenantIgnore.get(() -> printJobMapper.selectOne(
                    new LambdaQueryWrapper<PrintJob>().eq(PrintJob::getProviderOrderId, orderId).last("LIMIT 1")));
            return job != null && verifyWithTenant(job.getTenantId(), dataString, sign);
        } catch (Exception e) {
            log.error("签名验证异常", e);
            return false;
        }
    }

    private boolean verifyWithTenant(Long tenantId, String dataString, String sign) {
        if (tenantId == null || dataString == null || sign == null) return false;
        try {
            Sign verifier = getSignObject(tenantId);
            return verifier != null && verifier.verify(dataString.getBytes(StandardCharsets.UTF_8),
                    SecureUtil.decode(sign));
        } catch (Exception e) {
            log.warn("飞鹅回调签名校验失败: tenantId={}", tenantId, e);
            return false;
        }
    }

    /**
     * 获取签名验证对象（使用Hutool简化）
     *
     * 按打印任务所属租户读取公钥，轮换后立即生效。
     *
     * @return Hutool Sign对象
     */
    private Sign getSignObject(Long tenantId) {
        String publicKey = config.getPublicKey(tenantId);
        if (publicKey == null || publicKey.isBlank()) return null;
        // 不跨租户缓存，租户轮换公钥后立即生效。
        return SecureUtil.sign(SignAlgorithm.SHA256withRSA, null, publicKey);
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

