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

/**
 * <p>
 * 订单商品表
 * </p>
 *
 * @author Ht
 * @since 2025-09-19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("restaurant_order_item")
public class RestaurantOrderItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 订单ID
     */
    private Long orderId;

    /**
     * 菜品ID
     */
    private Long dishId;

    /**
     * 分类id
     */
    private Long categoryId;

    /** 门店正常售价快照（含规格加价）。 */
    private BigDecimal unitListPrice;

    /** 菜品级活动和会员价计算后的成交单价。 */
    private BigDecimal unitSellingPrice;

    /** 下单时门店实际单位成本快照；为空表示当时未维护成本。 */
    private BigDecimal unitCostSnapshot;

    /**
     * 数量
     */
    private Integer quantity;

    /** 当前订单项全部优惠金额。 */
    private BigDecimal discountAmount;

    /** 当前订单项最终应付金额。 */
    private BigDecimal linePayableAmount;

    /** 当前剩余数量对应的成交成本总额。 */
    private BigDecimal lineCostAmount;

    /**
     * 是否赠送（0-否，1-是）
     */
    private Integer isGift;

    /**
     * 选择的规格
     */
    private String specOptionsText;

    /**
     * 备注（口味要求等）
     */
    private String note;


    /**
     * 批次号（第几次下单，1表示首次，2表示第二次加菜）
     */
    private Integer batchNo;

    /** 本批菜品确认提交时的用户快照，与加入购物车的人无关。 */
    private Long submittedByUserId;
    private String submittedByName;

    /**
     * 加菜时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime addTime;

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
