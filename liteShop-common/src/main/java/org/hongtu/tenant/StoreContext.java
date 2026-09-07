package org.hongtu.tenant;

import java.util.List;

/** 当前请求的门店数据范围。 */
public final class StoreContext {

    private static final ThreadLocal<Boolean> RESTRICTED = new ThreadLocal<>();
    private static final ThreadLocal<List<Long>> STORE_IDS = new ThreadLocal<>();

    private StoreContext() {
    }

    public static void setRestricted(boolean restricted, List<Long> storeIds) {
        RESTRICTED.set(restricted);
        STORE_IDS.set(storeIds == null ? List.of() : List.copyOf(storeIds));
    }

    public static boolean isRestricted() {
        return Boolean.TRUE.equals(RESTRICTED.get());
    }

    public static List<Long> getStoreIds() {
        List<Long> storeIds = STORE_IDS.get();
        return storeIds == null ? List.of() : storeIds;
    }

    public static void clear() {
        RESTRICTED.remove();
        STORE_IDS.remove();
    }
}
