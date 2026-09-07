package org.hongtu.restaurant.DTO;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.enums.SessionStatusEnum;

/**
 * <p>
 * 餐桌会话表
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("table_session")
public class TableSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 会话编号
     */
    private String sessionNo;

    /**
     * 门店ID
     */
    private Long storeId;

    /**
     * 餐桌ID
     */
    private Long tableId;

    /**
     * 就餐人数
     */
    private Integer peopleCount;

    /**
     * 当前在线人数（WebSocket连接数）
     */
    private Integer currentUserCount;

    /**
     * 当前订单ID（会话期间的主订单）
     */
    private Long currentOrderId;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    /**
     * 总订单数
     */
    private Integer totalOrders;

    /**
     * 备注
     */
    private String remark;

    /**
     * 总消费金额
     */
    private BigDecimal totalAmount;

    /**
     * 实付金额
     */
    private BigDecimal paidAmount;

    /**
     * 状态：1-进行中，2-已结束
     */
    private SessionStatusEnum status;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;


}
