package org.hongtu.constant;

/**
 * 功能：
 * 作者：ht
 * 时间：2025/7/11 上午2:08
 */
public class CommonConst {

    // 邮箱正则
    public static final String EMAIL_REGEX =
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
    // 手机号正则
    public static final String PHONE_REGEX = "^1[3-9]\\d{9}$";

    //匹配中文正则
    public static final String CHINESE_REGEX = ".*[\u4e00-\u9fa5]+.*";

    /**
     * 登录/注册验证码在Redis中的前缀（统一使用，自动判断是登录还是注册）
     */
    public static final String SMS_LOGIN_CODE_PREFIX = "sms:loginCode:";

    /**
     * 密码重置验证码在Redis中的前缀
     */
    public static final String SMS_RESET_CODE_PREFIX = "sms:resetCode:";

    /**
     * 验证码最大重试次数
     * */
    public static final int MAX_RETRY_LIMIT = 5;

    /**
     * 发送验证码最大次数在Redis中的前缀
     */
    public static final String SEND_COUNT_CODE_PREFIX = "sms:sendCount:";

    /**
     * 发送验证码重置时间（小时）
     * */
    public static final int SEND_RESET_TIME = 1;

    /**
     * 验证码发送最大次数
     * */
    public static final int MAX_SEND_LIMIT = 5;

    /**
     * 密码重置验证码在Redis中的前缀
     */
    public static final String RETRY_KEY_PREFIX = "resetRetry:";

    /**
     * 手机验证码有效期（分钟）
     */
    public static final Integer COMMON_PHONE_CODE_EXPIRE_MINUTES = 5;

    /**
     * 邮箱验证码有效期（分钟）
     */
    public static final Integer COMMON_EMAIL_CODE_EXPIRE_MINUTES = 5;

    /**
     * 验证码发送间隔（秒）
     */
    public static final Integer SEND_INTERVAL_SECONDS = 60;

    /**
     * 验证码发送间隔在Redis中的前缀
     */
    public static final String SEND_INTERVAL_PREFIX = "sms:sendInterval:";

    /**
     * IP限流在Redis中的前缀
     */
    public static final String IP_LIMIT_PREFIX = "sms:ipLimit:";

    /**
     * IP限流时间窗口（小时）
     */
    public static final int IP_LIMIT_WINDOW_HOURS = 1;

    /**
     * IP限流最大次数（每小时）
     */
    public static final int IP_MAX_SEND_LIMIT = 10;

    /**
     * 验证码类型: NUMERIC-纯数字, ALPHANUMERIC-字母数字混合
     */
    public static final String CODE_TYPE = "NUMERIC";

    /**
     * 验证码长度
     */
    public static final int CODE_LENGTH = 6;

    /** 允许查询的余额明细最大时间范围：1年 */
    public static final int MAX_QUERY_YEARS = 1;


}
