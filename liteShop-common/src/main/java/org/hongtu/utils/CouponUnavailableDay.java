package org.hongtu.utils;

import cn.hutool.core.date.ChineseDate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 优惠券不可用日期匹配；节日只指当天，不包含调休和连续假期。 */
public final class CouponUnavailableDay {
    public static final Set<String> HOLIDAYS = Set.of("NEW_YEAR", "SPRING_FESTIVAL", "LABOR_DAY",
            "DRAGON_BOAT", "QIXI", "MID_AUTUMN", "NATIONAL_DAY");

    private CouponUnavailableDay() {
    }

    /** 匹配指定的公历日期或逐年换算的农历节日。 */
    public static boolean matches(String dates, String holidays, LocalDate day) {
        if (day == null) return false;
        if (split(dates).contains(day.toString())) return true;
        Set<String> selected = split(holidays);
        if (selected.isEmpty()) return false;
        if (selected.contains("NEW_YEAR") && day.getMonthValue() == 1 && day.getDayOfMonth() == 1) return true;
        if (selected.contains("LABOR_DAY") && day.getMonthValue() == 5 && day.getDayOfMonth() == 1) return true;
        if (selected.contains("NATIONAL_DAY") && day.getMonthValue() == 10 && day.getDayOfMonth() == 1) return true;
        if (day.getYear() > 2099 || day.getYear() < 1900) return false;

        ChineseDate lunar = new ChineseDate(day);
        // 闰月不重复计算节日；月份名称不受前面是否存在闰月影响。
        if (lunar.isLeapMonth()) return false;
        String month = lunar.getChineseMonthName();
        int lunarDay = lunar.getDay();
        return (selected.contains("SPRING_FESTIVAL") && "正月".equals(month) && lunarDay == 1)
                || (selected.contains("DRAGON_BOAT") && "五月".equals(month) && lunarDay == 5)
                || (selected.contains("QIXI") && "七月".equals(month) && lunarDay == 7)
                || (selected.contains("MID_AUTUMN") && "八月".equals(month) && lunarDay == 15);
    }

    public static Set<String> split(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty())
                .collect(Collectors.toSet());
    }
}
