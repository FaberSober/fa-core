package com.faber.core.config.thread;

import com.faber.core.context.BaseContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 一轮主流程及其线程池子任务的生命周期。只提供通用回调，不依赖业务统计或日志实现。
 */
public final class TaskExecutionTracker {
    private static final Logger log = LoggerFactory.getLogger(TaskExecutionTracker.class);
    private static final ThreadLocal<TaskExecutionTracker> CURRENT = new ThreadLocal<>();

    private final Supplier<? extends AutoCloseable> bindTaskContext;
    private final Consumer<Result> completion;
    private final Map<String, Object> completionContext;
    private int pending = 1;
    private boolean mainCompleted;
    private boolean completed;
    private boolean incompleteTracking;
    private Throwable failure;

    public TaskExecutionTracker(Supplier<? extends AutoCloseable> bindTaskContext, Consumer<Result> completion) {
        this.bindTaskContext = Objects.requireNonNull(bindTaskContext);
        this.completion = Objects.requireNonNull(completion);
        this.completionContext = snapshotContext();
    }

    public static TaskExecutionTracker current() {
        return CURRENT.get();
    }

    public static Scope bind(TaskExecutionTracker tracker) {
        TaskExecutionTracker previous = CURRENT.get();
        restoreTracker(tracker);
        return new Scope(previous);
    }

    /** 主流程退出后调用；主流程自身保留一个登记，避免子任务过早触发完成。 */
    public void completeMain(Throwable cause) {
        Result result;
        synchronized (this) {
            if (mainCompleted) return;
            mainCompleted = true;
            recordFailure(cause);
            result = release();
        }
        notifyCompletion(result);
    }

    /** 未接入完成协议的异步入口不能据其提交结果推断业务成功。 */
    public synchronized void markIncompleteTracking() {
        incompleteTracking = true;
    }

    TrackedTask register(Runnable task) {
        Objects.requireNonNull(task);
        Map<String, Object> context = snapshotContext();
        synchronized (this) {
            if (completed) throw new IllegalStateException("本轮任务已经结束");
            if (task instanceof Future<?> || task instanceof CompletableFuture.AsynchronousCompletionTask) {
                incompleteTracking = true;
            }
            pending++;
        }
        return new TrackedTask(this, task, context);
    }

    private void completeTask(Throwable cause) {
        Result result;
        synchronized (this) {
            recordFailure(cause);
            result = release();
        }
        notifyCompletion(result);
    }

    private Result release() {
        pending--;
        if (pending != 0 || completed) return null;
        completed = true;
        return new Result(failure, incompleteTracking);
    }

    private void recordFailure(Throwable cause) {
        if (cause == null) return;
        if (failure == null) {
            failure = cause;
        } else if (failure != cause && failure.getSuppressed().length < 8) {
            failure.addSuppressed(cause);
        }
    }

    private void notifyCompletion(Result result) {
        if (result == null) return;
        Map<String, Object> previousContext = BaseContextHandler.getHoldMap();
        TaskExecutionTracker previousTracker = CURRENT.get();
        // 收尾操作可能再次使用线程池，不允许把它登记到已结束的这一轮。
        CURRENT.remove();
        restoreContext(copyContext(completionContext));
        try (AutoCloseable ignored = bindTaskContext.get()) {
            completion.accept(result);
        } catch (Throwable error) {
            // 统计、日志等回调故障不能覆盖业务异常，也不能再次触发收尾。
            log.error("任务完成回调失败", error);
        } finally {
            restoreContext(previousContext);
            restoreTracker(previousTracker);
        }
    }

    private static Map<String, Object> snapshotContext() {
        return copyContext(BaseContextHandler.getHoldMap());
    }

    private static Map<String, Object> copyContext(Map<String, Object> context) {
        return context == null ? null : new HashMap<>(context);
    }

    private static void restoreContext(Map<String, Object> context) {
        if (context == null) BaseContextHandler.remove();
        else BaseContextHandler.setHoldMap(context);
    }

    private static void restoreTracker(TaskExecutionTracker tracker) {
        if (tracker == null) CURRENT.remove();
        else CURRENT.set(tracker);
    }

    public record Result(Throwable failure, boolean incompleteTracking) {}

    public static final class Scope implements AutoCloseable {
        private final TaskExecutionTracker previous;
        private boolean closed;

        private Scope(TaskExecutionTracker previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            restoreTracker(previous);
        }
    }

    static final class TrackedTask implements Runnable {
        private static final int PENDING = 0;
        private static final int RUNNING = 1;
        private static final int FINISHED = 2;
        private final TaskExecutionTracker tracker;
        private final Runnable task;
        private final Map<String, Object> context;
        private final AtomicInteger state = new AtomicInteger(PENDING);

        private TrackedTask(TaskExecutionTracker tracker, Runnable task, Map<String, Object> context) {
            this.tracker = tracker;
            this.task = task;
            this.context = context;
        }

        @Override
        public void run() {
            if (!state.compareAndSet(PENDING, RUNNING)) return;
            Map<String, Object> previousContext = BaseContextHandler.getHoldMap();
            Throwable cause = null;
            restoreContext(context);
            try (Scope ignored = bind(tracker); AutoCloseable taskContext = tracker.bindTaskContext.get()) {
                task.run();
            } catch (Throwable error) {
                cause = error;
                TaskExecutionTracker.<RuntimeException>throwUnchecked(error);
            } finally {
                restoreContext(previousContext);
                state.set(FINISHED);
                tracker.completeTask(cause);
            }
        }

        boolean hasStarted() {
            return state.get() != PENDING;
        }

        void cancel(Throwable cause) {
            if (!state.compareAndSet(PENDING, FINISHED)) return;
            try {
                if (task instanceof Future<?> future) future.cancel(true);
            } catch (Throwable error) {
                log.error("取消未执行的任务失败", error);
            } finally {
                tracker.completeTask(cause);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> void throwUnchecked(Throwable error) throws T {
        throw (T) error;
    }
}
