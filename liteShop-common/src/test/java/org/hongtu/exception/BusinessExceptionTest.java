package org.hongtu.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 业务异常默认错误码测试。
 */
class BusinessExceptionTest {

    @Test
    void defaultBusinessFailureIsNotReportedAsAuthenticationFailure() {
        BusinessException error = new BusinessException("业务规则不允许该操作");

        assertEquals(400, error.getCode());
    }
}
