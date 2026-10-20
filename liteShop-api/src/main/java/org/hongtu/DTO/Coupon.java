package org.hongtu.DTO;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.CouponTypeEnum;
import org.hongtu.enums.OnOrOffShelfEnum;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * <p>
 * 优惠券
 * </p>
 *
 * @author Honghong
 * @since 2025-06-29
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("coupon")
public class Coupon implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 优惠券ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 优惠券名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 优惠券类型
     * 0:满减券, 1:整单折扣券, 2:菜品免单券, 3:菜品折扣券, 4:代金券
     */
    private CouponTypeEnum type;

    /**
     * 面值/折扣率（根据类型含义不同）
     * - 满减券: 减免金额（如20=减20元）
     * - 整单折扣券: 折扣率（如0.85=85折）
     * - 菜品免单券: 不使用（可为0）
     * - 菜品折扣券: 折扣率（如0.85=85折），每单限一份
     * - 代金券: 固定抵扣金额（元）
     */
    private BigDecimal value;

    /**
     * 最低消费金额，按优惠前整单金额判断。
     */
    private BigDecimal minAmount;

    /** 适用门店ID，逗号分隔；为空表示全部门店。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String storeIds;

    /** 可用星期，1=周一至7=周日，逗号分隔；为空表示每天。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String useWeekdays;

    /** 不可用的具体公历日期，yyyy-MM-dd，逗号分隔。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unavailableDates;

    /** 每年不可用的节日编码，逗号分隔；按节日当天匹配。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String unavailableHolidays;

    /** 每日可用时段起点；与结束时间同时为空表示全天。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalTime useStartTime;

    /** 每日可用时段终点。 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalTime useEndTime;

    /**
     * 发放总数
     */
    private Integer totalCount;

    /**
     * 剩余数量
     */
    private Integer remainCount;

    /**
     * 有效期开始
     */
    @TableField("start_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    /**
     * 有效期结束
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime endTime;

    /**
     * 状态(0:下架,1:上架)
     */
    private OnOrOffShelfEnum status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    /**
     * 是否可叠加使用
     * 0-不可叠加（只能单独使用），1-可叠加（可与其他券一起使用）
     * 默认为1（可叠加）
     */
    private BooleanEnum stackable;

    /** 同组券互斥；为空表示只按明确的配对关系判断。 */
    private String exclusiveGroup;

    /** 明确允许一起使用的其他券模板 ID；配对关系单独存表。 */
    @TableField(exist = false)
    private List<Long> stackWithCouponIds;

    /**
     * 关联的菜品列表（非持久化字段）
     * 用于菜品免单券的创建和查询
     */
    @TableField(exist = false)
    private List<CouponDishItem> dishItems;

}
