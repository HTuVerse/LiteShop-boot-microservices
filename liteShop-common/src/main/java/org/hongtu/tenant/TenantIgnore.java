package org.hongtu.tenant;

import com.baomidou.mybatisplus.core.plugins.IgnoreStrategy;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;

import java.util.function.Supplier;

/** Tenant filtering bypass with nesting support. */
public final class TenantIgnore {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    private TenantIgnore() {
    }

    public static void open() {
        int depth = DEPTH.get();
        if (depth == 0) {
            InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        }
        DEPTH.set(depth + 1);
    }

    public static void close() {
        int depth = DEPTH.get();
        if (depth <= 1) {
            DEPTH.remove();
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        } else {
            DEPTH.set(depth - 1);
        }
    }

    /** Temporarily removes this thread's ignore state and returns its nesting depth. */
    public static int suspend() {
        int depth = DEPTH.get();
        DEPTH.remove();
        if (depth > 0) {
            InterceptorIgnoreHelper.clearIgnoreStrategy();
        }
        return depth;
    }

    /** Restores a nesting depth previously returned by {@link #suspend()}. */
    public static void restore(int depth) {
        if (depth <= 0) {
            return;
        }
        InterceptorIgnoreHelper.handle(IgnoreStrategy.builder().tenantLine(true).build());
        DEPTH.set(depth);
    }

    public static void run(Runnable task) {
        open();
        try {
            task.run();
        } finally {
            close();
        }
    }

    public static <T> T get(Supplier<T> supplier) {
        open();
        try {
            return supplier.get();
        } finally {
            close();
        }
    }
}