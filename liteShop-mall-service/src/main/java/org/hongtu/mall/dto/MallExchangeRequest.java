package org.hongtu.mall.dto;

import lombok.Data;

/** 客户端兑换请求，requestKey 由客户端生成以防重复点击。 */
@Data
public class MallExchangeRequest {
    private Long productId;
    private String requestKey;
}
