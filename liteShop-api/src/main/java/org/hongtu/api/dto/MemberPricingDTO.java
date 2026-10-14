package org.hongtu.api.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/** 会员定价仅需的最小字段集合，避免反序列化完整用户对象。 */
@Data
@Accessors(chain = true)
public class MemberPricingDTO implements Serializable {
    private Long id;
    private Integer level;
}
