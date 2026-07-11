# Final Dispatch Fix Report

日期：2026-07-11

## 结论

最终残余 Important 已在文档层闭合：Java -> Python RunCommand 由独立 `runtime_dispatch_outbox` 可靠派发。AgentRun、dispatch 和 immutable RunCommand outbox 同事务提交，网络发送事务外执行；publisher 通过 lease/CAS 启动恢复和多实例单领取，Python 通过 `dispatch_id/request_hash` 稳定去重，ACK 不确定时重放同一 envelope，不产生双执行。

本次未修改代码、迁移或新增 wire 类型。跨运行时仍只使用既有 RunCommand、RuntimeError 和既有 HTTP 成功语义。

## 修改范围

- `docxs/数据/V2双运行时迁移设计.md`：新增 `runtime_dispatch_outbox` 字段、唯一/FK/CHECK/索引、不可变字段、事务边界、lease/CAS 状态机、过期/冲突/重新派发收敛，并纳入阶段 1、阶段 4、回滚和 Testcontainers 门禁。
- `docxs/契约/双运行时内部契约V1.md`：固定持久化 RunCommand 原 envelope 重放规则、Python 稳定接受语义和“不新增 wire 类型”边界。
- `docxs/实现/Java运行时客户端实现.md`：明确 Application/Worker/Infra 与无数据库 Runtime Client 的职责，补齐 publisher 算法和故障测试。
- `docxs/实现/运行状态与事件接入.md`：补齐 dispatch outbox 的事务、恢复、状态冲突和 AgentRun/审计收敛。
- `docxs/运维/部署与回滚.md`：阶段 4 增加 dispatch outbox 硬门禁；drain 期间保持 publisher 运行，直到派发关闭且队列全部 `delivered/expired/failed`。
- `docxs/测试/测试策略.md`：加入提交后发送前崩溃、发送后 ACK 前崩溃、lease owner 崩溃、多实例竞争、deadline 过期、hash 冲突和 superseded 不发送，统一验证无双执行。
- `docxs/文档索引.md`：只更新既有行描述，未新增索引行。

## 验证结果

- Markdown 文件扫描：53 个。
- JSON 围栏解析：84 个，全部通过。
- Markdown 围栏闭合：通过。
- 相对链接存在性：通过。
- dispatch outbox 必需字段、五种状态、事务/崩溃/竞争/过期/冲突/superseded 场景扫描：31/31 命中。
- 禁止的新接受类 wire 名称扫描：未发现；完全复用 RunCommand/RuntimeError。
- 最终提交后执行 `git diff --check ee5bd9a..HEAD`，实际结果为退出码 0、无错误输出。

本报告记录的是目标设计闭合与文档验证结果，不表示 `runtime_dispatch_outbox`、publisher、Python 幂等存储或 Testcontainers fixture 已在代码中实现。
