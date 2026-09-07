package org.hongtu.DTO;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;
import java.time.LocalDateTime;
@Data @Accessors(chain = true) @TableName("user_level_scheme")
public class UserLevelScheme {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long tenantId;
    private String name;
    private String description;
    private Long sourceTemplateId;
    private Integer isActive;
    private Integer isDefault;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
