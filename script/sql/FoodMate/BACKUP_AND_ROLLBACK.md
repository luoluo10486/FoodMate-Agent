# 备份与回滚前置条件

## 执行前

1. 确认目标数据库名称为 `FoodMate`，连接账号具备所需 DDL 权限，且不是生产数据的本地复用副本。
2. 在维护窗口执行逻辑备份，例如 `pg_dump --format=custom --file=FoodMate_<UTC时间>.dump FoodMate`；记录文件路径、大小和 SHA-256。
3. 在备份副本上先演练恢复，并确认 `validation.sql` 能读到预期表、索引和约束。

本项目的本地 PostgreSQL 默认运行在 Docker 中；宿主机未安装 PostgreSQL 客户端时，使用显式容器参数完成同样流程：

```powershell
.\script\sql\FoodMate\backup-restore.ps1 `
  -DatabaseName FoodMate `
  -Username postgres `
  -DockerContainer foodmate-postgres `
  -BackupFile FoodMate_<UTC时间>.dump `
  -RestoreDatabaseName FoodMateRestore_<UTC时间> `
  -Execute -RunValidation
```

恢复库名称必须与源库不同，脚本拒绝生产/预发布命名、覆盖已有备份文件和覆盖已有恢复库。
验证完成后，如确认恢复库只是本轮隔离演练产物，可额外使用
`-DropRestoreDatabaseAfterValidation` 清理恢复库；源库和备份文件不会被该开关删除。
备份文件保存在 `script/sql/FoodMate/backups/`，已被 Git 忽略，必须按组织的备份保管策略另行保存。
4. 记录当前脚本版本、数据库 PostgreSQL 版本、执行人和开始时间。

## 回滚前置条件

- 仅当变更说明明确允许回滚、没有依赖新增结构的已提交业务数据，且已完成备份校验时，才允许执行回滚脚本。
- V1 基线回滚会删除全部 FoodMate 表及数据，只能用于废弃环境或经确认的灾备恢复，不得用于生产故障处置。
- 增量变更优先使用对应的、经评审的反向 SQL；禁止手工修改 Flyway history 或直接删除迁移记录。
- 回滚后重新执行 `validation.sql`，在 `EXECUTION_RECORD.md` 追加回滚人、时间、结果和恢复验证。
