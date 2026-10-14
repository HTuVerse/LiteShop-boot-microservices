package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.VerifyStatusEnum;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 核销记录
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("verify_record")
public class VerifyRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 核销ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户优惠券ID
     */
    private Long userCouponId;

    /**
     * 订单核销来源；人工扫码核销时为空。
     */
    private Long orderId;

    /**
     * 核销管理员ID
     */
    private Long merchantId;

    /**
     * 核销码
     */
    private String verifyCode;

    /**
     * 核销时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime verifyTime;

    /**
     * 状态(0:未核销,1:已核销)
     */
    private VerifyStatusEnum status;


}
