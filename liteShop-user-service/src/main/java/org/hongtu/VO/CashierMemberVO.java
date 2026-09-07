package org.hongtu.VO;

import lombok.Data;

import java.math.BigDecimal;

/** 收银台按手机号查询所需的最小会员信息。 */
@Data
public class CashierMemberVO {
    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String avatar;
    private Integer level;
    private Integer points;
    private BigDecimal surplusMoney;
}
