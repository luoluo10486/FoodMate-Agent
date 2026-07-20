# FoodMate 数据库变更说明

## V1 基线

- 脚本：`baseline/V1__init_core_schema.sql`
- 回滚：`rollback/R1__drop_core_schema.sql`
- 范围：29 张核心业务、认证和运行时表，以及索引、约束和中文注释。
- 执行方式：人工执行；Java profile 不自动运行 Flyway。
- 验收：执行 `validation.sql`，并保留输出记录。

## 增量变更规则

变更编号、影响表、锁与停机要求、备份位置、执行窗口、校验结果和回滚结论必须在 `EXECUTION_RECORD.md` 中登记。已执行版本禁止修改原文件。
