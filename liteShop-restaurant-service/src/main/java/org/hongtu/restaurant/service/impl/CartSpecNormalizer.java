package org.hongtu.restaurant.service.impl;

import org.hongtu.enums.BooleanEnum;
import org.hongtu.enums.StatusEnum;
import org.hongtu.exception.BusinessException;
import org.hongtu.restaurant.DTO.DishSpecOption;
import org.hongtu.restaurant.VO.DishVO;
import org.hongtu.restaurant.VO.SpecValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 按服务端菜品规格生成稳定文本和加价，禁止使用客户端传来的价格。 */
final class CartSpecNormalizer {
    record Selection(String text, BigDecimal extraPrice) {}

    static Selection normalize(DishVO dish, String specText) {
        if (specText != null && specText.length() > 255) throw new BusinessException("规格内容过长");
        List<String> requested = specText == null || specText.isBlank() ? List.of()
                : Arrays.stream(specText.split(",", -1)).map(String::trim).toList();
        if (requested.size() > 30 || requested.stream().anyMatch(String::isBlank)
                || new HashSet<>(requested).size() != requested.size()) {
            throw new BusinessException("规格选择无效");
        }
        Set<String> remaining = new HashSet<>(requested);
        List<String> canonical = new ArrayList<>();
        BigDecimal extra = BigDecimal.ZERO;
        for (DishSpecOption group : dish.getSpecOptions() == null ? List.<DishSpecOption>of() : dish.getSpecOptions()) {
            if (group.getStatus() == StatusEnum.DISABLED) continue;
            int selected = 0;
            for (SpecValue value : group.getSpecValueList()) {
                if (value.getValue() != null && remaining.remove(value.getValue())) {
                    selected++;
                    canonical.add(value.getValue());
                    if (value.getExtraPrice() != null) extra = extra.add(value.getExtraPrice());
                }
            }
            if (group.getIsRequired() == BooleanEnum.YES && selected == 0) {
                throw new BusinessException("请选择必选规格：" + group.getSpecName());
            }
            if (group.getMoreSelect() != BooleanEnum.YES && selected > 1) {
                throw new BusinessException("规格只能选择一项：" + group.getSpecName());
            }
        }
        if (!remaining.isEmpty() || extra.signum() < 0) throw new BusinessException("规格选择无效");
        return new Selection(String.join(",", canonical), extra);
    }
}
