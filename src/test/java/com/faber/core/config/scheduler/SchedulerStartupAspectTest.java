package com.faber.core.config.scheduler;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SchedulerStartupAspectTest {

    @Test
    void shouldSkipScheduledWorkAndTransactionsUntilReady() {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(TestConfig.class, SchedulerStartupGate.class,
                    SchedulerStartupAspect.class, ScheduledWork.class);
            context.registerBean(PlatformTransactionManager.class, () -> transactions);
            context.refresh();

            ScheduledWork work = context.getBean(ScheduledWork.class);
            work.runScheduled();
            assertEquals(0, work.count());
            verifyNoInteractions(transactions);

            work.runManual();
            assertEquals(1, work.count());

            context.getBean(SchedulerStartupGate.class).onApplicationReady();
            work.runScheduled();
            assertEquals(2, work.count());
            verify(transactions).getTransaction(any(TransactionDefinition.class));
            verify(transactions).commit(any());
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    @EnableTransactionManagement
    static class TestConfig {
    }

    static class ScheduledWork {
        private int executions;

        @Scheduled(fixedDelay = 1000)
        @Transactional
        public void runScheduled() {
            executions++;
        }

        public void runManual() {
            executions++;
        }

        public int count() {
            return executions;
        }
    }
}
