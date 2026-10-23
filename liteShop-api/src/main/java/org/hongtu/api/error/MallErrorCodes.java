package org.hongtu.api.error;

/** 积分商城跨服务的确定性业务错误码；未知结果必须继续对账，不能直接回滚库存。 */
public final class MallErrorCodes {
    private MallErrorCodes() {}

    public static final int POINTS_USER_MISSING = 9301;
    public static final int POINTS_INSUFFICIENT = 9302;
    public static final int COUPON_USER_MISSING = 9311;
    public static final int COUPON_UNAVAILABLE = 9312;
    public static final int COUPON_TIME_INVALID = 9313;
}
