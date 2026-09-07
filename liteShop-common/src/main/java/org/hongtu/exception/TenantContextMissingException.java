package org.hongtu.exception;

/** 访问租户业务表时没有可用租户上下文。 */
public class TenantContextMissingException extends RuntimeException {

    public TenantContextMissingException(String message) {
        super(message);
    }
}
