package com.af.framework.annotation;

import java.lang.annotation.*;

/**
 * @service注解
 */

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AfService {
    String value() default "";
}
