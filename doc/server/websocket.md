# WebSocket

# Java服务端
## 发送简单文本
```java
// 1. 设置发送频道
WsHolder.setChannel("PullNewData");
// 2. 后续消息均在上述频道中发布
WsHolder.sendMessage(WsTypeEnum.PLAIN_TEXT, "开始下载NWP数据...");
```

# Web前端
## 发送消息
```typescript jsx
// 新版本
import { sendMessage } from '@features/fa-admin-pages/layout/websocket';

sendMessage({ type: 'test', data: { msg } });
```

## 接受数据
```typescript jsx
import useBus from "use-bus";

useBus(
    ['@@ws/RECEIVE/WebSocketTaskDemo'],
    ({ type, channel, payload }) => {
        console.log(type, channel, payload);
    },
    [],
)
```

# 导入数据加入导入进度
## 前度导入Modal
1. 设置`showMsg`为true；
2. 设置`showMsgChannel`信号接收频道；
```typescript jsx
<CommonExcelUploadModal
    fetchFinish={refreshPage}
    apiImportExcel={api.importExcel}
    showTemplateDownload={false}
    showMsg
    showMsgChannel="IMPORT_CHANNEL_XX"
    tips="导入说明"
>
    <Button icon={<UploadOutlined />}>导入</Button>
</CommonExcelUploadModal>
```

## 后端增加导入进度websocket返回
```java
// 1. 设置发送频道
WsHolder.setChannel("IMPORT_CHANNEL_XX");
// 2. 后续消息均在上述频道中发布
WsHolder.sendMessage(WsTypeEnum.PLAIN_TEXT, "开始导入数据数据...");
```

### 后台导入使用新的线程导入
> 导入任务如果耗时较长，建议开启新的线程导入。参考代码如下
```java
Map<String, Object> holdMap = BaseContextHandler.getHoldMap(); // 保存当前线程用户信息
executor.execute(() -> {
    BaseContextHandler.setHoldMap(holdMap); // 把保存的用户信息设置到新线程中
    // TODO 导入逻辑
});
```

## 本项目websocket数据格式说明
> 统一使用JSON格式
### 请求参数
```json
{
  "type": "request", // 请求类型，对应分发到不同的业务
  "data": {
    "id": "123456",
    "content": "Hello, World!"
  }
}
```

### 返回参数
```json
{
  "code": 0, // 错误码
  "type": "request", // 返回类型，对应分发到不同的业务
  "msg": "success", // 错误信息
  "data": {
    "id": "123456",
    "content": "Hello, World!"
  }
}
```

## websocket中Session的 getBasicRemote() 和 getAsyncRemote() 方法的区别
websocket中Session的 getBasicRemote() 和 getAsyncRemote() 方法有以下主要区别

同步 vs 异步
1. getBasicRemote()：同步发送消息的方式。发送消息时会阻塞当前线程，直到消息发送完成。
2. getAsyncRemote()：异步发送消息的方式。发送消息后立即返回，不阻塞当前线程。

性能和并发
1. getBasicRemote()：由于同步特性，可能会导致性能瓶颈，尤其是在高并发场景下。
2. getAsyncRemote()：异步特性使得它更适合高并发场景，提高系统的响应速度和吞吐量。

使用场景
1. getBasicRemote()：适用于消息量较小且对实时性要求较高的场景。
2. getAsyncRemote()：适用于大量消息发送或者对实时性要求相对较低的场景。

总之两者的使用场景为：
- getBasicRemote()：同步发送，适合小量消息和高实时性需求。
- getAsyncRemote()：异步发送，适合大量消息和高并发场景。

原文链接：https://blog.csdn.net/weixin_46425661/article/details/141897511

## Reference
[Springboot整合websocket(附详细案例代码)](https://blog.csdn.net/weixin_46425661/article/details/141897511)
