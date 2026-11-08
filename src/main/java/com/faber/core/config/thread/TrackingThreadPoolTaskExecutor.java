package com.faber.core.config.thread;

import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.concurrent.ListenableFuture;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 在原 Spring 执行器上跟踪带执行上下文的直接 Runnable 提交，保留原有类型与生命周期。
 */
public class TrackingThreadPoolTaskExecutor extends ThreadPoolTaskExecutor {
    private volatile TaskDecorator configuredDecorator;

    public TrackingThreadPoolTaskExecutor() {
        super.setTaskDecorator(this::decorateTask);
        setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void execute(Runnable task) {
        TaskExecutionTracker tracker = TaskExecutionTracker.current();
        if (tracker == null) {
            super.execute(task);
            return;
        }
        TaskExecutionTracker.TrackedTask tracked = tracker.register(task);
        try {
            super.execute(tracked);
        } catch (RuntimeException | Error error) {
            tracked.cancel(error);
            throw error;
        }
    }

    @Override
    public void setTaskDecorator(TaskDecorator taskDecorator) {
        configuredDecorator = taskDecorator;
    }

    private Runnable decorateTask(Runnable task) {
        TaskDecorator decorator = configuredDecorator;
        if (decorator == null) return task;
        Runnable decorated = Objects.requireNonNull(decorator.decorate(task));
        TaskExecutionTracker.TrackedTask tracked = trackedTask(task);
        return tracked == null ? decorated : new DecoratedTask(decorated, tracked);
    }

    @Override
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        RejectedExecutionHandler delegate = handler == null ? new ThreadPoolExecutor.AbortPolicy() : handler;
        super.setRejectedExecutionHandler((task, executor) -> {
            Throwable rejection = null;
            try {
                delegate.rejectedExecution(task, executor);
            } catch (RuntimeException | Error error) {
                rejection = error;
                throw error;
            } finally {
                TaskExecutionTracker.TrackedTask tracked = trackedTask(task);
                // CallerRunsPolicy 正常时已执行；关闭后静默丢弃时必须释放登记。
                if (tracked != null && !tracked.hasStarted() && !executor.getQueue().contains(task)) {
                    tracked.cancel(rejection == null
                            ? new RejectedExecutionException("线程池未接受任务") : rejection);
                }
            }
        });
    }

    @Override
    protected void cancelRemainingTask(Runnable task) {
        TaskExecutionTracker.TrackedTask tracked = trackedTask(task);
        if (tracked != null) tracked.cancel(new CancellationException("线程池关闭，任务尚未执行"));
        super.cancelRemainingTask(task);
    }

    @Override
    public Future<?> submit(Runnable task) {
        markUnsupportedSubmission();
        return super.submit(task);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        markUnsupportedSubmission();
        return super.submit(task);
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public ListenableFuture<?> submitListenable(Runnable task) {
        markUnsupportedSubmission();
        return super.submitListenable(task);
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public <T> ListenableFuture<T> submitListenable(Callable<T> task) {
        markUnsupportedSubmission();
        return super.submitListenable(task);
    }

    private void markUnsupportedSubmission() {
        TaskExecutionTracker tracker = TaskExecutionTracker.current();
        if (tracker != null) tracker.markIncompleteTracking();
    }

    private static TaskExecutionTracker.TrackedTask trackedTask(Runnable task) {
        if (task instanceof TaskExecutionTracker.TrackedTask tracked) return tracked;
        if (task instanceof DecoratedTask decorated) return decorated.tracked;
        return null;
    }

    private record DecoratedTask(Runnable delegate, TaskExecutionTracker.TrackedTask tracked) implements Runnable {
        @Override
        public void run() {
            Throwable failure = null;
            try {
                delegate.run();
            } catch (RuntimeException | Error error) {
                failure = error;
                throw error;
            } finally {
                if (!tracked.hasStarted()) {
                    tracked.cancel(failure == null
                            ? new CancellationException("任务装饰器未执行关联工作") : failure);
                }
            }
        }
    }
}
