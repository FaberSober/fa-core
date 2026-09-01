# thread

## 线程池配置
配置项代码目录：`com.faber.core.config.thread.ThreadPoolConfig`

示例代码：`com.faber.base.thread.ThreadPoolTest`

```java
@Autowired
private Executor executor;

Map<String, Object> holdMap = BaseContextHandler.getHoldMap(); // 保存当前线程用户信息
executor.execute(() -> {
    // 线程中执行
    BaseContextHandler.setHoldMap(holdMap); // 把保存的用户信息设置到新线程中
});
```
