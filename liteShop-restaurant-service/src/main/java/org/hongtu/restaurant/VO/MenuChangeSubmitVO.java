package org.hongtu.restaurant.VO;

import lombok.Data;
import lombok.experimental.Accessors;
import org.hongtu.restaurant.DTO.MenuChangeRecord;

/**
 * 菜单变更提交结果。
 */
@Data
@Accessors(chain = true)
public class MenuChangeSubmitVO {
    private MenuChangeRecord record;
    private DishVO effectiveDish;
}
