# 线程池

## 线程池配置

配置项代码目录：`com.faber.core.config.thread.ThreadPoolConfig`，统一 Bean 名为 `executor`，使用 `TrackingThreadPoolTaskExecutor`，保留原有 Spring 线程池类型、配置和生命周期。

示例代码：`com.faber.base.thread.ThreadPoolTest`

```java
@Autowired
private Executor executor;

Map<String, Object> current = BaseContextHandler.getHoldMap();
Map<String, Object> holdMap = current == null ? null : new HashMap<>(current);
executor.execute(() -> {
    Map<String, Object> previous = BaseContextHandler.getHoldMap();
    try {
        BaseContextHandler.setHoldMap(holdMap);
        // 异步业务
    } finally {
        if (previous == null) BaseContextHandler.remove();
        else BaseContextHandler.setHoldMap(previous);
    }
});
```

- 普通请求中的异步任务仍按上述方式传递上下文；每次提交使用独立快照，结束后恢复原值，避免线程复用串用户。
- 原有 `CallerRunsPolicy` 在线程池繁忙时可能直接在调用线程执行，因此清理时要恢复原上下文，不能一律删除。
- 事务不跨线程传播；不要为异步操作或日志临时创建无界线程池。

## 定时任务中的异步工作

继承 `BaseJob` 的任务，通过已注入的统一 `executor` 直接提交 Runnable 时，框架自动跟踪本轮异步工作，无需逐个改成 Future 或自行编写统计：

```java
@Override
protected void run() {
    executor.execute(() -> {
        JobRunLogger.info("开始异步业务");
        // 业务处理；异常需要导致整轮失败时，应继续抛出
        JobRunLogger.info("异步业务完成");
    });
}
```

- 主流程及子任务通过同一执行器提交的关联任务归入同一轮，支持嵌套提交。主流程返回后释放调度线程，全部关联工作结束后才发布最终结果并关闭本轮日志。
- 自动复制、恢复 `BaseContextHandler` 中的用户、租户及分表等上下文，并传播本轮日志上下文。现有手动传递代码及 `JobRunLogger.wrap` 保持兼容；其他独立 ThreadLocal 仍需按所属组件约定处理。
- 任一关联工作出现未被业务处理的异常时，该轮失败，但已经提交的其他工作仍需结束后再收尾。仅打印错误日志或捕获后正常返回，不会自动改变任务结果。
- 一轮调度只增加一次执行次数，不按线程数量累计。触发耗时为本轮开始到主流程退出；总耗时为本轮开始到全部关联工作结束，包含排队等待，并行耗时不相加。
- 拒绝提交或关闭时取消的排队任务会释放跟踪登记；关闭后 CallerRunsPolicy 静默丢弃的任务也不能被误计为成功。运行中的任务仍需实际退出，框架不会强行终止业务线程。

## 跟踪范围与限制

- 本期完整跟踪范围是直接 `execute(Runnable)` 及其关联提交。普通请求无定时任务执行上下文，不计入定时任务统计。
- `submit`、`@Async`、`execute(FutureTask)`、`CompletableFuture` 等封装可能在内部保存异常，提交返回不等于业务成功。检测到这些未接入完成协议的提交时，本轮标记为未完整跟踪，最终结果为“未确认”，不计入成功／失败和平均总耗时样本。
- 自建线程池、`new Thread`、默认公共线程池和跨进程任务不会自动纳入；需要通过专门完成协议接入后才能确认整轮结果。
- 进程异常退出可能留下未结束记录，本期不提供任务心跳或重启中断自动判定。
