package org.hongtu.saas.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 同时校验租户是否购买功能；permission 非空时再校验当前用户 RBAC 权限。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireFeature {
    String value();
    String permission() default "";
}
