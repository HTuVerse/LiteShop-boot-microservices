package org.hongtu.restaurant.VO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.hongtu.restaurant.DTO.RestaurantOrder;
import org.hongtu.restaurant.enums.SessionStatusEnum;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 餐桌会话视图对象（包含订单列表和桌位信息）
 *
 * @author Ht
 * @since 2025-11-05
 */
@Data
public class TableSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 会话ID
     */
    private Long id;

    /**
     * 会话编号
     */
    private String sessionNo;

    /**
     * 匿名桌台接口与 WebSocket 使用的短期签名票据，仅在扫码/开桌成功时返回。
     */
    private String sessionToken;

    /**
     * 就餐人数
     */
    private Integer peopleCount;

    /**
     * 当前在线人数
     */
    private Integer currentUserCount;

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
     * 当前订单ID（会话期间的主订单）
     */
    private Long currentOrderId;

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
     * 状态描述
     */
    private String statusDesc;

    // ========== 桌位信息 ==========

    /**
     * 桌位id
     */
    private Long tableId;

    /**
     * 桌位编号
     */
    private String tableNumber;

    /**
     * 桌位名称
     */
    private String tableName;

    /**
     * 门店ID
     */
    private Long storeId;
    /**
     * 门店名称
     */
    private String storeName;

    // ========== 订单列表 ==========

    /**
     * 该会话下的所有订单及订单明细
     */
    private List<RestaurantOrderVO> orders;

    /**
     * 用餐时长（分钟）
     */
    private Long durationMinutes;

}

