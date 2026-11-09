package com.faber.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Quartz Job
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value={ElementType.FIELD,ElementType.TYPE})
public @interface FaJob {

    String value() default "";

    /** 任务目录分组；空值在页面归入未分组。 */
    String group() default "";

    /** 任务能力说明，不替代具体调度配置的描述。 */
    String description() default "";

    String[] tags() default {};

    /** 分组内排序，越小越靠前。 */
    int sort() default 0;

}
