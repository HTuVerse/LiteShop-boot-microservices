package org.hongtu.exception;

/** 内部依赖不可用；对外只返回固定文案，根因保留在日志中。 */
public class RemoteDependencyException extends RuntimeException {
    public RemoteDependencyException(String message) {
        super(message);
    }

    public RemoteDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
