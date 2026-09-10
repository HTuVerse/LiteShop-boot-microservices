package org.hongtu.saas.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 轻量只读配额门禁。修改资源的接口仍必须在 Service 中调用 consume/release，才能保证并发原子性和失败补偿。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireQuota {
    String value();
    String amount() default "1";
    String permission() default "";
}
