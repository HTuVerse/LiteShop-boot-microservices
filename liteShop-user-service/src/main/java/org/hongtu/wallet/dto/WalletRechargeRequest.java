package org.hongtu.wallet.dto;

import lombok.Data;

/** 会员创建储值订单时提交的档位与幂等键。 */
@Data
public class WalletRechargeRequest {
    private Long planId;
    private String requestKey;
}
