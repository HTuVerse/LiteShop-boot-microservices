package org.hongtu.restaurant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.hongtu.restaurant.DTO.*;
import com.baomidou.mybatisplus.spring.service.IService;
import org.hongtu.restaurant.VO.PaymentStatusVO;
import org.hongtu.restaurant.VO.RefundDishVO;
import org.hongtu.restaurant.VO.RestaurantOrderVO;
import org.hongtu.restaurant.enums.OrderStatusEnum;
import org.hongtu.restaurant.enums.PaymentTypeEnum;
import org.hongtu.restaurant.enums.SceneTypeEnum;

import org.hongtu.Result.Result;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 餐厅订单表 服务类
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
public interface RestaurantOrderService extends IService<RestaurantOrder> {


    /**
     * 创建订单
     * @param request 创建订单请求
     * @return 创建的订单（包含订单明细）
     */
    RestaurantOrderVO createOrder(CreateOrderRequest request);

    /**
     * 收银员代会员下单。会员身份由服务端参数决定，不读取普通顾客登录态。
     */
    RestaurantOrderVO createProxyOrder(CreateOrderRequest request, Long memberUserId, Long operatorId);

    /** 根据租户内幂等键返回已创建订单。 */
    RestaurantOrderVO getByRequestKey(String requestKey);

    /**
     * 更新订单状态
     * @param orderId 订单ID
     * @param status 订单状态
     * @return 是否成功
     */
    boolean updateOrderStatus(Long orderId, OrderStatusEnum status);

    /**
     * 取消订单
     * @param orderId 订单ID
     * @param cancelReason 取消原因
     * @return 是否成功
     */
    boolean cancelOrder(Long orderId, String cancelReason);

    /**
     * 创建支付订单
     * @param orderId 订单ID
     * @param paymentType 支付方式
     * @param openid 用户openid（微信支付必填）
     * @return 支付响应
     */
    PaymentResponse createPayment(Long orderId, Integer paymentType, String paymentScene,
                                  String openid, String authCode, String returnUrl, String payerClientIp);

    /**
     * 查询支付状态
     * @param orderId 订单ID
     * @return 支付状态信息
     */
    PaymentStatusVO getPaymentStatus(Long orderId);

    /**
     * 主动查询支付结果并更新订单状态
     * @param orderId 订单ID
     * @return 是否已支付
     */
    boolean queryAndUpdatePaymentStatus(Long orderId);

    /**
     * 根据用户ID分页查询订单列表（包含订单明细）
     * @param userId 用户ID
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @return 分页订单列表（包含订单明细）
     */
    Page<RestaurantOrderVO> getOrdersByUserId(Long userId, String sessionNo, Integer SceneType, Integer currentPage, Integer pageSize);

    /**
     * 分页查询订单列表（包含订单明细）
     * @param currentPage 当前页
     * @param pageSize 每页大小
     * @param storeId 门店ID
     * @param tableId 餐桌ID
     * @param userId 用户ID
     * @param orderType 订单类型
     * @param status 订单状态
     * @param paymentStatus 支付状态
     * @param phoneNumber 手机号
     * @return 分页订单列表（包含订单明细）
     */
    Page<RestaurantOrderVO> getOrderPage(Integer currentPage, Integer pageSize,
                                         Long storeId, Long tableId, Long userId,
                                         Integer orderType, Integer status,
                                         Integer paymentStatus, String phoneNumber);


    /**
     * 余额支付
     * @param orderId 订单ID
     * @return 支付是否成功
     */
    boolean payByBalance(Long orderId);

    RestaurantOrderVO getOrderDetail(Long orderId);


    // ================== 退菜功能 ==================

    /**
     * 退菜（管理员操作）
     *
     * 功能说明：
     * 1. 支持部分退菜或全部退菜
     * 2. 自动计算退款金额
     * 3. 更新订单总金额
     * 4. 记录退菜日志
     * 5. 如果订单已支付，需要发起退款流程
     * 6. 如果订单未支付，直接减少订单金额
     *
     * 业务规则：
     * - 只能退已下单的菜品
     * - 退菜数量不能超过订单项数量
     * - 已完成的订单不允许退菜
     * - 已取消的订单不允许退菜
     * - 退菜后如果订单金额为0，自动取消订单
     *
     * @param request 退菜请求
     * @return 退菜结果（包含退款金额、更新后的订单信息）
     */
    RefundDishVO refundDish(RefundDishRequest request);
    /**
     * 根据会话号查询订单
     * 用于检查会话是否已有订单
     *
     * @param sessionNo 会话编号
     * @return 订单信息，如果不存在则返回null
     */
    RestaurantOrder getOrderBySessionNo(String sessionNo);

    /**
     * 标记订单项为赠菜/取消赠菜（桌位模式）
     * 单价不变，优惠金额=该行小计（或取消赠菜时优惠=0）。
     *
     * @param request 标记赠菜请求
     * @return 更新后的订单信息
     */
    RestaurantOrderVO markOrderItemsGift(MarkOrderItemsGiftRequest request);

    /**
     * 追加菜品到现有订单（加菜功能）
     * 会话期间，用户可以多次追加菜品到同一个订单中
     * 每次追加会生成新的批次号，便于厨房分批制作
     *
     * @param request 追加菜品请求
     * @return 更新后的订单信息（包含所有订单明细）
     */
    RestaurantOrderVO addItemsToOrder(AddItemsRequest request);


    /**
     * 处理支付成功回调
     * 1. 根据商户订单号更新订单支付状态
     * 2. 自动打印订单（如果启用了飞鹅打印机）
     *
     * @param outTradeNo 商户订单号
     * @param transactionId 支付平台交易号（微信支付单号或支付宝交易号）
     * @param  paymentType  支付方式
     * @return 是否处理成功
     */
    boolean handlePaymentSuccess(String outTradeNo, String transactionId,PaymentTypeEnum paymentType);

    /**
     * 处理支付超时
     * 当用户在规定时间内未完成支付，关闭订单
     *
     * @param orderId 订单ID
     * @param reason 超时原因
     * @return 是否处理成功
     */
    boolean handlePaymentTimeout(Long orderId, String reason);

    /**
     * 处理支付失败
     * 当支付过程中发生错误时调用
     *
     * @param orderId 订单ID
     * @param reason 失败原因
     * @return 是否处理成功
     */
    boolean handlePaymentError(Long orderId, String reason);

    /** 支付服务已验签后，由订单服务核对并入账。 */
    void applyVerifiedPaymentNotify(Long tenantId, org.hongtu.DTO.PaymentNotifyResult notice);

    /** 支付服务已验签后，由订单服务核对退款单并入账。 */
    void applyVerifiedRefundNotify(Long tenantId, org.hongtu.api.dto.PaymentRefundNotifyResult notice);

    /**
     * 补偿仍处于已申请或处理中的退款单。
     */
    void reconcileOpenRefunds();


    // ================== 收银台专用方法 ==================

    /**
     * 收银台-扫码支付下单（预留方法）
     *
     * @param request 收银台下单请求
     * @return 订单信息（包含订单明细）
     */
    RestaurantOrderVO cashierScanPay(CashierOrderRequest request);

    /**
     * 收银台-直接支付（统一入口，含余额支付）
     * 传 userId 时按余额支付（扣会员余额）；否则按现金/线下等直接下单。
     * 仅用于无桌位、散客一笔过的直接收银；桌位收银请先提交订单/加菜，再调用 cashierSettleSession。
     *
     * @param request 收银台下单请求
     * @return 订单信息（包含订单明细）
     */
    RestaurantOrderVO cashierDirectOrder(CashierOrderRequest request);

    /**
     * 收银台-桌位结算，支持前台微信/支付宝付款码及现金、余额等方式收款。
     * 对会话当前订单收款后结束会话、释放桌位。用于桌位收银流程：先提交订单/加菜，再在结算时调用本接口。
     *
     * @param request 桌位结算请求（sessionNo、支付方式、实收金额等）
     * @return 订单信息（包含订单明细）
     */
    RestaurantOrderVO cashierSettleSession(CashierSettleSessionRequest request);

    // ================== 优惠券计算方法 ==================

    /**
     * 计算优惠券的优惠金额（支持单张或多张）
     * 后端自动校验：有效性、是否属于用户、叠加规则、互斥性
     * 需要查询菜品实时价格计算免单金额
     *
     * @param userId 用户ID
     * @param userCouponIds 用户优惠券ID列表
     * @param orderTotalAmount 订单总金额（用于计算整单折扣）
     * @return 计算结果：usable、totalDiscountAmount、couponDetails（各券明细）
     */
    Result calcCouponDiscount(Long userId, List<Long> userCouponIds, BigDecimal orderTotalAmount, Long storeId);

    /** 按购物车实购菜品和服务端价格试算优惠券。 */
    Result calcCouponDiscountForCart(Long userId, CouponQuoteRequest request);

    Result cashierCouponQuote(CashierCouponQuoteRequest request);
}
