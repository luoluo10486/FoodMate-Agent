# FoodMate 数据库变更说明

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
