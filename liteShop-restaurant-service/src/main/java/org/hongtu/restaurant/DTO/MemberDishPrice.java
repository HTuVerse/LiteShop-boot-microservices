package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 长期会员等级菜品价；临时会员价使用定价活动。 */
@Data
@Accessors(chain = true)
@TableName("member_dish_price")
public class MemberDishPrice implements Serializable {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    /** 为空表示租户全部门店。 */
    private Long storeId;
    private Long dishId;
    private Integer memberLevelCode;
    private BigDecimal memberPrice;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;
    private Integer priority;
    private Integer status;
    private Integer deleted;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
