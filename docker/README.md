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

## 应用容器

构建并启动 Java 控制面和 Python Agent Runtime：

```powershell
docker compose --env-file .env -f docker/compose.yml up -d --build foodmate agent-runtime
docker compose --env-file .env -f docker/compose.yml ps foodmate agent-runtime
```

Java 容器使用 `local` profile，Python 容器默认使用 Redis-backed `stub` 索引；两者都只使用 deterministic 本地逻辑，不调用付费模型或 embedding 服务。应用容器不会自动执行 `script/sql/FoodMate` 迁移，迁移必须按版本人工执行并记录。

容器内 readiness：

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health/readiness
Invoke-WebRequest http://localhost:9002/foodmate/internal/health/ready
```

Java 容器通过 Compose 网络访问 `agent-runtime:9000`，不应在容器配置中使用宿主机的 `localhost`。本地 Compose 已将四档 Agent 模型路由锁定为 `deterministic:local`，避免宿主机 `.env` 中的云模型配置被应用容器继承；这些设置只用于本地业务验证，不代表真实模型联调已经完成。

应用容器不会自动执行数据库迁移。启动前应确认 V16-V25 已按 `script/sql/FoodMate` 的顺序实际执行，启动后再检查 Java 和 Python readiness，以及应用日志中的 Outbox/Worker 状态。停止时使用 `docker compose ... down` 保留数据卷，除非明确需要销毁本地卷并另行确认。

## M2-1 RAG

默认 `FOODMATE_RAG_MODE=stub`，只使用 Redis 隔离前缀保存确定性关键词索引，不连接 Milvus，也不读取 embedding API Key。

需要验证向量索引业务路径时，使用 `FOODMATE_RAG_MODE=local` 和
`FOODMATE_RAG_EMBEDDING_PROVIDER=deterministic`。此模式使用本地确定性向量并写入 Compose 的 Milvus：

```powershell
docker compose --env-file .env -f docker/compose.yml up -d milvus
```

无需真实模型或付费 embedding 服务。Compose 网络内的 Runtime 使用
`http://milvus:19530`；宿主机启动的 Java/Python 进程则使用
`http://localhost:19530`。集合维度以首次生成的实际向量为准，已有集合维度不一致时会失败关闭。

local 模式的 Python 索引消费者会在订阅索引 Topic 前探测 Milvus 的
`/healthz`（默认从 `FOODMATE_RAG_MILVUS_URI` 的 `19530` 端口推导为 `9091`），
直到 readiness 成功或达到 `FOODMATE_RAG_MILVUS_READY_TIMEOUT_SECONDS`；stub 模式不执行该探测。
自定义探针地址时设置 `FOODMATE_RAG_MILVUS_HEALTH_URL`。

Compose 还会把 local 模式所需的预算、价格版本和确定性向量维度传入
`agent-runtime`。默认仍是 `stub`，因此不启动 Milvus 也不会连接它；切换到
`local` 时必须显式启动 Milvus，并保持 collection 名称与隔离环境一致：

```powershell
$env:FOODMATE_RAG_MODE = "local"
$env:FOODMATE_RAG_EMBEDDING_PROVIDER = "deterministic"
$env:FOODMATE_RAG_MILVUS_URI = "http://milvus:19530"
$env:FOODMATE_RAG_MILVUS_COLLECTION = "foodmate_knowledge_chunks_local"
docker compose --env-file .env -f docker/compose.yml up -d milvus foodmate agent-runtime
```

切换真实 OpenAI-compatible embedding 时，将 provider 改为 `openai-compatible`，并显式配置 endpoint、API Key、model、预算和价格版本；缺少任一配置不会回退到 stub 或 deterministic。当前只提供基础设施容器，Python/Java 应用仍按各自开发命令启动；Compose 不自动执行数据库迁移。

## RocketMQ

`rocketmq-namesrv` + `rocketmq-broker` 是 Java 控制面与 Python Runtime 的异步主通道（[ADR-0005](../docxs/决策/ADR-0005-RocketMQ异步主通道.md)）。本地只部署单 NameServer + 单 Broker，不配置集群、TLS 或 ACL。

```powershell
docker compose --env-file .env -f docker/compose.yml up -d rocketmq-broker
docker compose --env-file .env -f docker/compose.yml up rocketmq-init
```

`rocketmq-init` 是一次性容器，创建 9 个业务 Topic 与 11 个 consumer group 后退出；可重复执行。它共享 Broker 的网络命名空间，因此 `mqadmin` 的 `127.0.0.1:10911` 与 Broker 注册到 NameServer 的地址一致。9 个业务 Topic 包含 4 个 Agent Topic、2 个知识索引 Topic、1 个可见性 Topic 和 2 个清理 Topic；清理 Topic 分别是 `foodmate-knowledge-purge-v1` 与 `foodmate-knowledge-purge-result-v1`。

Broker 关闭了 `autoCreateTopicEnable` 与 `autoCreateSubscriptionGroup`，所以**消费组也必须预先创建**——用未登记的消费组订阅会静默收不到消息。其中 `foodmate-selftest-v1` 专供自动化测试，避免测试挪动 Java/Python 正式消费组的位点。

三个约束容易踩坑：

1. **Topic 名不能带点号。** Broker 强制 `^[%|a-zA-Z0-9_-]+$`，`foodmate.agent.command.v1` 会被拒绝，因此契约统一使用 `foodmate-agent-command-v1` 这种连字符命名。
2. **数据目录属主。** 镜像里没有 `/home/rocketmq/{store,logs}`，Docker 建命名卷时用 root 创建目录，而进程以 uid 3000 运行。`rocketmq-prepare` 一次性容器负责 `chown`，namesrv/broker 依赖它成功退出后才启动。
3. **`brokerIP1=127.0.0.1`。** Java 与 Python 都跑在宿主机上，NameServer 必须返回宿主机可达的 Broker 地址；填容器 IP 会导致客户端连不上。

Broker 重启后消息保留（`foodmate-rocketmq-broker-store` 命名卷 + `SYNC_FLUSH`）：

```powershell
docker exec foodmate-rocketmq-broker sh -c "/home/rocketmq/rocketmq-5.2.0/bin/mqadmin topicStatus -n foodmate-rocketmq-namesrv:9876 -t foodmate-agent-command-v1"
```

重启前后 `Max Offset` 应保持一致。
