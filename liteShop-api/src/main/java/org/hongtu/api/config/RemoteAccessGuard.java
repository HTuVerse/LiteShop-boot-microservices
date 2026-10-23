package org.hongtu.api.config;

import org.hongtu.exception.BusinessException;
import org.hongtu.exception.RemoteDependencyException;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** 远程鉴权和租户检查共用隔离舱；故障连续发生时短暂熔断并保持拒绝访问。 */
public class RemoteAccessGuard {
    private final Semaphore permits = new Semaphore(16);
    private final AtomicInteger failures = new AtomicInteger();
    private final AtomicLong openUntil = new AtomicLong();

    public <T> T call(Supplier<T> action) {
        if (System.currentTimeMillis() < openUntil.get() || !permits.tryAcquire()) {
            throw new RemoteDependencyException("远程鉴权暂不可用");
        }
        try {
            T value = action.get();
            failures.set(0);
            return value;
        } catch (BusinessException rejected) {
            // 有明确业务错误码说明依赖已响应，不参与熔断计数。
            failures.set(0);
            throw rejected;
        } catch (RuntimeException failure) {
            if (failures.incrementAndGet() >= 5) {
                openUntil.set(System.currentTimeMillis() + 10_000);
                failures.set(0);
            }
            throw new RemoteDependencyException("远程鉴权调用失败", failure);
        } finally {
            permits.release();
        }
    }
}
