package org.hongtu.utils;

import org.hongtu.constant.CommonConst;

import java.util.regex.Pattern;

import static org.hongtu.constant.CommonConst.*;


/**
 * 功能：
 * 作者：
 * 时间：2025/7/11 上午2:16
 */
public class hongTuUtil {
    // 预编译正则对象（提高性能）
    private static final Pattern EMAIL_PATTERN = Pattern.compile(EMAIL_REGEX);
    private static final Pattern PHONE_PATTERN = Pattern.compile(PHONE_REGEX);
    private static final Pattern CHINESE_PATTERN = Pattern.compile(CHINESE_REGEX);


    /**
     * 验证是否为邮箱
     */
    public static boolean isEmail(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return EMAIL_PATTERN.matcher(input).matches();
    }

    /**
     * 验证是否为手机号
     */
    public static boolean isPhone(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return PHONE_PATTERN.matcher(input).matches();
    }

    /**
     * 检查字符串是否包含中文字符
     */
    public static boolean containsChinese(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return CHINESE_PATTERN.matcher(input).matches();
    }

    /**
     * 判断字符串类型
     */
    public static String checkInputType(String input) {
        if (isEmail(input)) {
            return "email";
        } else if (isPhone(input)) {
            return "phone";
        } else {
            return "无效格式";
        }
    }



}
