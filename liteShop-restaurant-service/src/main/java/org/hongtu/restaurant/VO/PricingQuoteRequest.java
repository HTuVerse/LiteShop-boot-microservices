package org.hongtu.restaurant.VO;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** 服务端定价试算请求；客户端价格不作为计算依据。 */
@Data
public class PricingQuoteRequest {
    private Long storeId;
    private Long userId;
    /** 后台预览可直接指定等级；正式结算优先按 userId 从用户服务读取。 */
    private Integer memberLevelCode;
    private String channel;
    private List<Item> items;

    @Data
    public static class Item {
        private String lineKey;
        private Long dishId;
        private Integer quantity;
        private BigDecimal specExtraPrice;
    }
}
