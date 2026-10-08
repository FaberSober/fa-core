package com.faber.core.config.scheduler;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 启动完成前跳过定时任务；检查先于事务执行。 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SchedulerStartupAspect {

    private final SchedulerStartupGate startupGate;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object checkReady(ProceedingJoinPoint point) throws Throwable {
        return startupGate.isReady() ? point.proceed() : null;
    }
}
