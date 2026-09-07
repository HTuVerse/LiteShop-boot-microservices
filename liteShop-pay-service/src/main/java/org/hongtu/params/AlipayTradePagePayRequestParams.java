package org.hongtu.params;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * 支付宝电脑网站支付请求参数 - 基于官方业务请求参数（biz_content）
 * 参考文档：https://opendocs.alipay.com/open/028r8t?scene=22
 * 
 * 本类封装了 alipay.trade.page.pay 接口的所有业务请求参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlipayTradePagePayRequestParams {

    // ==================== 必填参数 ====================
    
    /**
     * 商户订单号，必填
     * string(64)，由商家自定义，64个字符以内，仅支持字母、数字、下划线且需保证在商户端不重复
     */
    private String outTradeNo;

    /**
     * 订单总金额，必填
     * price(11)，单位为元，精确到小数点后两位，取值范围为 [0.01,100000000]。金额不能为0
     */
    private BigDecimal totalAmount;

    /**
     * 订单标题，必填
     * string(256)，注意：不可使用特殊字符，如 /，=，& 等
     */
    private String subject;

    /**
     * 销售产品码，必填
     * string(64)，与支付宝签约的产品码名称。注：目前电脑支付场景下仅支持FAST_INSTANT_TRADE_PAY
     */
    @Builder.Default
    private String productCode = "FAST_INSTANT_TRADE_PAY";

    // ==================== 可选参数 ====================
    
    /**
     * PC扫码支付的方式，可选
     * string(2)，支持前置模式和跳转模式
     * 0：订单码-简约前置模式，对应 iframe 宽度不能小于600px，高度不能小于300px
     * 1：订单码-前置模式，对应iframe 宽度不能小于 300px，高度不能小于600px
     * 3：订单码-迷你前置模式，对应 iframe 宽度不能小于 75px，高度不能小于75px
     * 4：订单码-可定义宽度的嵌入式二维码，商户可根据需要设定二维码的大小
     * 2：订单码-跳转模式
     */
    private String qrPayMode;

    /**
     * 回调地址
     */
    private String notifyUrl;

    /**
     * 商户自定义二维码宽度，可选
     * number(4)，注：qr_pay_mode=4时该参数有效
     */
    private Integer qrcodeWidth;

    /**
     * 订单包含的商品列表信息，可选
     * GoodsDetail[]，json格式
     */
    private String goodsDetail;

    /**
     * 订单绝对超时时间，可选
     * string(32)，格式为yyyy-MM-dd HH:mm:ss。超时时间范围：1m~15d
     * 注：time_expire和timeout_express两者只需传入一个或者都不传，两者均传入时，优先使用time_expire
     */
    private String timeExpire;

    /**
     * 二级商户信息，可选
     * SubMerchant，直付通模式和机构间连模式下必传，其它场景下不需要传入
     */
    private String subMerchant;

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
     * 优惠参数，可选
     * string(512)，为 JSON 格式。注：仅与支付宝协商后可用
     */
    private String promoParams;

    /**
     * 请求后页面的集成方式，可选
     * string(16)
     * 枚举值：
     * ALIAPP：支付宝钱包内
     * PCWEB：PC端访问
     * 默认值为PCWEB
     */
    private String integrationType;

    /**
     * 请求来源地址，可选
     * string(256)，如果使用ALIAPP的集成方式，用户中途取消支付会返回该地址
     */
    private String requestFromUrl;

    /**
     * 商户门店编号，可选
     * string(32)，指商户创建门店时输入的门店编号
     */
    private String storeId;

    /**
     * 商户原始订单号，可选
     * string(32)，最大长度限制 32 位
     */
    private String merchantOrderNo;

    /**
     * 外部指定买家，可选
     * ExtUserInfo对象
     */
    private String extUserInfo;

    /**
     * 开票信息，可选
     * InvoiceInfo对象
     */
    private String invoiceInfo;
}

