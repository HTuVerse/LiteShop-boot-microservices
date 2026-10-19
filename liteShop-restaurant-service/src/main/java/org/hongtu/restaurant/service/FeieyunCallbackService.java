package org.hongtu.restaurant.service;

/**
 * 飞鹅打印机回调服务接口
 *
 * @author hongtu
 */
public interface FeieyunCallbackService {

    /**
     * 验证回调签名
     *
     * 使用飞鹅云公钥进行 RSA SHA256 签名验证
     *
     * @param dataString 待验证的数据字符串
     * @param sign Base64编码的签名字符串
     * @return true-验证通过，false-验证失败
     */
    boolean verifySignature(String orderId, String dataString, String sign);

    /** 按小票码和设备定位租户，再验证扫码通知的飞鹅签名。 */
    boolean verifyScanSignature(String sn, String orderId, String content, String dataString, String sign);

    /**
     * 处理打印回调
     *
     * @param orderId 打印订单ID
     * @param sn 打印机编号
     * @param status 打印状态（1-成功，0-失败）
     * @param time 打印时间戳
     * @param msg 错误信息（失败时）
     */
    void handlePrintCallback(String orderId, String sn, Integer status, Long time, String msg);

    /**
     * 处理扫码回调
     *
     * @param openId 微信OpenID
     * @param sn 打印机编号
     * @param orderId 订单ID
     * @param time 扫码时间戳
     * @param content 二维码内容
     */
    void handleScanCallback(String openId, String sn, String orderId, Long time, String content);
}

