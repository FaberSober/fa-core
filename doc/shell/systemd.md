# systemd 部署与操作说明

`systemd` 适合在 Linux 服务器上长期运行 `fa-admin`。使用 `systemd` 管理后，不要再同时使用 `service.sh` 启动同一个 JAR，避免端口冲突和重复进程。

以下示例以授权中心部署为例，请按实际服务器修改用户、目录和 JDK 路径。

## 1. 准备应用和密钥

示例目录：

```text
/home/www/app/fa-ai/
├── fa-admin.jar
└── private-key.pem
```

确保运行用户能够读取 JAR 和私钥，并限制私钥权限：

```bash
sudo chown www:www /home/www/app/fa-ai/private-key.pem
sudo chmod 600 /home/www/app/fa-ai/private-key.pem
```

授权中心的 `application-prod.yml` 使用环境变量读取私钥：

```yaml
license-center:
  private-key-file: ${LICENSE_CENTER_PRIVATE_KEY_FILE:}
```

客户端不要配置授权中心私钥，只配置客户端需要的授权中心地址、授权码和公钥等参数。

## 2. 创建环境变量文件

创建 `/etc/fa-ai/fa-admin.env`：

```bash
sudo mkdir -p /etc/fa-ai
sudo vi /etc/fa-ai/fa-admin.env
```

授权中心环境变量示例：

```dotenv
LICENSE_CENTER_PRIVATE_KEY_FILE=/home/www/app/fa-ai/private-key.pem
```

限制环境变量文件权限：

```bash
sudo chmod 600 /etc/fa-ai/fa-admin.env
```

`EnvironmentFile` 中的变量会传递给 Java 进程，Spring Boot 可以直接读取 `${LICENSE_CENTER_PRIVATE_KEY_FILE:}`。不要把私钥内容直接写入 unit 文件或提交到 Git。

## 3. 创建 unit 文件

创建 `/etc/systemd/system/fa-admin.service`：

```bash
sudo vi /etc/systemd/system/fa-admin.service
```

内容如下：

```ini
[Unit]
Description=fa-admin
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=www
Group=www
WorkingDirectory=/home/www/app/fa-ai
EnvironmentFile=/etc/fa-ai/fa-admin.env
ExecStart=/usr/local/java/jdk-17.0.12/bin/java -jar /home/www/app/fa-ai/fa-admin.jar --spring.profiles.active=prod
SuccessExitStatus=143
Restart=on-failure
RestartSec=5
TimeoutStopSec=30
KillSignal=SIGTERM
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

`ExecStart` 不经过 shell 解析，因此要使用绝对路径；环境变量通过 `EnvironmentFile` 提供，不要写成 `--LICENSE_CENTER_PRIVATE_KEY_FILE=...`。

## 4. 加载并启动服务

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now fa-admin
sudo systemctl status fa-admin
```

验证服务是否已设置开机启动：

```bash
systemctl is-enabled fa-admin
systemctl is-active fa-admin
```

## 5. 常用操作

```bash
# 查看当前状态
sudo systemctl status fa-admin

# 启动、停止、重启
sudo systemctl start fa-admin
sudo systemctl stop fa-admin
sudo systemctl restart fa-admin

# 查看最近日志
sudo journalctl -u fa-admin -n 100 --no-pager

# 持续查看日志
sudo journalctl -u fa-admin -f
```

## 6. 更新 JAR

先停止服务，再替换 JAR，最后启动并检查日志：

```bash
sudo systemctl stop fa-admin
# 将新的 fa-admin.jar 放到 /home/www/app/fa-ai/fa-admin.jar
sudo systemctl start fa-admin
sudo systemctl status fa-admin
```

如果只修改了环境变量文件或 unit 文件，执行：

```bash
sudo systemctl daemon-reload
sudo systemctl restart fa-admin
```

## 7. 客户端配置提示

客户端可以单独使用 `/etc/fa-ai/fa-admin-client.env`，例如：

```dotenv
FA_LICENSE_SERVER_URL=https://fa.ai.dward.cn
FA_LICENSE_KEY=替换为客户端授权码
```

然后将 unit 中的 `EnvironmentFile` 改为该文件。客户端不需要 `LICENSE_CENTER_PRIVATE_KEY_FILE`；公钥属于多行 PEM 内容时，建议继续放在 `application-prod.yml` 的 YAML 多行块中，不要直接写入简单的 `EnvironmentFile`。

## 8. 常见问题

- 服务反复重启：执行 `sudo journalctl -u fa-admin -n 200 --no-pager`，重点检查 JDK、JAR、配置文件和目录权限。
- 私钥读取失败：确认 `private-key.pem` 存在，且 `www` 用户有读取权限。
- 修改配置未生效：确认修改的是 `EnvironmentFile`，并执行 `daemon-reload` 后重启服务。
- 端口被占用：确认没有通过 `service.sh` 或其他方式启动同一个 `fa-admin.jar`。
