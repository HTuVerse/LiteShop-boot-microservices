package org.hongtu.params;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付宝手机网站支付请求参数 - 基于官方业务请求参数（biz_content）
 * 参考文档：https://opendocs.alipay.com/open/02ivbs?scene=21
 *
 * 本类封装了 alipay.trade.wap.pay 接口的所有业务请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlipayTradeWapPayRequestParams {

    // ==================== 必填参数 ====================

    /**
     * 商户订单号，必填
     * string(64)，商户网站唯一订单号
     */
    private String outTradeNo;

    /**
     * 订单总金额，必填
     * price(9)，单位为元，精确到小数点后两位，取值范围：[0.01,100000000]
     */
    private BigDecimal totalAmount;

    /**
     * 订单标题，必填
     * string(256)，注意：不可使用特殊字符，如 /，=，& 等
     */
    private String subject;

    /**
     * 回调地址
     */
    private String notifyUrl;

    /**
     * 销售产品码，必填
     * string(64)，商家和支付宝签约的产品码。手机网站支付为：QUICK_WAP_WAY
     */
    @Builder.Default
    private String productCode = "QUICK_WAP_WAY";

    // ==================== 可选参数 ====================

    /**
     * 针对用户授权接口，获取用户相关数据时，用于标识用户授权关系，可选
     * string(40)
     */
    private String authToken;

    /**
     * 用户付款中途退出返回商户网站的地址，可选
     * string(400)
     */
    private String quitUrl;

    /**
     * 订单包含的商品列表信息，可选
     * GoodsDetail[]，json格式，详见商品明细说明
     */
    private String goodsDetail;

    /**
     * 绝对超时时间，可选
     * string(32)，格式为yyyy-MM-dd HH:mm:ss。超时时间范围：1m~15d
     */
    private String timeExpire;

    /**
     * 业务扩展参数，可选
     * ExtendParams对象
     */
    private String extendParams;

    /**
     * 商户传入业务信息，可选
     * string(512)，具体值要和支付宝约定，应用于安全，营销等参数直传场景，格式为json格式
     */
    private String businessParams;

    /**
     * 公用回传参数，可选
     * string(512)，如果请求时传递了该参数，则返回给商户时会回传该参数。
     * 支付宝只会在同步返回（包括跳转回商户网站）和异步通知时将该参数原样返回。
     * 本参数必须进行UrlEncode之后才可以发送给支付宝。
     */
    private String passbackParams;

    /**
     * 商户原始订单号，可选
     * string(32)，最大长度限制32位
     */
    private String merchantOrderNo;

    /**
     * 外部指定买家，可选
     * ExtUserInfo对象
     */
    private String extUserInfo;
}
