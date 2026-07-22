# FoodMate 数据库人工执行记录

> 模板记录。实际执行后必须由执行人填写，不能用应用启动日志替代。

## V2 临时 PostgreSQL 演练（非目标库）

| 字段 | 内容 |
|---|---|
| 数据库 | 临时 Docker PostgreSQL 16，数据库 `FoodMate` |
| 环境 | local SQL rehearsal，仅验证脚本，不是用户现有 PostgreSQL |
| 脚本版本 | `V1__init_core_schema.sql` + `V2__m1_account_and_privacy.sql` |
| 执行时间（UTC） | 2026-07-22T13:39:31Z |
| 执行结果 | 基线成功；V2 首次成功；V2 重复执行成功；V2 validation 成功 |
| 备份 | 未对用户目标库执行 DDL，故未创建目标库备份 |
| 回滚结论 | 临时容器验证后已删除；目标库未执行、无需回滚 |

> 该演练不构成目标 `FoodMate` 数据库的正式执行证据。正式执行前仍须按 `BACKUP_AND_ROLLBACK.md` 完成备份、恢复演练和执行人登记。

| 字段 | 内容 |
|---|---|
| 数据库 | `FoodMate` |
| 环境 | 待填写：local/dev/staging/prod |
| 脚本版本 | 待填写：V1 或 Vn |
| 执行人 | 待填写 |
| 执行时间（UTC） | 待填写 |
| 备份位置与校验和 | 待填写 |
| 执行命令/客户端版本 | 待填写 |
| 执行结果 | 待填写：成功/失败 |
| `validation.sql` 结果 | 待填写 |
| 回滚结论 | 待填写：未执行/已执行及原因 |

执行失败时，保留完整错误、已执行语句范围和恢复动作；不得覆盖原记录。
