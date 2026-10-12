# 部署
## 方式一
1. mvn打包，将会先打包frontend前端项目，然后将前端项目一起打包到jar中；
2. 使用`fa-admin/src/test/java/com/faber/utils/deploy/FaDeploySimpleTest.java`测试方法来进行部署；

## 显式 FTPS 下载

- 使用 OpenJDK 17 或更新版本，显式 FTPS 地址填写 `ftps://主机名或IP`，端口通常为 `21`；普通 FTP 配置不变。
- 使用被动模式和 TLS 1.2，控制、数据连接均加密。自动信任证书（无需配置 Java 信任库），但地址仍须匹配证书。自动信任存在服务器被冒充的风险。

### 启动参数

以下三个 JVM 参数必须放在 `-jar` 前，保留部署环境原有的内存参数和 Spring profile：

```sh
java -Xms512m -Xmx1024m \
  --add-opens=java.base/sun.security.ssl=ALL-UNNAMED \
  --add-opens=java.base/sun.security.util=ALL-UNNAMED \
  -Djdk.tls.client.enableSessionTicketExtension=false \
  -jar /opt/fa-admin/fa-admin.jar --spring.profiles.active=prod
```

本地 `.vscode/launch.json`、部署脚本及模板、Dockerfile 已加入参数。首次部署改造需构建并部署新 jar；只调整启动参数时需重启进程，热更新无效。

### 验证与限制

- ✅ 用户已确认中电建 FTPS 文件正常下载；后端编译及 12 项定向测试通过。
- 会话复用依赖 OpenJDK 内部实现，升级 JDK 或更换服务器后须重新验证；Docker 镜像尚未验证。
- 禁用 session ticket 的参数作用于整个 JVM，可能影响其他 TLS 客户端的会话恢复及性能。
