package org.hongtu.utils;

import cn.hutool.core.util.RandomUtil;

import static org.hongtu.constant.CommonConst.*;

/**
 * 验证码生成工具类
 * 支持纯数字和字母数字混合验证码
 *
 * @author system
 * @since 2025-02-24
 */
public class CodeGeneratorUtil {

    /**
     * 生成验证码（根据配置自动选择类型）
     *
     * @return 验证码
     */
    public static String generateCode() {
        if ("ALPHANUMERIC".equalsIgnoreCase(CODE_TYPE)) {
            return generateAlphanumericCode(CODE_LENGTH);
        }
        return generateNumericCode(CODE_LENGTH);
    }

    /**
     * 生成指定长度的数字验证码
     *
     * @param length 验证码长度
     * @return 数字验证码
     */
    public static String generateNumericCode(int length) {
        if (length <= 0 || length > 10) {
            throw new IllegalArgumentException("验证码长度必须在1-10之间");
        }
        return RandomUtil.randomNumbers(length);
    }

    /**
     * 生成指定长度的字母数字混合验证码
     * 不包含易混淆字符: 0/O, 1/I/l
     *
     * @param length 验证码长度
     * @return 字母数字混合验证码
     */
    public static String generateAlphanumericCode(int length) {
        if (length <= 0 || length > 20) {
            throw new IllegalArgumentException("验证码长度必须在1-20之间");
        }
        // 排除易混淆字符
        String chars = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
        return RandomUtil.randomString(chars, length);
    }
}
