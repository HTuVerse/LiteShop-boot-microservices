package org.hongtu.api.util;

import org.hongtu.Result.Result;
import org.hongtu.exception.BusinessException;
import org.hongtu.exception.RemoteDependencyException;

/**
 * Feign 调用结果工具
 * 内部接口统一返回 Result 包装，调用方通过本工具解包：
 * 失败（code != 200）时抛出 BusinessException，与原单体中被调 Service 直接抛异常的行为保持一致
 */
public class FeignResultUtil {

    private FeignResultUtil() {
    }

    /**
     * 解包 Result，失败抛 BusinessException
     *
     * @return Result.data（可能为 null）
     */
    public static <T> T unwrap(Result<T> result) {
        if (result == null) {
            throw new RemoteDependencyException("远程服务无响应");
        }
        if (!Result.CODE_SUCCESS.equals(result.getCode())) {
            if (result.getCode() == null || result.getCode() == 500 || result.getCode() == 503) {
                throw new RemoteDependencyException("远程服务处理失败");
            }
            throw new BusinessException(result.getCode(),
                    result.getMsg() == null ? "远程服务调用失败" : result.getMsg());
        }
        return result.getData();
    }

    /**
     * 仅校验成功与否（用于 Result<Void>）
     */
    public static void check(Result<?> result) {
        unwrap(result);
    }
}
