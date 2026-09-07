package org.hongtu.DTO;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.hongtu.enums.PointsOperationEnum;
import org.hongtu.enums.PointsSourceEnum;

/**
 * <p>
 * 用户积分记录DTO
 * </p>
 *
 * @author Ht
 * @since 2025-08-06
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user_points_record")
public class UserPointsRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 积分操作类型：1-增加，2-减少
     */
    private PointsOperationEnum operationType;

    /**
     * 积分来源：1-注册奖励，2-签到奖励，3-购物奖励，4-活动奖励，5-积分兑换
     */
    private PointsSourceEnum source;

    /**
     * 积分变动数量
     */
    private Integer points;

    /**
     * 操作前积分
     */
    private Integer beforePoints;

    /**
     * 操作后积分
     */
    private Integer afterPoints;

    /**
     * 备注说明
     */
    private String remark;

    /**
     * 操作时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime operationTime;


}
