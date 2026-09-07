package org.hongtu.restaurant.enums;

/** 餐桌预约状态常量。 */
public final class TableReservationStatus {
    public static final int PENDING = 0;
    public static final int ARRIVED = 1;
    public static final int COMPLETED = 2;
    public static final int CANCELLED = 3;
    public static final int NO_SHOW = 4;

    private TableReservationStatus() {}

    public static String description(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case PENDING -> "待到店";
            case ARRIVED -> "已到店";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
            case NO_SHOW -> "已爽约";
            default -> "未知";
        };
    }
}
