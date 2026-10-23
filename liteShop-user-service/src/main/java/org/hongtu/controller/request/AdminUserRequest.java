package org.hongtu.controller.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.hongtu.enums.GenderEnum;
import org.hongtu.enums.StatusEnum;

import java.sql.Date;
import java.util.List;

/** 管理端用户写入白名单；不接收余额、积分、经验值或认证密钥。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AdminUserRequest {
    private Long id;
    private Long tenantId;
    private String username;
    private String password;
    private String nickname;
    private GenderEnum gender;
    private String phone;
    private String email;
    private String introduction;
    private String region;
    private Date birthday;
    private StatusEnum status;
    private Integer level;
    private List<String> roleCode;
    private String avatar;
    private Long avatarResourceId;
}
