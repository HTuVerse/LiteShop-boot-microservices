package org.hongtu.restaurant.DTO;

import lombok.Data;
import java.util.List;

/** 收银员代会员试算优惠券；桌位订单使用服务端已提交的明细。 */
@Data
public class CashierCouponQuoteRequest {
    private Long userId;
    private Long storeId;
    private String sessionNo;
    private List<Long> userCouponIds;
    private List<CashierOrderRequest.CartItem> cartItems;
}
