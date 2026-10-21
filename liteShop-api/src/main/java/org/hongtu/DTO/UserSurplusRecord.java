package org.hongtu.DTO;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.BalanceResourceEnum;

/**
 * <p>
 * 余额变动历史表
 * </p>
 *
 * @author Ht
 * @since 2025-08-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_surplus_record")
public class UserSurplusRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 充值余额
     */
    private BigDecimal amount;

    /** 本次消费或退款涉及的赠送余额，单位元。 */
    private BigDecimal giftAmount;

    /** 退款对应的原余额支付订单号。 */
    private String sourceOrderNo;

    /**
     * 变动后余额
     */
    private BigDecimal newBalance;

    /**
     * 1-用户充值 2-管理员充值 3-退款 4-消费
     */
    private BalanceResourceEnum type;

    /**
     * 变动时间
     */
    private LocalDateTime createdAt;

    /**
     * 变动前余额
     */
    private BigDecimal oldBalance;

    /**
     * 操作用户id
     * */
    private long operateUserId;


}
