package com.faber.core.config.thread;

import com.faber.core.context.BaseContextHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TaskExecutionTrackerTest {
    private final List<TrackingThreadPoolTaskExecutor> executors = new ArrayList<>();
    private final AtomicReference<Throwable> uncaught = new AtomicReference<>();
    private final CountDownLatch uncaughtDone = new CountDownLatch(1);
    private final ThreadLocal<String> logContext = new ThreadLocal<>();

    @AfterEach
    void cleanup() {
        executors.forEach(TrackingThreadPoolTaskExecutor::shutdown);
        BaseContextHandler.remove();
        logContext.remove();
    }

    @Test
    void completesOnceWithOriginalContextAndWithoutReopeningTheFinishedRound() {
        BaseContextHandler.setTenantId("root");
        logContext.set("caller");
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> callbackContext = new AtomicReference<>();
        AtomicReference<TaskExecutionTracker> callbackTracker = new AtomicReference<>();
        TaskExecutionTracker tracker = new TaskExecutionTracker(() -> bindLog("execution"), result -> {
            callbackContext.set(BaseContextHandler.getTenantId() + ":" + logContext.get());
            callbackTracker.set(TaskExecutionTracker.current());
            calls.incrementAndGet();
        });

        BaseContextHandler.setTenantId("changed");
        tracker.completeMain(null);
        tracker.completeMain(new IllegalStateException("重复结束"));

        assertEquals(1, calls.get());
        assertEquals("root:execution", callbackContext.get());
        assertNull(callbackTracker.get());
        assertEquals("changed", BaseContextHandler.getTenantId());
        assertEquals("caller", logContext.get());
    }

    @Test
    void waitsForNestedSubmissionsAfterMainHasReturned() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(4);
        Completion completion = completion();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch nestedStarted = new CountDownLatch(1);
        CountDownLatch releaseNested = new CountDownLatch(1);
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            executor.execute(() -> {
                firstStarted.countDown();
                block(releaseFirst);
                executor.execute(() -> {
                    nestedStarted.countDown();
                    block(releaseNested);
                });
            });
        }
        await(firstStarted);
        completion.tracker.completeMain(null);
        assertNull(completion.result.get());
        releaseFirst.countDown();
        await(nestedStarted);
        assertNull(completion.result.get());
        releaseNested.countDown();
        await(completion.done);
        assertNull(completion.result.get().failure());
        assertFalse(completion.result.get().incompleteTracking());
        assertEquals(1, completion.calls.get());
    }

    @Test
    void preservesMainFailureAndWaitsForAlreadySubmittedWork() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(2);
        Completion completion = completion();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            executor.execute(() -> {
                started.countDown();
                block(release);
            });
        }
        await(started);
        AssertionError error = new AssertionError("主流程失败");
        completion.tracker.completeMain(error);
        assertNull(completion.result.get());
        release.countDown();
        await(completion.done);
        assertSame(error, completion.result.get().failure());
    }

    @Test
    void recordsChildFailureAndRetainsItsUncaughtException() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(2);
        Completion completion = completion();
        IllegalArgumentException error = new IllegalArgumentException("子任务失败");
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            executor.execute(() -> { throw error; });
        }
        completion.tracker.completeMain(null);
        await(completion.done);
        assertSame(error, completion.result.get().failure());
        await(uncaughtDone);
        assertSame(error, uncaught.get());
    }

    @Test
    void copiesSubmissionContextAndCleansReusedWorker() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(2);
        CountDownLatch blocking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            blocking.countDown();
            block(release);
        });
        await(blocking);
        BaseContextHandler.setTenantId("submitted");
        Completion completion = completion();
        AtomicReference<String> childContext = new AtomicReference<>();
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            executor.execute(() -> {
                childContext.set(BaseContextHandler.getTenantId() + ":" + logContext.get());
                BaseContextHandler.setTenantId("child-change");
            });
        }
        BaseContextHandler.setTenantId("parent-change");
        completion.tracker.completeMain(null);
        release.countDown();
        await(completion.done);

        assertEquals("submitted:execution", childContext.get());
        assertEquals("parent-change", BaseContextHandler.getTenantId());
        assertNull(executor.submit(BaseContextHandler::getHoldMap).get(3, TimeUnit.SECONDS));
        assertNull(executor.submit(TaskExecutionTracker::current).get(3, TimeUnit.SECONDS));
        assertNull(executor.submit(logContext::get).get(3, TimeUnit.SECONDS));
    }

    @Test
    void callerRunsRestoresParentContextAndPreservesBusinessException() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(0, new ThreadPoolExecutor.CallerRunsPolicy());
        CountDownLatch blocking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            blocking.countDown();
            block(release);
        });
        await(blocking);
        BaseContextHandler.setTenantId("parent");
        logContext.set("main-log");
        Completion completion = completion();
        IllegalStateException error = new IllegalStateException("调用线程失败");
        Thread parent = Thread.currentThread();
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            assertSame(error, assertThrows(IllegalStateException.class, () -> executor.execute(() -> {
                assertSame(parent, Thread.currentThread());
                BaseContextHandler.setTenantId("child");
                throw error;
            })));
            assertSame(completion.tracker, TaskExecutionTracker.current());
        }
        assertEquals("parent", BaseContextHandler.getTenantId());
        assertEquals("main-log", logContext.get());
        assertNull(completion.result.get());
        completion.tracker.completeMain(null);
        assertSame(error, completion.result.get().failure());
        release.countDown();
    }

    @Test
    void abortRejectionReleasesItsRegistration() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(0);
        CountDownLatch blocking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        executor.execute(() -> {
            blocking.countDown();
            block(release);
        });
        await(blocking);
        Completion completion = completion();
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            assertThrows(TaskRejectedException.class, () -> executor.execute(() -> fail("不应执行")));
        }
        completion.tracker.completeMain(null);
        await(completion.done);
        assertInstanceOf(RejectedExecutionException.class, completion.result.get().failure());
        assertEquals(1, completion.calls.get());
        release.countDown();
    }

    @Test
    void shutdownCallerRunsDiscardDoesNotLeaveRoundWaiting() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(0, new ThreadPoolExecutor.CallerRunsPolicy());
        executor.shutdown();
        Completion completion = completion();
        AtomicBoolean executed = new AtomicBoolean();
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            // 保留 CallerRunsPolicy 关闭后不抛异常的原有行为。
            assertDoesNotThrow(() -> executor.execute(() -> executed.set(true)));
        }
        completion.tracker.completeMain(null);
        await(completion.done);
        assertFalse(executed.get());
        assertInstanceOf(RejectedExecutionException.class, completion.result.get().failure());
    }

    @Test
    void shutdownCancelsQueuedRegistrationEvenWithTaskDecorator() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(2);
        executor.setTaskDecorator(task -> () -> task.run());
        CountDownLatch blocking = new CountDownLatch(1);
        executor.execute(() -> {
            blocking.countDown();
            block(new CountDownLatch(1));
        });
        await(blocking);
        Completion completion = completion();
        AtomicBoolean executed = new AtomicBoolean();
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            executor.execute(() -> executed.set(true));
        }
        completion.tracker.completeMain(null);
        executor.shutdown();
        await(completion.done);
        assertFalse(executed.get());
        assertInstanceOf(CancellationException.class, completion.result.get().failure());
    }

    @Test
    void decoratorFailureBeforeStartingTheTaskReleasesRegistration() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(2);
        IllegalStateException error = new IllegalStateException("装饰器失败");
        executor.setTaskDecorator(task -> () -> { throw error; });
        Completion completion = completion();
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            executor.execute(() -> fail("不应执行"));
        }
        completion.tracker.completeMain(null);
        await(completion.done);
        await(uncaughtDone);
        assertSame(error, completion.result.get().failure());
        assertSame(error, uncaught.get());
    }

    @Test
    void decoratorSkippingTheTaskDoesNotLeaveRoundWaiting() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(2);
        executor.setTaskDecorator(task -> () -> {});
        Completion completion = completion();
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            executor.execute(() -> fail("不应执行"));
        }
        completion.tracker.completeMain(null);
        await(completion.done);
        assertInstanceOf(CancellationException.class, completion.result.get().failure());
    }

    @Test
    void futureAndCompletableFutureFailuresAreNotAssumedSuccessful() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(2);
        Completion completion = completion();
        FutureTask<Void> future = new FutureTask<>(() -> { throw new IllegalArgumentException("已封装异常"); });
        CompletableFuture<Void> stage;
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            executor.execute(future);
            stage = CompletableFuture.runAsync(() -> { throw new IllegalStateException("CF异常"); }, executor);
        }
        assertThrows(ExecutionException.class, () -> future.get(3, TimeUnit.SECONDS));
        assertThrows(CompletionException.class, stage::join);
        completion.tracker.completeMain(null);
        await(completion.done);
        assertTrue(completion.result.get().incompleteTracking());
    }

    @Test
    void shutdownAlsoCancelsTheOriginalQueuedFuture() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(2);
        CountDownLatch blocking = new CountDownLatch(1);
        executor.execute(() -> {
            blocking.countDown();
            block(new CountDownLatch(1));
        });
        await(blocking);
        Completion completion = completion();
        FutureTask<Void> future = new FutureTask<>(() -> fail("不应执行"));
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            executor.execute(future);
        }
        completion.tracker.completeMain(null);
        executor.shutdown();
        await(completion.done);
        assertTrue(future.isCancelled());
        assertTrue(completion.result.get().incompleteTracking());
        assertInstanceOf(CancellationException.class, completion.result.get().failure());
    }

    @Test
    void ordinaryRequestsDoNotGainJobTrackingOrAutomaticContextPropagation() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(2);
        BaseContextHandler.setTenantId("request");
        AtomicReference<String> workerTenant = new AtomicReference<>();
        AtomicReference<TaskExecutionTracker> workerTracker = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        executor.execute(() -> {
            workerTenant.set(BaseContextHandler.getTenantId());
            workerTracker.set(TaskExecutionTracker.current());
            done.countDown();
        });
        await(done);
        assertNull(workerTenant.get());
        assertNull(workerTracker.get());
        assertEquals("request", BaseContextHandler.getTenantId());
    }

    @Test
    void submitRetainsItsReturnValueButMarksUnsupportedCompletionProtocol() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(2);
        Completion completion = completion();
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(completion.tracker)) {
            assertEquals("value", executor.submit(() -> "value").get(3, TimeUnit.SECONDS));
        }
        completion.tracker.completeMain(null);
        await(completion.done);
        assertTrue(completion.result.get().incompleteTracking());
        assertEquals("normal", executor.submit(() -> "normal").get(3, TimeUnit.SECONDS));
    }

    @Test
    void concurrentRoundsStayIsolated() throws Exception {
        TrackingThreadPoolTaskExecutor executor = executor(4);
        BaseContextHandler.setTenantId("first");
        Completion first = completion();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> firstTenant = new AtomicReference<>();
        AtomicReference<String> secondTenant = new AtomicReference<>();
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(first.tracker)) {
            executor.execute(() -> {
                started.countDown();
                block(release);
                firstTenant.set(BaseContextHandler.getTenantId());
            });
        }
        await(started);
        BaseContextHandler.setTenantId("second");
        Completion second = completion();
        try (TaskExecutionTracker.Scope ignored = TaskExecutionTracker.bind(second.tracker)) {
            executor.execute(() -> secondTenant.set(BaseContextHandler.getTenantId()));
        }
        first.tracker.completeMain(null);
        second.tracker.completeMain(null);
        assertNull(first.result.get());
        assertNull(second.result.get());
        release.countDown();
        await(first.done);
        await(second.done);
        assertEquals("first", firstTenant.get());
        assertEquals("second", secondTenant.get());
        assertEquals(1, first.calls.get());
        assertEquals(1, second.calls.get());
    }

    @Test
    void failingCompletionCallbackIsNotRetriedOrRethrown() {
        AtomicInteger calls = new AtomicInteger();
        TaskExecutionTracker tracker = new TaskExecutionTracker(() -> bindLog("execution"), result -> {
            calls.incrementAndGet();
            throw new IllegalStateException("统计设施故障");
        });
        assertDoesNotThrow(() -> tracker.completeMain(new AssertionError("原业务错误")));
        tracker.completeMain(null);
        assertEquals(1, calls.get());
        assertNull(logContext.get());
    }

    private TrackingThreadPoolTaskExecutor executor(int capacity) {
        return executor(capacity, new ThreadPoolExecutor.AbortPolicy());
    }

    private TrackingThreadPoolTaskExecutor executor(int capacity, RejectedExecutionHandler rejectionHandler) {
        TrackingThreadPoolTaskExecutor executor = new TrackingThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(capacity);
        executor.setRejectedExecutionHandler(rejectionHandler);
        executor.setThreadFactory(task -> {
            Thread thread = new Thread(task, "execution-tracker-test");
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((worker, error) -> {
                uncaught.compareAndSet(null, error);
                uncaughtDone.countDown();
            });
            return thread;
        });
        executor.initialize();
        executors.add(executor);
        return executor;
    }

    private Completion completion() {
        AtomicReference<TaskExecutionTracker.Result> result = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);
        TaskExecutionTracker tracker = new TaskExecutionTracker(() -> bindLog("execution"), value -> {
            result.set(value);
            calls.incrementAndGet();
            done.countDown();
        });
        return new Completion(tracker, result, calls, done);
    }

    private AutoCloseable bindLog(String value) {
        String previous = logContext.get();
        logContext.set(value);
        return () -> {
            if (previous == null) logContext.remove();
            else logContext.set(previous);
        };
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(3, TimeUnit.SECONDS), "任务没有在期限内完成");
    }

    private static void block(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }

    private record Completion(TaskExecutionTracker tracker, AtomicReference<TaskExecutionTracker.Result> result,
                              AtomicInteger calls, CountDownLatch done) {}
}
