package org.hongtu.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/** 内部展示与券核销只需这些字段，不传输用户实体和财务信息。 */
@Data
@Accessors(chain = true)
public class UserContactDTO {
    private Long id;
    private String username;
    private String nickname;
    private String phone;
}
