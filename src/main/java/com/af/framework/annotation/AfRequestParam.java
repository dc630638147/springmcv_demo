package com.af.framework.annotation;

import java.lang.annotation.*;

/**
 * @RequestParam
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AfRequestParam {
    String value() default "";
}
