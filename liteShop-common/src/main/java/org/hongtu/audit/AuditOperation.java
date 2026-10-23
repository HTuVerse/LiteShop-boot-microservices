package org.hongtu.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 标记需要自动记录的后台操作入口。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditOperation {
    String module();
    String type();
    String objectType() default "";
    String summary() default "";
    int objectIdArgument() default -1;
}
