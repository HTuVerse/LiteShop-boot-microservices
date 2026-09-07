package org.hongtu.utils;

/**
 * 数据脱敏工具类
 * 用于对敏感信息进行脱敏处理,保护用户隐私
 *
 * @author system
 * @since 2025-02-24
 */
public class DesensitizationUtil {

    /**
     * 手机号脱敏
     * 显示前3位和后4位,中间用****替换
     * 例: 13812345678 -> 138****5678
     *
     * @param phone 手机号
     * @return 脱敏后的手机号
     */
    public static String desensitizePhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 邮箱脱敏
     * @ 前保留前3位,其余用***替换
     * 例: example@gmail.com -> exa***@gmail.com
     *
     * @param email 邮箱
     * @return 脱敏后的邮箱
     */
    public static String desensitizeEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf("@");
        String prefix = email.substring(0, atIndex);
        String suffix = email.substring(atIndex);
        
        if (prefix.length() <= 3) {
            return prefix.charAt(0) + "***" + suffix;
        }
        return prefix.substring(0, 3) + "***" + suffix;
    }

    /**
     * 身份证号脱敏
     * 保留前6位和后4位
     * 例: 110101199001011234 -> 110101********1234
     *
     * @param idCard 身份证号
     * @return 脱敏后的身份证号
     */
    public static String desensitizeIdCard(String idCard) {
        if (idCard == null || idCard.length() < 15) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 银行卡号脱敏
     * 保留前4位和后4位
     * 例: 6222021234567890123 -> 6222************0123
     *
     * @param bankCard 银行卡号
     * @return 脱敏后的银行卡号
     */
    public static String desensitizeBankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 12) {
            return bankCard;
        }
        return bankCard.substring(0, 4) + "************" + bankCard.substring(bankCard.length() - 4);
    }

    /**
     * 姓名脱敏
     * 保留姓,名字用*替换
     * 例: 张三 -> 张*, 欧阳娜娜 -> 欧***
     *
     * @param name 姓名
     * @return 脱敏后的姓名
     */
    public static String desensitizeName(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return name;
        }
        StringBuilder masked = new StringBuilder();
        masked.append(name.charAt(0));
        for (int i = 1; i < name.length(); i++) {
            masked.append("*");
        }
        return masked.toString();
    }

    /**
     * 账号通用脱敏(自动识别手机号或邮箱)
     *
     * @param account 账号(手机号或邮箱)
     * @return 脱敏后的账号
     */
    public static String desensitizeAccount(String account) {
        if (account == null) {
            return null;
        }
        // 判断是否是手机号
        if (account.matches("^1[3-9]\\d{9}$")) {
            return desensitizePhone(account);
        }
        // 判断是否是邮箱
        if (account.contains("@")) {
            return desensitizeEmail(account);
        }
        return account;
    }
}
