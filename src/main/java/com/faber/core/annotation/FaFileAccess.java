package com.faber.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 按文件存储类型决定是否要求登录。 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FaFileAccess {
    String AUTH_REQUIRED = FaFileAccess.class.getName() + ".authRequired";

    boolean allowPublicResource() default false;
}
