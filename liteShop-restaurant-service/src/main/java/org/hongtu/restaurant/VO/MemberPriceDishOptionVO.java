package org.hongtu.restaurant.VO;

import lombok.Data;
import lombok.experimental.Accessors;

/** 会员菜品价下拉框使用的轻量菜品选项。 */
@Data
@Accessors(chain = true)
public class MemberPriceDishOptionVO {
    private Long id;
    private String dishName;
    private Integer sourceType;
    private Boolean available;
    private String unavailableReason;
}
