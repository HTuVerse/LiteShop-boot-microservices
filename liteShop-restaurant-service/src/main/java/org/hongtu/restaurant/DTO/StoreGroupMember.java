package org.hongtu.restaurant.DTO;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 门店分组成员关系。
 */
@Data
@Accessors(chain = true)
@TableName("store_group_member")
public class StoreGroupMember implements Serializable {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long groupId;
    private Long storeId;
    private LocalDateTime createdAt;
}
