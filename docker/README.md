# FoodMate 本地基础设施

`compose.yml` 是本项目本地基础设施的统一入口。PostgreSQL、Redis 和 MinIO 是三个独立容器，加入同一个 `foodmate` 网络并由同一个 Compose 项目管理；它们不应合并成单个容器。

从项目根目录启动：

```powershell
docker compose --env-file .env -f docker/compose.yml up -d
docker compose --env-file .env -f docker/compose.yml ps
```

首次启动后创建私有头像/导出 Bucket（已在当前本地环境执行）：

```powershell
docker run --rm --network foodmate `
  -e MC_HOST_local="http://${MINIO_ROOT_USER}:${MINIO_ROOT_PASSWORD}@foodmate-minio:9000" `
  minio/mc:latest mb --ignore-existing local/${MINIO_BUCKET}
```

Bucket 保持 private；应用只使用短时签名 URL，不把永久 MinIO 地址写入数据库。

停止服务但保留数据：

```powershell
docker compose --env-file .env -f docker/compose.yml down
```

默认数据卷为 `foodmate-postgres-data`、`foodmate-redis-data` 和 `foodmate-minio-data`。数据库 DDL 仍按 `script/sql/FoodMate` 下的人工 SQL 执行，Compose 不会自动启用 Flyway 或自动修改业务表。

配置模板见 `.env.example`。真实 `.env` 只保存在本机并被 Git 忽略。

## RocketMQ

`rocketmq-namesrv` + `rocketmq-broker` 是 Java 控制面与 Python Runtime 的异步主通道（[ADR-0005](../docxs/决策/ADR-0005-RocketMQ异步主通道.md)）。本地只部署单 NameServer + 单 Broker，不配置集群、TLS 或 ACL。

```powershell
docker compose --env-file .env -f docker/compose.yml up -d rocketmq-broker
docker compose --env-file .env -f docker/compose.yml up rocketmq-init
```

`rocketmq-init` 是一次性容器，创建 4 个 Agent Topic 与 4 个 consumer group 后退出；可重复执行。它共享 Broker 的网络命名空间，因此 `mqadmin` 的 `127.0.0.1:10911` 与 Broker 注册到 NameServer 的地址一致。

三个约束容易踩坑：

1. **Topic 名不能带点号。** Broker 强制 `^[%|a-zA-Z0-9_-]+$`，`foodmate.agent.command.v1` 会被拒绝，因此契约统一使用 `foodmate-agent-command-v1` 这种连字符命名。
2. **数据目录属主。** 镜像里没有 `/home/rocketmq/{store,logs}`，Docker 建命名卷时用 root 创建目录，而进程以 uid 3000 运行。`rocketmq-prepare` 一次性容器负责 `chown`，namesrv/broker 依赖它成功退出后才启动。
3. **`brokerIP1=127.0.0.1`。** Java 与 Python 都跑在宿主机上，NameServer 必须返回宿主机可达的 Broker 地址；填容器 IP 会导致客户端连不上。

Broker 重启后消息保留（`foodmate-rocketmq-broker-store` 命名卷 + `SYNC_FLUSH`）：

```powershell
docker exec foodmate-rocketmq-broker sh -c "/home/rocketmq/rocketmq-5.2.0/bin/mqadmin topicStatus -n foodmate-rocketmq-namesrv:9876 -t foodmate-agent-command-v1"
```

重启前后 `Max Offset` 应保持一致。
