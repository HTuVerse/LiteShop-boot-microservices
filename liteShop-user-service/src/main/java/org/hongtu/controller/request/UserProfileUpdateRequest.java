package org.hongtu.controller.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import org.hongtu.enums.GenderEnum;

import java.sql.Date;

/** 用户仅能提交个人资料字段，资金和认证状态不属于资料更新请求。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileUpdateRequest {
    private Long id;
    private String username;
    private String nickname;
    private GenderEnum gender;
    private String introduction;
    private String region;
    private Date birthday;
    private String avatar;
    private Long avatarResourceId;
}
