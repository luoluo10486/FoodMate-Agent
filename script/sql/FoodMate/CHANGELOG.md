# FoodMate 数据库变更说明

## 2026-08-13 M1-5 第一切片复核

- V13/V14 未在本轮重复执行；当前本地 `FoodMate` 数据库已存在对应结构。
- 只读校验确认 `food_logs`、`food_log_items`、`nutrition_foods`、`nutrition_unit_conversions`、`approval_requests` 及 V14 幂等字段/索引存在，`food_logs` 旧 JSON 字段已移除。
- 当前数据量：`food_logs=1`、`food_log_items=1`、`nutrition_foods=0`、`nutrition_unit_conversions=0`、`approval_requests=1`。营养目录 seed 尚未导入。
- 审批审计已验证 `approval.propose`、`approval.confirm`、`approval.execute` 各 1 条成功记录。
- 详细复核记录见 [`EXECUTION_RECORD.md`](./EXECUTION_RECORD.md)。

## V2__m1_account_and_privacy.sql（待人工执行）

- 变更角色约束，加入 `superadmin`。
- 增加密码重置令牌、个人数据导出任务和账号注销清理任务表；只保存哈希、对象键和任务元数据，不保存明文凭据或文件二进制。
- 调整头像对象元数据，使私有 MinIO 签名 URL 不需要持久化。
- 增加认证会话按用户和有效状态查询索引。
- 执行前必须完成 `BACKUP_AND_ROLLBACK.md` 中的备份和恢复演练；执行后运行 `validation/V2__m1_account_and_privacy_validation.sql` 并填写 `EXECUTION_RECORD.md`。
- Flyway 保持关闭；本脚本不得由应用启动自动执行。

## V1 基线

- 脚本：`baseline/V1__init_core_schema.sql`
- 回滚：`rollback/R1__drop_core_schema.sql`
- 范围：29 张核心业务、认证和运行时表，以及索引、约束和中文注释。
- 执行方式：人工执行；Java profile 不自动运行 Flyway。
- 验收：执行 `validation.sql`，并保留输出记录。

## 增量变更规则

变更编号、影响表、锁与停机要求、备份位置、执行窗口、校验结果和回滚结论必须在 `EXECUTION_RECORD.md` 中登记。已执行版本禁止修改原文件。

## V13__m1_5_food_log_nutrition_approval.sql（待人工执行）

- 调整 `food_logs` 为餐次主表：新增 `agent_run_id`、`idempotency_key`、`revision`，删除旧 `items_json`/`nutrition_json`。
- 新增 `food_log_items`、`nutrition_foods`、`nutrition_unit_conversions`、`approval_requests`，并建立外键、精度约束、状态约束、软删除字段和幂等索引。
- 不写入任何营养目录种子数值；目录数据必须另行核实官方来源、版本和许可证后再由人工 SQL 导入。
- 执行前置条件：`food_logs` 必须为空；否则脚本主动失败，不允许静默丢弃历史 JSON。
- 校验：`validation/V13__m1_5_food_log_nutrition_approval_validation.sql`。
- 回滚：`rollback/R13__m1_5_food_log_nutrition_approval.sql`；仅限 V13 新表均为空的开发数据库。

## V14__m1_5_operation_idempotency.sql（待人工执行）

- 为 `operation_audits` 增加 `idempotency_key`、`parameters_digest`。
- 增加操作者 + 幂等键唯一索引，统一覆盖饮食记录创建、删除和恢复。
- 校验：`validation/V14__m1_5_operation_idempotency_validation.sql`。
- 回滚：`rollback/R14__m1_5_operation_idempotency.sql`。
