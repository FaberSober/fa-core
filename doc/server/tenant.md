# 多租户非标准数据访问约定

适用于 `fa.setting.tenant.enabled=true` 的业务代码。标准 `BaseTn*Entity` CRUD 已由核心层自动隔离；自定义 SQL、异步任务、导出、缓存和消息必须遵循以下规则。

## 1. 通用原则

- 租户业务必须使用 `TenantContext.requireTenantId()` 获取可信租户 ID；不信任请求体或请求头直接传入的 `tenantId`。
- 平台控制面操作可以没有租户上下文，但必须由受保护的显式服务执行，不能把“忽略租户”做成通用开关。
- 单租户模式不要求租户上下文，但业务代码仍保持同一调用路径，不复制另一套 Service 或 Controller。

## 2. 自定义 SQL

- XML、注解 SQL、原生 SQL 不自动获得 Entity 租户拦截能力；租户私有表必须显式追加 `tenant_id = #{tenantId}`。
- Service 先校验并传递 `TenantContext.requireTenantId()`，SQL 使用 `#{tenantId}`，禁止使用 `${tenantId}` 拼接。
- 多表关联时，每张租户私有表都要有租户条件；平台表按平台语义查询，不因关联而隐式扩大租户范围。
- 自定义 SQL 同时支持单租户模式时，用配置判断是否需要租户 ID；多租户模式缺少租户上下文必须拒绝访问。
- 复杂报表、动态表名和批量 SQL 逐条确认租户条件，不能因“入口已校验”省略数据库条件。

## 3. 异步任务与定时任务

- 提交任务时捕获租户 ID；任务参数或消息载荷必须明确记录租户 ID，不能依赖执行线程原有的 `ThreadLocal`。
- 需要保留操作人审计信息时，复制 `BaseContextHandler.getHoldMap()` 后再传递，禁止多个线程共享可变 Map。
- 工作线程执行前恢复上下文，执行后在 `finally` 中调用 `BaseContextHandler.remove()`，防止线程池复用造成跨租户污染。
- 跨租户平台任务按租户逐个设置上下文；平台任务保持空租户上下文，不复用上一次任务的租户信息。

```java
Map<String, Object> holder = BaseContextHandler.getHoldMap() == null
        ? null : new HashMap<>(BaseContextHandler.getHoldMap());
String tenantId = TenantContext.getTenantId();
executor.execute(() -> {
    try {
        BaseContextHandler.setHoldMap(holder);
        TenantContext.setTenantId(tenantId);
        // 执行租户业务
    } finally {
        BaseContextHandler.remove();
    }
});
```

## 4. 导出与批处理

- 标准 `BaseBiz.exportExcel()`、分页和批量接口沿用当前请求租户上下文，不另写租户条件。
- 自定义导出必须复用同一 Biz 查询路径；异步导出必须把租户 ID 放入任务参数并在执行线程恢复。
- 导入、导出、批量更新不能使用客户端提交的租户 ID 覆盖上下文；平台管理员跨租户操作必须明确目标租户。

## 5. 缓存

- Redis、JetCache 等跨请求缓存的租户业务 Key 必须包含 `tenantId`；推荐格式：`业务前缀:tenant:{tenantId}:业务键`。
- 平台级缓存使用固定的 `platform` 范围；不能用空字符串与某个租户缓存混用。
- 权限缓存至少包含 `userId + tenantId`；切换租户后重新计算或读取另一租户 Key。
- `BaseContextHandler` 内的线程缓存只允许当前请求使用，不能直接放入异步任务或跨请求复用。
- 更新、删除和租户权限变更时，清理范围必须与读缓存的租户范围一致；无法精确清理时宁可清理对应业务前缀的全部缓存。

## 6. 消息与事件消费

- 租户业务事件的载荷必须携带 `tenantId`；平台事件明确使用 `null` 或 `platform`，不从 HTTP 请求头补充。
- 消费者处理数据库、权限或缓存操作前恢复租户上下文，并校验租户状态；处理结束后清理上下文。
- 广播消息应按租户拆分投递，或明确标记为平台消息；不能把一个租户事件广播给所有租户。
- 重试、延迟和死信消息必须保留原始租户 ID，不能使用重试线程当前残留的上下文。

## 7. 最小检查清单

- 是否明确区分平台操作和租户操作？
- 自定义 SQL 的每张租户私有表是否带 `tenant_id` 条件？
- 异步、定时、导出和消费任务是否显式携带并清理租户上下文？
- 跨请求缓存 Key 是否包含租户范围？
- 是否拒绝使用客户端租户 ID 直接覆盖可信上下文？
