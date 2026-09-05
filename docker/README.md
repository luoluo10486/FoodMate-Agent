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
docker compose --env-file .env -f docker/compose.yml logs -f agent-runtime
```

Java 容器使用 `local` profile，Python 容器默认使用 Redis-backed `stub` 索引；两者都只使用 deterministic 本地逻辑，不调用付费模型或 embedding 服务。应用容器不会自动执行 `script/sql/FoodMate` 迁移，迁移必须按版本人工执行并记录。

容器内 readiness：

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health/readiness
Invoke-WebRequest http://localhost:9002/foodmate/internal/health/ready
```

 Java 容器通过 Compose 网络访问 `agent-runtime:9000`，不应在容器配置中使用宿主机的 `localhost`。Compose 默认将四档 Agent 模型路由设为 `deterministic:local`；需要真实 Chat 时，在被忽略的根目录 `.env` 中显式设置 `FOODMATE_DOCKER_MODEL_TIER_STANDARD/HIGH/EVAL=cloud_primary:<provider-model-id>`，并补齐 `FOODMATE_DOCKER_MODEL_PROVIDER_CLOUD_PRIMARY_*` 端点、API Key 和已审计价格配置。启用真实 SQL Agent 时再设置 `FOODMATE_DOCKER_SQL_PLANNER_MODE=local`，它复用同一套 Chat 路由和价格治理，不需要 SQL 专用 API Key。宿主机的同名非 Docker 变量不会自动进入容器，容器也不会从源码或镜像读取凭据。

应用容器不会自动执行数据库迁移。启动前应确认 V16-V29 已按 `script/sql/FoodMate` 的顺序实际执行，启动后再检查 Java 和 Python readiness，以及应用日志中的 Outbox/Worker 状态。修改 Python 源码后必须重新执行 `up -d --build agent-runtime`，仅重启不会更新镜像内容。停止时使用 `docker compose ... down` 保留数据卷，除非明确需要销毁本地卷并另行确认。

### M3 清理执行门禁

数据库、对象和向量硬删除由 Java 清理执行器负责，但必须同时满足策略表的
`hard_delete_enabled=true`、`FOODMATE_RETENTION_EXECUTION_ENABLED=true` 和
`FOODMATE_RETENTION_EXECUTION_BACKUP_VERIFIED=true`。最后一个开关只能在
`script/sql/FoodMate/backup-restore.ps1` 完成非生产本地数据库备份、隔离库恢复和
`validation.sql` 校验后设置；默认值为 `false`。未完成备份证明时，执行器不会领取
任何清理任务。修改开关后使用 `up -d --force-recreate foodmate`，不要只执行
`restart`。

## M2-1 RAG

默认 `FOODMATE_DOCKER_RAG_MODE=stub`，只使用 Redis 隔离前缀保存确定性关键词索引，不连接 Milvus，也不读取 embedding API Key。Docker Compose 宿主侧统一使用 `FOODMATE_DOCKER_RAG_*`，避免根目录 `.env` 中的非 Docker 配置意外进入容器。

需要验证向量索引业务路径时，使用 `FOODMATE_DOCKER_RAG_MODE=local` 和
`FOODMATE_DOCKER_RAG_EMBEDDING_PROVIDER=deterministic`。此模式使用本地确定性向量并写入 Compose 的 Milvus：

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
$env:FOODMATE_DOCKER_RAG_MODE = "local"
$env:FOODMATE_DOCKER_RAG_EMBEDDING_PROVIDER = "deterministic"
$env:FOODMATE_DOCKER_RAG_MILVUS_URI = "http://milvus:19530"
$env:FOODMATE_DOCKER_RAG_MILVUS_COLLECTION = "foodmate_knowledge_chunks_local"
docker compose --env-file .env -f docker/compose.yml up -d milvus foodmate agent-runtime
```

切换真实 OpenAI-compatible embedding 时，将 `FOODMATE_DOCKER_RAG_EMBEDDING_PROVIDER` 改为 `openai-compatible`，并显式配置 `FOODMATE_DOCKER_RAG_EMBEDDING_BASE_URL`、`FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY`、model、预算和价格版本；缺少任一配置不会回退到 stub 或 deterministic。Python 由 Compose 的 `agent-runtime` 服务启动，Compose 不自动执行数据库迁移。

如果 Docker Desktop 所在网络要求通过出站代理访问 SiliconFlow，可只给
`agent-runtime` 配置 `FOODMATE_DOCKER_HTTP_PROXY` 和
`FOODMATE_DOCKER_HTTPS_PROXY`，并保留 `FOODMATE_DOCKER_NO_PROXY` 中的 Compose
服务名。代理默认留空；不要把代理凭据写入 Git，也不要把代理用于 PostgreSQL、Redis、
RocketMQ、MinIO 或 Milvus 的内部访问。修改后使用 `up -d --force-recreate agent-runtime`，
再执行带 `-ExecuteRequest` 的 smoke 才会验证真实请求。

SiliconFlow 可使用 `BAAI/bge-m3` 或 `Qwen/Qwen3-Embedding-0.6B`。分别设置 `FOODMATE_DOCKER_RAG_EMBEDDING_PROFILE=bge-m3` 或 `qwen3-embedding-0.6b`，并为每个模型使用独立的 `FOODMATE_DOCKER_RAG_MILVUS_COLLECTION`；Embedding 的 `FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY` 必须在被忽略的本地 `.env` 或 Secret Store 中单独显式配置，不能从 Chat provider 变量继承，也不能提交到仓库。两个模型都会按实际返回维度校验 Milvus collection，切换模型时必须切换 collection 并重新索引。

宿主机 smoke 入口默认顺序请求两个 profile，也可以只验证一个 profile：

```powershell
.\script\local\siliconflow-embedding-smoke.ps1 -Profile bge-m3
.\script\local\siliconflow-embedding-smoke.ps1 -Profile qwen3-embedding-0.6b
```

该入口只从当前 PowerShell 进程读取 `FOODMATE_RAG_EMBEDDING_BASE_URL` 和 `FOODMATE_RAG_EMBEDDING_API_KEY`，不读取项目 `.env`，也不接受命令行密钥参数；真实调用前必须使用已轮换的供应商密钥。

当前验证口径（2026-09-02）：D112 使用历史凭据时，BGE 和 Qwen 两个 profile 均返回 1024 维向量；D114 使用当前凭据复验时，两个请求均返回 HTTP 401 `Unauthorized`，响应摘要为 `Api key is invalid`。因此当前 Docker Runtime 的启动、配置、readiness 和 profile 隔离已验证，但当前凭据下的真实 Embedding 调用尚未成功；应在 SiliconFlow 控制台确认或轮换密钥后再复验。历史证据与当前凭据不能混为一个成功结论。

#### Docker 真实密钥注入与重建

根目录 `.env` 只作为 Compose 的非敏感配置输入。真实密钥不要写入仓库或复制到命令行参数；在 Windows 本地联调时，可以仅注入当前 PowerShell 进程，Compose 会优先使用进程环境变量：

```powershell
$secureEmbeddingKey = Read-Host "SiliconFlow Embedding API Key" -AsSecureString
$embeddingKeyPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureEmbeddingKey)
try {
    $env:FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($embeddingKeyPtr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($embeddingKeyPtr)
}

docker compose --env-file .env -f docker/compose.yml up -d --build --force-recreate agent-runtime
Invoke-WebRequest http://localhost:9002/foodmate/internal/health/ready

Remove-Item Env:FOODMATE_DOCKER_RAG_EMBEDDING_API_KEY
```

Chat 密钥使用独立的 `FOODMATE_DOCKER_MODEL_PROVIDER_CLOUD_PRIMARY_API_KEY` 注入，不能复用 Embedding 密钥。修改 `.env` 或进程变量后必须重新创建 `agent-runtime`；单独 `restart` 不会更新容器环境变量。可用 `docker inspect foodmate-agent-runtime` 检查 `FOODMATE_RAG_MODE`、profile、model 和 collection，但不要输出任何 `*_API_KEY`、密码或令牌。

真实业务闭环的付费调用必须单独开启 `FOODMATE_DOCKER_PAID_EXECUTION_ENABLED=true`，并保持最多 4 个场景、累计 5 CNY、无 fallback/自动重试和云 provider 门禁。可先运行 `script/local/paid-cloud-preflight.ps1 -Scenario rag` 做非付费配置校验；只有显式传入 `-ExecutePaid` 才会在当前脚本进程内启用容器门禁并重建 Runtime。预检只输出模型、状态和配置是否存在，不输出密钥，也不等同于业务链路完成。

#### 真实 RAG 业务闭环验收入口

`script/local/real-rag-e2e.ps1` 是受限的 R1 业务验收入口，不承担压测、长稳、组件重启、ACK 丢失或重复投递故障矩阵。无参数执行只做 Compose、Java/Python readiness、真实 RAG 配置和付费门禁预检，不登录、不上传文件、不调用云模型：

```powershell
.\script\local\real-rag-e2e.ps1
```

显式执行真实付费闭环前，管理员账号和密码只能注入当前 PowerShell 进程；不要作为脚本参数、命令行参数或日志内容传递。脚本会使用 `.env` 中已经配置的 Chat/Embedding Key，自动将 Docker 运行时置为真实 local 模式，单轮最多 1 个场景、预算上限 5 CNY、禁止自动重试，并在默认结束路径软删除本轮文档和会话：

```powershell
$env:FOODMATE_E2E_ADMIN_USERNAME = Read-Host "FoodMate admin username"
$securePassword = Read-Host "FoodMate admin password" -AsSecureString
$passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:FOODMATE_E2E_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
}
try {
    .\script\local\real-rag-e2e.ps1 -ExecutePaid
} finally {
    Remove-Item Env:FOODMATE_E2E_ADMIN_USERNAME,Env:FOODMATE_E2E_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
```

入口会验证批次上传、Java Index Outbox/RocketMQ、Python 解析和真实 Embedding/Milvus、Java 结果回写、批次 SSE、显式发布、公共检索、真实 Chat AgentRun、`run.completed` 引用、SSE `Last-Event-ID` 回放，以及下线后的不可检索。默认使用 3 份隔离 Markdown 样例；也可通过 `-DocumentPaths` 传入 1 至 5 个不超过 20 MB 的 PDF/DOCX/Markdown/TXT 文件。脚本只输出脱敏状态、模型标识、数量和稳定错误摘要，不输出 API Key、密码、Prompt、回答、原文、对象键或供应商原始响应。

两个 Embedding profile 是互斥的运行配置，不会混写同一 collection。切换时先选择一个 profile 和对应 collection，重新创建 Runtime，并对需要检索的文档重新索引；旧 collection 可保留用于回滚或在确认无引用后单独清理。

#### 真实餐食计划业务闭环验收入口

`script/local/real-meal-plan-e2e.ps1` 是受限的 R3 业务验收入口。无参数执行只检查 Compose、Java/Python readiness、Docker Chat 的 `high` 档云路由和付费门禁，不登录、不创建 Run、不调用模型：

```powershell
.\script\local\real-meal-plan-e2e.ps1
```

真实执行必须显式传入 `-ExecutePaid`，管理员账号和密码只能从当前 PowerShell 进程的
`FOODMATE_E2E_ADMIN_USERNAME`、`FOODMATE_E2E_ADMIN_PASSWORD` 读取。入口固定为一个
`meal-plan` 场景，累计预算上限 5 CNY，关闭 fallback 和自动重试。它通过真实
`POST /api/chat/runs` 创建 AgentRun，然后读取
`GET /api/agent-runs/{runId}/stream`，断言云模型生成 `run.clarification_requested`、
`meal_plan.save_plan` 确认请求，再以同一份 `{"plan": ...}` 调用 approval confirm/execute。

```powershell
$env:FOODMATE_E2E_ADMIN_USERNAME = Read-Host "FoodMate admin username"
$securePassword = Read-Host "FoodMate admin password" -AsSecureString
$passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:FOODMATE_E2E_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
}
try {
    .\script\local\real-meal-plan-e2e.ps1 -ExecutePaid
} finally {
    Remove-Item Env:FOODMATE_E2E_ADMIN_USERNAME,Env:FOODMATE_E2E_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
```

成功条件是 Java 返回 `saved` 的 `meal_plan`、已绑定的购物清单，以及从同一
AgentRun 的 SSE 回放得到唯一 `run.completed`，终态中的计划 ID 必须与 Java 执行结果一致。
默认只软删除本轮计划和自动创建的会话；`-KeepData` 仅用于明确需要保留业务证据的单轮执行。
入口是业务正确性检查，不执行压测、组件重启、ACK/重复投递故障注入、备份恢复或生产操作。

#### 真实饮食记录业务闭环验收入口

`script/local/real-food-log-e2e.ps1` 是受限的 R2 业务验收入口。无参数执行只检查 Compose、Java/Python readiness、Docker Chat 的 `high` 档云路由和付费门禁，不登录、不创建 Run、不调用模型：

```powershell
.\script\local\real-food-log-e2e.ps1
```

真实执行必须显式传入 `-ExecutePaid`，管理员凭据只能从当前 PowerShell 进程的
`FOODMATE_E2E_ADMIN_USERNAME`、`FOODMATE_E2E_ADMIN_PASSWORD` 读取。入口固定为一个
`food-log` 场景，累计预算上限 5 CNY，关闭 fallback 和自动重试。脚本通过真实
`POST /api/chat/runs` 创建 AgentRun，读取
`GET /api/agent-runs/{runId}/stream`，断言云模型生成 `food_log_writer` 的
`run.clarification_requested`，再将安全的餐食时间、餐次、备注和食材参数同时传给
approval confirm/execute。

成功条件是 Java 返回绑定当前 Run 的 `food_log`，至少一条食材营养状态为 `matched`，并从同一 Run 的 SSE 回放得到唯一 `run.completed`，终态中的记录 ID 必须与 Java 执行结果一致。默认只软删除本轮饮食记录和自动创建的会话；`-KeepData` 仅用于明确需要保留业务证据的单轮执行。入口是业务正确性检查，不执行压测、组件重启、ACK/重复投递故障注入、备份恢复或生产操作。

#### 真实 SQL Agent 业务闭环验收入口

`script/local/real-sql-agent-e2e.ps1` 是受限的 R4 只读 SQL Agent 验收入口。无参数执行只检查 Compose 配置、Java/Python readiness、SQL Planner/Composer 的真实云路由和付费门禁，不登录、不创建 Run、不调用模型：

```powershell
.\script\local\real-sql-agent-e2e.ps1
```

真实执行必须显式传入 `-ExecutePaid`。管理员账号和密码只能从当前 PowerShell 进程的
`FOODMATE_E2E_ADMIN_USERNAME`、`FOODMATE_E2E_ADMIN_PASSWORD` 读取，不能作为脚本参数或写入日志。脚本固定一个 `sql-agent` 场景、累计预算上限 5 CNY、要求 `cloud_primary`、关闭 fallback 和自动重试；Chat Key 继续由本地忽略的 `.env` 供 Compose 注入：

```powershell
$env:FOODMATE_E2E_ADMIN_USERNAME = Read-Host "FoodMate admin username"
$securePassword = Read-Host "FoodMate admin password" -AsSecureString
$passwordPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:FOODMATE_E2E_ADMIN_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPtr)
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPtr)
}
try {
    .\script\local\real-sql-agent-e2e.ps1 -ExecutePaid
} finally {
    Remove-Item Env:FOODMATE_E2E_ADMIN_USERNAME,Env:FOODMATE_E2E_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
```

入口验证真实 Chat -> SQL Planner -> `time_parser`/`database_query` -> Java Schema/AST/用户范围/只读 Guard -> PostgreSQL SQL 审计 -> Composer -> `run.completed`/SSE，并使用 `Last-Event-ID` 回放终态。输出只包含脱敏状态、模型标识、工具名、审计计数和 SSE 数量，不包含 API Key、密码、Prompt、完整回答或 SQL 原文；默认只软删除本轮会话。该入口只验收业务正确性，不执行压测、组件重启、ACK/重复投递故障注入、备份恢复或生产操作。

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
