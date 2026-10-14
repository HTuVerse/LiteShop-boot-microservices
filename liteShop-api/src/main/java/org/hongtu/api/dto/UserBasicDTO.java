package org.hongtu.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/** 微服务间展示用户身份使用的最小字段集合。 */
@Data
@Accessors(chain = true)
public class UserBasicDTO implements Serializable {
    private Long id;
    private String username;
    private String nickname;
}
