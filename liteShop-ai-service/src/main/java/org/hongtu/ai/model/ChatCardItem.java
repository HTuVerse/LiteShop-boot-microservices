package org.hongtu.ai.model;

import java.math.BigDecimal;

/** 智能问数卡片中的单条排行或原因。 */
public record ChatCardItem(String name, String value, String unit, BigDecimal percent,
                           String icon, String title, String detail) {
}
