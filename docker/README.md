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
