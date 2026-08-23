# FoodMate Figma 前端像素级验收报告

更新时间：2026-08-23

## 1. 结论

本报告记录两类不同验收结果：

1. Figma 文件内部结构、组件系统、Prototype 和画板截图回读已完成。
2. 前端代码与 Figma 画板的自动化像素差异已覆盖 105 个已建立映射的页面/状态，105 个结果均为 `DIFF_REVIEW`，不能标记为像素级通过。

因此当前不能宣称“Figma 105 张画板已全部像素级通过”。已经完成的是可复核的 Figma 全量结构验收、105 个画板的路由/状态映射、差异证据收集，以及运行时几何、可见文字、DPR 和 105/105 人工视觉复核登记；由于仍存在可见差异，结果继续保留为 `DIFF_REVIEW`。

## 2. Figma 文件内部验收

来源文件：[Fintech dashboard Community](https://www.figma.com/design/MX18RZCfAmgprNzxItkHUH/Fintech-dashboard--Community-?node-id=0-1)

| 项目 | 结果 |
|---|---:|
| 文件 key | `MX18RZCfAmgprNzxItkHUH` |
| `🎨 :: Design` 顶层画板 | 105 |
| Design 页递归节点 | 19,985 |
| Prototype reaction | 1,940 |
| 无效 Prototype 目标 | 0 |
| Figma 画板截图请求 | 105/105 成功 |
| User Component Set | 24 |
| Admin Component Set | 14 |
| Foundations Variables 集合 | 5 |
| 已回读本地样式 | 8 |

实际设计系统页面为 `01 Foundations`、`02 Components - User`、`03 Components - Admin`。`🎨 :: Design` 页面自身没有 Component Set，这不影响全文件 User/Admin 组件集已经建立。

## 3. 像素差异方法

脚本：[png-diff.mjs](../../foodmate-ui/scripts/png-diff.mjs)

脚本比较 RGB/RGBA PNG 的同尺寸像素，输出：不同像素数、差异比例、平均绝对误差、RMSE 和最大通道差异。浏览器截图在固定视口采集，Figma PNG 与浏览器 PNG 尺寸不一致时先记录尺寸问题，不强行比较。

当前验收口径：

- `PASS`：尺寸一致，且差异结果与人工截图检查均满足当前页面的验收阈值。
- `DIFF_REVIEW`：尺寸一致但存在需要人工复核或继续修正的像素差异。
- `SIZE_MISMATCH`：尺寸不同，不能作为像素结论。
- `UNMAPPED`：Figma 画板尚未映射到独立前端路由和状态。

## 4. 已映射页面结果

以下为代表性页面结果；105 项完整字段、路由、query 状态、视口、PNG 路径和 diff 锚点以 [`figma-105-mapping.json`](../../foodmate-ui/.qa/figma-pixel-acceptance/figma-105-mapping.json) 为准。结果来自 2026-08-18 运行的 `generate-figma-105-diff.mjs`，认证页使用 `1440×900`，其它画板按各自 Figma 目标尺寸记录。

| 页面/状态 | Figma 节点 | 尺寸 | 差异比例 | RMSE | 结论 |
|---|---|---:|---:|---:|---|
| Workspace Home | `640:256` | 1440×1024 | 42.26% | 20.09 | `DIFF_REVIEW` |
| Agent Chat | `640:428` | 1440×1024 | 24.19% | 16.51 | `DIFF_REVIEW` |
| Diet Records | `640:588` | 1440×1024 | 37.94% | 17.38 | `DIFF_REVIEW` |
| Intake Analysis | `640:773` | 1440×1024 | 28.07% | 18.50 | `DIFF_REVIEW` |
| Meal Planning | `640:901` | 1440×1024 | 23.83% | 16.80 | `DIFF_REVIEW` |
| Admin Overview | `995:977` | 1440×1024 | 33.57% | 19.58 | `DIFF_REVIEW` |
| Admin Tool Registry | `692:3847` | 1440×1024 | 21.43% | 18.84 | `DIFF_REVIEW` |
| Admin Deleted Resources | `692:4104` | 1440×1024 | 79.34% | 21.09 | `DIFF_REVIEW` |
| User Knowledge Empty | `795:786` | 1440×1024 | 21.91% | 12.25 | `DIFF_REVIEW` |
| User Knowledge Default | `795:838` | 1180×1024 主区域 | 70.62% | 143.65 | `DIFF_REVIEW` |
| User Knowledge Search Failed | `795:968` | 1440×1024 | 22.41% | 12.76 | `DIFF_REVIEW` |
| User Knowledge Source Unavailable | `795:1151` | 1440×1024 | 22.62% | 12.83 | `DIFF_REVIEW` |
| Profile Basic | `806:1119` | 1440×1024 | 67.46% | 20.74 | `DIFF_REVIEW` |
| Profile Memories | `806:1281` | 1440×1024 | 50.35% | 23.05 | `DIFF_REVIEW` |
| Profile Security | `806:1445` | 1440×1024 | 60.25% | 19.68 | `DIFF_REVIEW` |
| Profile Privacy | `806:1585` | 1440×1024 | 37.09% | 17.96 | `DIFF_REVIEW` |
| Login | `647:214` | 1440×900 | 99.19% | 7.50 | `DIFF_REVIEW` |
| Admin User Detail | `801:215` | 1440×1024 | 22.36% | 17.62 | `DIFF_REVIEW` |

Login 的高差异比例主要来自大面积抗锯齿、透明叠加和斜向背景边界；几何已按 Figma 读取结果对齐：表单 `400×471`，位置 `x=490,y=214.5`，品牌区 `163px`，字段区 `156px`，按钮 `52px`，分隔区 `56px`，注册行 `44px`。该页仍保留 `DIFF_REVIEW`，不将人工“基本重合”写成自动化 PASS。

证据目录：[`.qa/figma-pixel-acceptance`](../../foodmate-ui/.qa/figma-pixel-acceptance)

## 5. 全量画板映射

Figma Design 页共有 105 张顶层画板。本轮已为 105 张画板建立独立前端路由或 query 状态、同尺寸浏览器视口和 PNG 证据；当前 `UNMAPPED=0`、`SIZE_MISMATCH=0`。完整逐项清单不在本报告重复展开，以映射 JSON 作为机器可读的唯一清单来源。

每一项均记录 Figma 节点 ID、画板名称、画板尺寸、前端路由、query 状态、浏览器视口、Figma PNG、浏览器 PNG、diff JSON 锚点和人工复核结论。2026-08-18 基线运行时检查曾记录 `geometryPass=105/105`、`textPass=105/105`、`dprPass=105/105`；2026-08-22 Chat 历史页当前版本复核因 in-app 浏览器实际 DPR 为 `1.25`，更新后的运行时检查为 `geometryPass=105/105`、`textPass=105/105`、`dprPass=102/105`。因此仍不能将 `DIFF_REVIEW` 改为 `PASS`。

## 6. 其它检查

- 页面级横向溢出检查：已覆盖多个桌面和移动视口，当前记录为通过；这只证明没有页面级横向溢出，不等于像素级通过。
- Figma 可见文字边界：此前全文件扫描未发现越界或零尺寸文本；浏览器运行时的 105 项可见文本边界检查也均通过。
- Prototype：所有带目标的 reaction 目标均有效；该结果不等于浏览器端每条交互已经真实接通。
- 字体：生产构建已使用 `@fontsource/noto-sans-sc`、`@fontsource/space-mono` 和 `@fontsource/montserrat` 的真实 woff2 产物。
- iconfont：仍为 `BLOCKED`，因为实体字体包、CSS 映射、来源和授权尚未提供。

## 7. 后续验收门槛

1. 对 105 个已映射画板逐项完成几何、文字、颜色、状态和像素差异复核。
2. 使用同一视口、同一 DPR、同一字体加载完成条件补采或修正存在差异的浏览器截图。
3. 只有在自动 diff、几何检查、文字检查和人工复核都满足时，才将单页从 `DIFF_REVIEW` 改为 `PASS`。
4. iconfont 资源登记必须在收到真实包、CSS、来源和许可证后单独关闭，不能用 Lucide 或虚构字体替代。

## 8. 餐食规划状态补充验收

本轮补充餐食规划 Loading、Empty、Error 三种前端状态的独立映射。Figma 来源节点均为完整 `1440×1024` 画板；浏览器入口复用 `/planning?state=`，只用于复现设计状态，不代表真实计划数据或任务闭环已经完成。

| 状态 | Figma 节点 | 前端入口 | Figma 证据 | 浏览器证据 | 结果 |
|---|---|---|---|---|---|
| Loading | `692:2256` | `/planning?state=loading` | `meal-planning-loading-figma.png` | `meal-planning-loading-browser-stable.png` / `meal-planning-loading-browser-stable-rgba.png` | `DIFF_REVIEW` |
| Empty | `692:2446` | `/planning?state=empty` | `meal-planning-empty-figma.png` | `meal-planning-empty-browser-stable.png` / `meal-planning-empty-browser-stable-rgba.png` | `DIFF_REVIEW` |
| Error | `692:2542` | `/planning?state=error` | `meal-planning-error-figma.png` | `meal-planning-error-browser-stable.png` / `meal-planning-error-browser-stable-rgba.png` | `DIFF_REVIEW` |

| 状态 | 尺寸 | 差异比例 | RMSE | 结论 |
|---|---:|---:|---:|---|
| Loading | 1440×1024 | 26.74% | 13.19 | `DIFF_REVIEW` |
| Empty | 1440×1024 | 16.98% | 16.88 | `DIFF_REVIEW` |
| Error | 1440×1024 | 17.81% | 16.50 | `DIFF_REVIEW` |

三个状态均确认 `document.body.scrollWidth === window.innerWidth`。Empty 的“创建首个规划方案”已实际进入 `/chat?prompt=请为我创建本周餐食规划`；Error 的“重新加载”已实际恢复 `/planning` 默认态。这些是前端状态交互证据，不等价于真实计划数据、生成任务或后端错误闭环。

## 9. 餐食规划流程状态补充验收

本轮继续补齐已存在前端入口的餐食规划流程状态。所有画板与浏览器截图均为 `1440×1024`；`-rgba.png` 是浏览器 JPEG 证据的 RGBA 归一化副本，供 `png-diff.mjs` 使用。

| 状态 | Figma 节点 | 前端入口 | Figma 证据 | 浏览器证据 | 差异比例 | RMSE | 结果 |
|---|---|---|---|---|---:|---:|---|
| 向导步骤 1 | `692:2801` | `/planning?state=wizard-step1` | `meal-plan-wizard-step1-figma.png` | `meal-plan-wizard-step1-browser-stable-rgba.png` | 40.49% | 21.93 | `DIFF_REVIEW` |
| 向导步骤 2 | `692:2934` | `/planning?state=wizard-step2` | `meal-plan-wizard-step2-figma.png` | `meal-plan-wizard-step2-browser-stable-rgba.png` | 42.86% | 22.56 | `DIFF_REVIEW` |
| 向导步骤 3 | `692:3078` | `/planning?state=wizard-step3` | `meal-plan-wizard-step3-figma.png` | `meal-plan-wizard-step3-browser-stable-rgba.png` | 43.15% | 23.92 | `DIFF_REVIEW` |
| 冲突解决 | `692:3375` | `/planning?state=conflict` | `meal-plan-conflict-figma.png` | `meal-plan-conflict-browser-stable-rgba.png` | 37.28% | 25.12 | `DIFF_REVIEW` |
| 购物清单 | `692:3569` | `/planning?state=shopping-list` | `meal-plan-shopping-list-figma.png` | `meal-plan-shopping-list-browser-stable-rgba.png` | 24.35% | 17.23 | `DIFF_REVIEW` |
| 生成中 | `692:3746` | `/planning?state=generating` | `meal-plan-generating-figma.png` | `meal-plan-generating-browser-stable-rgba.png` | 13.69% | 16.84 | `DIFF_REVIEW` |
| 计划列表 | `692:2662` | `/planning?state=list` | `meal-plan-list-figma.png` | `meal-plan-list-browser-current-rgba.png` | 26.1032% | 19.40 | `DIFF_REVIEW` |

浏览器 smoke 已实际确认：向导步骤推进和取消生成、冲突方案应用、购物清单初始采购数量及导出反馈均可操作；七个入口均无页面级横向溢出。流程 fixture 只复现前端设计状态，不代表真实餐食生成、冲突解决、购物清单持久化或异步任务后端闭环完成。

## 10. Knowledge 状态补充验收

本轮补充了 Knowledge 默认态、检索失败和来源不可用三种前端状态的独立浏览器证据。Figma 结构依据为 `795:838`、`795:968`、`795:1145`、`795:1151` 和 `795:1328`；状态卡在完整画板中的绝对位置均为 `x=550,y=300`、`600×260`，空态仍使用已有 `560×220` 画板。

| 状态 | Figma 证据 | 浏览器证据 | 结果 |
|---|---|---|---|
| 默认态 | `user-knowledge-default-figma-latest.png` | `user-knowledge-default-browser-rgba.png` | `DIFF_REVIEW` |
| 检索失败 | `user-knowledge-search-failed-figma-latest.png` | `user-knowledge-search-failed-browser-full-rgba.png` | `DIFF_REVIEW` |
| 来源不可用 | `user-knowledge-source-unavailable-figma-latest.png` | `user-knowledge-source-unavailable-browser-full-rgba.png` | `DIFF_REVIEW` |

默认态 Figma 节点本身是主区域 `1180×1024`，因此浏览器证据按 `x=260` 裁剪后比较；两个状态画板使用完整 `1440×1024` 截图。三组结果均使用 `scripts/png-diff.mjs`，没有将视觉接近写成 `PASS`。状态层的半透明遮罩、色条、状态标签、标题、技术字段和重试入口均已通过截图人工复核。

本轮只补齐前端状态映射和像素差异证据，不代表真实 RAG 检索、文档导入、ACL 过滤、引用详情接口或 iconfont 实体资源已经完成。

## 11. Admin User Detail 补充验收

本轮完成 Figma `801:215` 到 `/admin/users` 的独立前端映射。Figma 画板关键几何为：侧栏 `260px`、顶栏 `64px`、左侧用户列表 `x=284,y=88,w=692,h=912`、右侧详情 `x=996,y=88,w=420,h=912`；列表为 4 行 `60px`，详情卡内边距 `20px`，详情说明卡位于卡内 `x=19,y=454,w=380,h=220`。

前端 mock fixture 按 Figma 示例登记 `usr_098a1`、`usr_112b9`、`usr_774x2`、`usr_889d4`，包含角色、状态、邮箱、活跃会话数和选中用户详情。详情 Tab 顺序为 `资料`、`饮食`、`登录会话`、`历史`、`业务会话`；状态变更和撤销会话继续复用 Admin 页面已有的二次确认、提交中、成功/失败和审计状态机。真实模式仍调用 `/api/admin/users`、用户状态 PATCH 和撤销全部会话 POST，不替换真实响应。

证据文件：

- Figma：`admin-user-detail-figma.png`
- 浏览器原始截图：`admin-user-detail-browser.png`
- RGBA 归一化截图：`admin-user-detail-browser-rgba.png`
- diff：`1440×1024`，差异比例 `22.36%`，RMSE `17.62`，结论 `DIFF_REVIEW`

历史首轮浏览器验证曾为 `DPR 1.25`，因此当时 diff 使用 RGBA 归一化副本；2026-08-18 已按 `DPR 1` 重新采集并纳入当前 105 项运行时复核。`重置凭证` 当前只有明确的未接入提示，不执行伪造请求。

## 12. Admin Operation Status 补充验收

本轮完成 Figma 工具注册表五个操作状态节点到 `/admin/tools?tab=registry` 的代码映射：

| 状态 | Figma 节点 | 前端行为 |
|---|---|---|
| 无权限 | `692:4319` | Operator 顶层信息横幅，写操作按钮锁定 |
| 确认操作 | `692:4539` | `480px` 确认弹窗、影响资源说明、取消/确认 |
| 提交中 | `692:4766` | 保持确认标题、`4px` 红色进度条、禁用按钮和同步文案 |
| 成功 | `692:4995` | 顶层成功横幅，列表状态局部更新 |
| 失败 | `692:5207` | 错误原因、集群未响应说明、错误码、请求 ID、关闭/重试 |

浏览器已实际检查默认注册表、配置详情后的确认弹窗、提交中弹窗和成功横幅；默认状态下列表、统计卡、筛选和分页保持可见，提交中状态没有页面级横向溢出或意外重叠。证据截图保存在 `.qa/figma-pixel-acceptance/admin-operation-status-*.png`。

本轮状态截图是浏览器行为证据，不等价于五个节点的自动化像素 PASS。Figma 五个状态与浏览器截图仍需在相同 DPR、字体加载完成条件下独立运行 `png-diff.mjs` 后，才能更新为单状态 `PASS` 或 `DIFF_REVIEW`。

## 13. 认证页面与异常状态代码迁移

本轮按 Figma 实际节点补齐认证页面代码和状态入口，视觉来源仍为 Figma，不使用旧前端样式反推。

| 页面/状态 | Figma 节点 | 前端入口 | 浏览器证据 | 像素结论 |
|---|---|---|---|---|
| 注册 | 680:216 | /register | register-page-browser.png、register-page-browser-mobile.png | DIFF_REVIEW |
| 找回密码 | 680:275 | /forgot-password | forgot-password-page-browser.png、forgot-password-page-success-browser.png | DIFF_REVIEW |
| 重置密码 | 680:307 | /reset-password | reset-password-page-browser.png | DIFF_REVIEW |
| 登录默认 | 647:214 | /login | login-default-browser-rgba.png | DIFF_REVIEW |
| 登录提交中 | 680:408 | /login?state=submitting | login-submitting-browser-rgba.png | DIFF_REVIEW |
| 登录字段错误 | 680:445 | /login?state=field-error | login-field-error-browser-rgba.png | DIFF_REVIEW |
| 登录凭证错误 | 680:483 | /login?state=credential-error | login-credential-error-browser-rgba.png | DIFF_REVIEW |
| 账号锁定 | 680:524 | /login?state=account-locked | login-account-locked-browser-rgba.png | DIFF_REVIEW |
| 账号禁用 | 680:564 | /login?state=account-disabled | login-account-disabled-browser-rgba.png | DIFF_REVIEW |
| 服务不可用 | 680:606 | /login?state=service-unavailable | login-service-unavailable-browser-rgba.png | DIFF_REVIEW |
| Token 无效 | 680:738 | /token-status?state=invalid | token-invalid-browser-rgba.png | DIFF_REVIEW |
| Token 过期 | 680:757 | /token-status?state=expired | token-expired-browser-rgba.png | DIFF_REVIEW |
| Token 已使用 | 680:776 | /token-status?state=used | token-used-browser-rgba.png | DIFF_REVIEW |

新增状态在 1440x900、DPR 1 的浏览器截图与 Figma PNG 上运行了 scripts/png-diff.mjs。结果全部保留 DIFF_REVIEW：登录默认 99.19% / RMSE 7.54；提交中 99.92% / 10.83；字段错误 99.98% / 18.92；凭证错误 99.52% / 20.53；账号锁定 100.00% / 31.18；账号禁用 99.99% / 14.02；服务不可用 99.99% / 13.41；Token 无效 99.99% / 9.39；Token 过期 99.99% / 9.67；Token 已使用 99.99% / 10.76。

浏览器行为检查确认：字段错误和凭证错误保留可用登录按钮；提交中、账号锁定、账号禁用和服务不可用禁用登录按钮；Token 三态均能进入找回密码或返回登录。移动注册页的四个输入控件完整位于 390x844 视口内。

本节不代表认证服务的所有异常一定能由 mock 状态触发，也不代表真实后端错误码已全部联调。真实 /api/auth/* 调用仍由 authService.ts 保持；mock 状态 query 只用于设计验收和前端状态复现。

## 40. 2026-08-14 Figma Agent 空态迁移与验收

本轮完成 Figma `agent-empty` 画板到独立前端状态的首轮迁移。视觉来源为 Figma 节点 `687:219`，不从旧前端反推颜色、字体、尺寸、间距或状态。

| 项目 | 结果 |
|---|---|
| Figma 节点/画板 | `687:219` / `1440×1024` |
| 前端入口 | `/chat?state=empty` |
| 空态结构 | 无 Trace 右栏；居中引导、三张推荐问题卡、底部 Composer |
| 桌面几何 | 推荐卡区域约 `720×123`，卡片间距 `16px`；Composer 位于 `y=912`，高度 `112px` |
| Figma 证据 | `chat-agent-empty-figma.png` |
| 浏览器证据 | `chat-agent-empty-browser-1440x1024.png`、`chat-agent-empty-browser-390x844.png` |
| RGBA 证据 | `chat-agent-empty-browser-1440x1024-rgba.png`、`chat-agent-empty-browser-390x844-rgba.png` |
| 自动 diff | 差异比例 `15.7739%`，RMSE `14.7325`，`DIFF_REVIEW` |

- [x] 桌面 `1440×1024` 实测 `document.body.scrollWidth === 1440`；标题、说明、推荐卡和 Composer 无页面级裁切或横向溢出。
- [x] 移动 `390×844` 实测页面宽度与视口一致；三张推荐卡改为单列，Composer 完整位于视口底部。为修复移动端 Composer 被百分比高度裁切的问题，空态页在移动断点使用 `calc(100dvh - 96px)` 的明确内容区高度。
- [x] 点击推荐卡会将真实推荐文案写入 Composer；点击发送后 URL 进入 `/chat?prompt=...`，不伪造后端 Agent 完成结果。
- [x] `ChatPage` 定向测试 `2/2`、`npm run typecheck` 和本次触及文件的 Prettier 检查通过；`git diff --check` 通过。
- [ ] 全量 `npm run format:check` 仍被工作区原有的 8 个未涉及文件阻断：`ClarificationCard.tsx`、`EmptyState.tsx`、`TaskCard.tsx`、`Composer.tsx`、`AdminPage.tsx`、`agentRunService.ts`、`sessionService.ts`、`agent.ts`；本轮未扩大范围改动这些文件。
- [ ] 本轮不关闭 iconfont 实体资源登记；当前仍缺少真实字体包、CSS 映射、来源 URL 和许可证，标准命令图标继续使用 Lucide。shadcn/Radix 基础设施迁移仍作为后续逐页重构的既定前置约束。

## 41. 2026-08-14 Figma Agent Planning 状态迁移与验收

本轮完成 Figma `agent-planning` 画板到 `/chat?state=planning` 的独立状态迁移。视觉来源为 Figma 节点 `687:342`，不从普通对话旧样式反推。

| 项目 | 结果 |
|---|---|
| Figma 节点/画板 | `687:342` / `1440×1024` |
| 前端入口 | `/chat?state=planning` |
| 状态差异 | 隐藏 Trace 右栏；显示 Planning 状态条、用户消息、四行规划步骤卡和红色停止按钮 |
| 核心几何 | 主区 `1180px`；状态条 `45px`；Composer `y=912/h=112`；规划卡约 `x=340/y=237/w=161/h=162` |
| Figma 证据 | `chat-agent-planning-figma.png` |
| 浏览器证据 | `chat-agent-planning-browser-1440x1024.jpg`、`chat-agent-planning-browser-390x844.jpg` |
| RGBA 证据 | `chat-agent-planning-browser-1440x1024-rgba.png`、`chat-agent-planning-browser-390x844-rgba.png` |
| 自动 diff | 差异比例 `14.9956%`，RMSE `14.0682`，`DIFF_REVIEW` |

- [x] 桌面 `1440×1024` 实测页面宽度与 Figma 画板一致，无页面级横向溢出；状态条、用户消息、规划卡和 Composer 均可见。
- [x] 移动 `390×844` 实测 `document.body.scrollWidth === 390`；规划卡自然保留四行内容，Composer 完整可见，停止按钮可用。
- [x] Planning 状态的输入框按设计禁用，停止按钮保留可用状态；本地 query fixture 只复现前端状态，不声明真实 AgentRun 后端完成。
- [x] `ChatPage` 定向测试 `3/3`、`npm run typecheck`、本次触及文件 Prettier 和 `git diff --check` 通过。
- [ ] diff 仍为 `DIFF_REVIEW`，WorkspaceLayout 头像、账号文案等共享壳层差异不能被本状态单独关闭；iconfont 实体资源继续为 `BLOCKED`。

## 42. 2026-08-14 Figma Agent Tool Executing 状态迁移与验收

本轮完成 Figma `agent-tool-executing` 画板到独立前端状态的迁移。视觉来源为 Figma 节点 `687:475`，实现严格读取节点颜色、字体、尺寸、间距和状态语义，不从旧前端反推。

| 项目 | 结果 |
|---|---|
| Figma 节点/画板 | `687:475` / `1440×1024` |
| 前端入口 | `/chat?state=tool-executing` |
| 主结构 | `260px` 侧栏、`860px` 对话区、`320px` Trace rail；保留完整共享工作站壳层 |
| 状态条 | `Planning ✓`、`Retrieving ✓`、`Executing ●`、`Composing ○` |
| 工具卡 | 完成、运行中、待处理三行；运行中橙色边框与加载图标，待处理降低强调度 |
| Composer | `y=912`、`h=112`；输入禁用，停止按钮保持可用 |
| Figma 证据 | `chat-agent-tool-executing-figma.png` |
| 浏览器证据 | `chat-agent-tool-executing-browser-1440x1024.jpg`、`chat-agent-tool-executing-browser-390x844.jpg` |
| RGBA 证据 | `chat-agent-tool-executing-browser-1440x1024-rgba.png`、`chat-agent-tool-executing-browser-390x844-rgba.png` |
| 自动 diff | 差异比例 `50.5259%`，RMSE `23.1703`，`DIFF_REVIEW` |

- [x] 桌面几何已实际核对：主区 `x=260,w=860`，Trace body `y=107`，首个 Trace 卡 `y=135`，工具气泡 `181×250`，用户气泡 `228×49`，Composer `y=912/h=112`。
- [x] 移动 `390×844` 实测 `document.body.scrollWidth === 390`；Trace rail 按窄屏规则隐藏，工具卡和停止按钮没有页面级横向溢出。
- [x] Trace 状态真实渲染 `fst_trace_9821aa`、意图解析、向量检索、数据库调用和结果合成；query fixture 只用于设计验收，不代表真实 AgentRun/SSE 后端闭环。
- [x] `ChatPage` 定向测试 `4/4`、`npm run typecheck`、本次触及文件 Prettier 和 `git diff --check` 通过。
- [ ] diff 仍为 `DIFF_REVIEW`；当前账户文案、字体光栅化和 Figma/前端头像位图差异未被伪装成 PASS。
- [ ] iconfont 实体资源仍为 `BLOCKED`；标准命令图标继续使用 Lucide，未写入虚构字体包、类名或 Unicode。
- [ ] `agent-awaiting-clarification` 已完成当前版本证据复核；下一步按顺序复核 `agent-write-confirmation`、`agent-budget-limit`，再处理失败、降级、取消和 SSE 重连状态。

## 43. 2026-08-15 Agent Awaiting Clarification 状态迁移与验收

本轮完成 Figma `agent-awaiting-clarification` 状态到独立前端 fixture 的迁移。唯一视觉来源为 Figma 文件 `MX18RZCfAmgprNzxItkHUH` 的节点 `687:642`，画板尺寸为 `1440×1024`；前端入口为 `/chat?state=awaiting-clarification`。该 query 只复现可重复的前端视觉和交互状态，不代表真实 AgentRun、SSE、澄清提交或后端任务闭环。

| 验收项 | 实测结果 |
|---|---|
| 桌面视口 | `1440×1024`，`document.body.scrollWidth === 1440` |
| 桌面澄清卡 | `x=340,y=237,w=222,h=193` |
| 桌面 Composer | `x=260,y=912,w=1180,h=112`，保持可输入 |
| 移动视口 | `390×844`，`document.body.scrollWidth === 390`，侧栏隐藏 |
| 移动澄清卡 | `x=64,y=251.8,w=222,h=193` |
| 移动 Composer | `x=0,y=732.8,w=375.2,h=112` |
| 共享布局状态 | 顶部“工作台”和侧栏“Agent 对话”均显示 Figma 选中态；fixture 覆盖 `Anddy / 1234567` |
| 资源 | 已登记 sidebar、topbar、message 三个 Figma 头像资源；路径位于 `foodmate-ui/public/assets/figma/agent-chat/awaiting-clarification/` |
| 定向测试 | `ChatPage.test.tsx`：`5/5` 通过；`npm run typecheck` 通过 |
| 桌面自动 diff | `differentRatio=0.1600301`，`meanAbsoluteError=1.9716`，`RMSE=13.5626`，结论 `DIFF_REVIEW` |
| 移动自动 diff | 当前只有 `1440×1024` Figma 参考图，移动截图实际为 `390×843` PNG；与桌面参考图尺寸不同，结论 `SIZE_MISMATCH`，不输出像素通过结论 |

- [x] 澄清卡选项支持选中态和回调；Composer 在 awaiting 状态保持可输入。
- [x] 共享布局在视觉 fixture 存在 override 时优先使用 override，不被当前认证用户状态覆盖。
- [x] 桌面和移动浏览器截图、RGBA 归一化截图与 Figma 参考图已保存到 `foodmate-ui/.qa/figma-pixel-acceptance/`。
- [ ] 自动 diff 仍为 `DIFF_REVIEW`；不能以人工接近替代像素级 PASS。
- [ ] iconfont 实体包、CSS 映射、来源 URL、许可证和 glyph 登记仍为 `BLOCKED`；标准命令图标继续使用 Lucide。
## 45. 2026-08-15 全量映射与 PNG 证据复核

本轮按实时 Figma 文件 `MX18RZCfAmgprNzxItkHUH` 的 `🎨 :: Design` 页面重新核对 105 个顶层画板，并补齐此前缺失的 11 张原始 Figma PNG。验收清单和自动汇总分别位于：

- `foodmate-ui/.qa/figma-pixel-acceptance/figma-105-mapping.json`
- `foodmate-ui/.qa/figma-pixel-acceptance/figma-105-diff-results.json`
- `foodmate-ui/scripts/generate-figma-105-diff.mjs`

本轮汇总结果：

| 状态 | 数量 | 说明 |
|---|---:|---|
| `DIFF_REVIEW` | 105 | Figma 与浏览器 PNG 尺寸一致，已运行 `scripts/png-diff.mjs`；仍有视觉差异，且当前 Chat 历史页复核的 DPR1 门禁未关闭 |
| `UNMAPPED` | 0 | 105 张画板均已有可验证的浏览器 fixture/路由证据 |
| `SIZE_MISMATCH` | 0 | 本轮没有把尺寸不一致伪装成像素通过 |
| `PASS` | 0 | 未满足自动 diff、几何、文字和人工复核四项条件 |

此前由 JPEG 字节误命名为 `.png` 导致的 `DIFF_ERROR` 已从当前 105 条输入中排除：汇总脚本会校验 PNG 文件头，并优先选择同尺寸的 RGBA 证据。当前清单引用的 105 个 Figma PNG 与 105 个浏览器 PNG 均已通过文件头和尺寸校验。历史基线运行时检查的 `viewportPass`、`geometryPass` 和 `textPass` 为 `105/105`，当前更新后的 `dprPass=102/105`，字体状态均为 `loaded`；人工视觉复核仍为 `0/105`。新增 Agent 六个状态均已建立 `/chat?state=...` fixture、浏览器 PNG 和 diff 记录，但结果继续保持 `DIFF_REVIEW`。

本轮已关闭 `UNMAPPED` 映射缺口，但没有关闭任何 `PASS`。部分 Admin 操作弹窗、Profile 异步操作、历史会话交互和 Workspace 输入状态均使用独立 query fixture，不能与默认页面截图混淆。iconfont 实体资源仍为 `BLOCKED`；后端真实 Agent/SSE 闭环也不作为本轮 fixture 完成标准。

## 46. 2026-08-22 shadcn 控件迁移后的运行时复核

本轮完成的是业务页面控件基础设施迁移，不重新生成 105 张画板的 Figma PNG 或像素 diff，因此不改变上一节的全量结论。

- Planning `/planning?state=wizard-step2` 浏览器复核确认：步骤导航控件为 `32px`，过敏源 Chip 和添加入口为 `26px`；修正前 shadcn 默认高度曾将 Chip 撑到 `40px`，已通过页面 CSS 显式覆盖并重新截图确认。
- Profile `/profile?state=basic` 浏览器复核确认：资料操作按钮和过敏原标签均保持设计 CSS 尺寸，过敏原标签为 `32px`；未发现页面级横向溢出。
- 业务页面源码扫描结果：原生 `<button>` 数量 `0`，`AdminPrimitives` 直接依赖数量 `0`；这项结果只证明控件实现边界，不证明页面与 Figma 已像素一致。
- Planning 冲突解决页 `/planning?state=conflict` 已实测两个 `radiogroup`、4 个可访问 radio、默认选中态和切换态；菜系选择 `/planning?state=wizard-step2` 已实测 shadcn Select 的三个 option 和受控值更新。
- Chat 历史写入确认卡 `/chat?state=history-page-2` 已实测两个可访问 radio，默认“添加到今天的午餐”，点击后可切换到“仅作为对话参考”。
- 当前页面级原生 `<button>`、`<select>`、`type="radio"`、`type="checkbox"` 均为 `0`；仅保留 3 个文件上传输入，属于浏览器文件选择 API 的必要入口；`AdminPrimitives` 直接依赖仍为 `0`。
- Planning 定向测试 `7/7`、Chat 定向测试 `25/25`，全量测试 `25` 个测试文件、`136/136`；`npm run typecheck`、`npm run build` 与 `git diff --check` 均通过。
- `MealPlanningFlow.tsx` 定向 Prettier 已通过；全量 `format:check` 仍受其他未提交文件阻塞，未把该阻塞写成当前页面运行失败。

本次不更新 105 条 diff 状态：仍为 `DIFF_REVIEW=105`、`PASS=0`，人工视觉复核仍未完成；iconfont 资源继续为 `BLOCKED`。

## 51. 2026-08-22 Agent Chat 会话操作面板复核

本轮重新读取 Figma 节点 `806:212`，并对 `/chat?state=session-actions` 完成代码、交互和同尺寸截图复核。Figma 关键几何为遮罩起点 `x=260`、操作面板 `x=470,y=90,w=760,h=316`、选中会话卡 `w=712,h=72`；浏览器实测保持这些尺寸，关闭按钮会隐藏面板并写入 `role=status` 提示。

| 验收项 | 当前证据 |
|---|---|
| Figma 节点 | `806:212`，画板 `1440×1024` |
| 浏览器截图 | `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-chat-session-actions-browser-current.png` |
| diff JSON | `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-chat-session-actions-current-diff.json` |
| PNG diff | `differentRatio=40.11095%`、`MAE=4.91090`、`RMSE=19.75133`、`maxChannelDelta=255`，保持 `DIFF_REVIEW` |
| 几何与运行时 | `1440×1024`、DPR `1`、面板 `760×316`、会话卡 `712×72`、根节点无滚动溢出 |
| 行为回归 | Chat 定向测试 `25/25`；关闭操作后 `dialogCount=0`；`npm run typecheck` 与目标文件 Prettier 通过 |

- [x] 当前画板已完成自动 diff、几何检查、文字检查和人工视觉复核登记；结果仍为 `DIFF_REVIEW`，没有把面板几何通过写成像素 `PASS`。
- [ ] 底层 Workspace 壳层、字体渲染、图标、遮罩合成和对话/Trace 内容仍与 Figma 存在差异；后续继续逐页修正。
- [ ] iconfont 实体资源登记继续为 `BLOCKED`；本轮没有创建虚构字体包、Unicode 或 CSS 映射。

## 59. 2026-08-22 Chat 历史分页当前版本证据更新

本轮重新读取实时 Figma 节点 `740:212`、`740:426`、`742:212`，并按当前前端代码重新采集三个 Chat 历史状态。前端保留用户要求的约束：只移除实现页面左上角的红、黄、绿三色窗口装饰点，Figma 画板不做修改；Figma 节点中的 `Space Mono` 助手正文、确认卡和 Trace 结构已按上下文保留。

| 状态 | Figma 节点 | 浏览器 PNG | PNG diff | 结论 |
|---|---|---|---|---|
| 历史第 2 页 | `740:212` | `agent-chat-history-page-2-browser-current.png` | `35.11997% / MAE 4.80656 / RMSE 21.57858` | `DIFF_REVIEW` |
| 历史第 3 页 | `740:426` | `agent-chat-history-page-3-browser-current.png` | `35.12099% / MAE 4.80661 / RMSE 21.57857` | `DIFF_REVIEW` |
| 搜索结果 | `742:212` | `agent-chat-search-results-browser-current.png` | `34.61378% / MAE 4.66959 / RMSE 21.26354` | `DIFF_REVIEW` |

三页均为 `1440×1024`，字体状态为 `loaded`，根节点无横向或纵向溢出，前端窗口装饰点数量为 `0`。当前 in-app 浏览器实际报告 `devicePixelRatio=1.25`，不满足计划要求的 DPR 1，因此本轮没有将几何检查写成 DPR 通过，也没有将任何页面标记为 `PASS`。本机 Chrome 的隔离 DPR1 截图尝试受当前执行策略拦截，未伪造替代证据。

- [x] 三个状态的当前前端 PNG、自动 diff 和映射字段已更新到 `foodmate-ui/.qa/figma-pixel-acceptance/`。
- [x] 旧人工结论中“缺少助手响应、来源、确认控件和 Trace”的描述已修正；当前 fixture 已包含这些结构。
- [ ] DPR1 浏览器截图仍待可验证的浏览器环境；三个状态继续保持 `DIFF_REVIEW`。
- [ ] iconfont 实体包、CSS 映射、来源、许可证和 glyph-Figma 映射仍缺失，继续保持 `BLOCKED`。

## 53. 2026-08-22 Agent Chat 归档结果卡复核

本轮重新读取 Figma 节点 `806:662`，并对 `/chat?state=archived` 完成同尺寸截图和交互复核。设计卡片为 `x=540,y=286,w=620,h=276`，浏览器已对齐 `ARCHIVED` 状态、归档会话条、保留说明、恢复按钮和关闭按钮；恢复与关闭均保留独立状态提示。

| 验收项 | 当前证据 |
|---|---|
| Figma 节点 | `806:662`，画板 `1440×1024` |
| 浏览器截图 | `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-chat-archived-browser-current.png` |
| diff JSON | `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-chat-archived-current-diff.json` |
| PNG diff | `differentRatio=38.11252%`、`MAE=3.88295`、`RMSE=17.12204`、`maxChannelDelta=255`，保持 `DIFF_REVIEW` |
| 几何与运行时 | `1440×1024`、DPR `1`、卡片 `620×276`、归档条 `556×56`、根节点无滚动溢出 |
| 行为回归 | Chat 定向测试 `25/25`；恢复/关闭动作已在浏览器验证；`npm run typecheck` 与目标文件 Prettier 通过 |

- [x] 当前画板已完成自动 diff、几何检查、文字检查和人工视觉复核登记；结果仍为 `DIFF_REVIEW`，没有把归档卡几何通过写成像素 `PASS`。
- [ ] 底层 Workspace 壳层、字体渲染、图标、遮罩合成和对话/Trace 内容仍与 Figma 存在差异；后续继续逐页修正。
- [ ] iconfont 实体资源登记继续为 `BLOCKED`；本轮没有创建虚构字体包、Unicode 或 CSS 映射。

## 52. 2026-08-22 Agent Chat 会话重命名结果卡复核

本轮重新读取 Figma 节点 `806:438`，并对 `/chat?state=renamed` 完成同尺寸截图和交互复核。设计卡片为 `x=540,y=300,w=620,h=244`，浏览器已对齐 `SAVED` 状态、同步说明、关闭按钮和 `148×44` 返回按钮；关闭结果后不会继续保留遮罩。

| 验收项 | 当前证据 |
|---|---|
| Figma 节点 | `806:438`，画板 `1440×1024` |
| 浏览器截图 | `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-chat-renamed-browser-current.png` |
| diff JSON | `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-chat-renamed-current-diff.json` |
| PNG diff | `differentRatio=37.23843%`、`MAE=3.76595`、`RMSE=16.77892`、`maxChannelDelta=255`，保持 `DIFF_REVIEW` |
| 几何与运行时 | `1440×1024`、DPR `1`、卡片 `620×244`、返回按钮 `148×44`、根节点无滚动溢出 |
| 行为回归 | Chat 定向测试 `25/25`；返回/关闭动作已在浏览器验证；`npm run typecheck` 与目标文件 Prettier 通过 |

- [x] 当前画板已完成自动 diff、几何检查、文字检查和人工视觉复核登记；结果仍为 `DIFF_REVIEW`，没有把成功卡几何通过写成像素 `PASS`。
- [ ] 底层 Workspace 壳层、字体渲染、图标、遮罩合成和对话/Trace 内容仍与 Figma 存在差异；后续继续逐页修正。
- [ ] iconfont 实体资源登记继续为 `BLOCKED`；本轮没有创建虚构字体包、Unicode 或 CSS 映射。

## 47. 2026-08-22 注册页布局层级收敛

本轮重新读取 Figma 节点 `680:216`，并在 `1440×900`、DPR 1、字体加载完成条件下重新采集 `/register`。改动范围仅限注册页结构：将 Figma 中独立的四字段组 `680:227`、密码规则组 `680:250` 和底部操作组 `680:263` 从平铺表单间距改为对应的嵌套层级，保留 `form` 语义、真实注册接口和 shadcn `Input`/`Button` 控件。

| 验收项 | 当前证据 |
|---|---|
| 卡片几何 | `x=490,y=34.4,w=460,h=831.2`；内容宽 `380px` |
| 输入几何 | 四个输入 `y=235.2/320.8/406.4/492px`，均为 `380×50px` |
| Figma 参考 | `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/register-page-latest.png` |
| 浏览器 RGBA | `foodmate-ui/.qa/figma-pixel-acceptance/register-page-browser-current-rgba.png` |
| PNG diff | `differentRatio=54.06998%`、`meanAbsoluteError=0.80904`、`RMSE=5.52169`，保持 `DIFF_REVIEW` |
| 行为回归 | `AuthPages.test.tsx`：`13/13`；`npm run typecheck` 通过；触及文件 Prettier 通过 |

- [x] 注册页的卡片、字段、密码规则和操作区已按 Figma 层级分组，按钮与页脚不再受字段组平铺间距影响。
- [x] 注册页保留空值交互状态，用户输入后密码规则按真实值更新；Figma 静态参考图中的示例值和全绿规则因此继续作为状态差异记录。
- [ ] 该画板仍不能标记 `PASS`：当前浏览器交互态与 Figma 示例填充态不同，且完整人工视觉复核尚未关闭；不得用本轮几何通过替代像素验收。
- [ ] iconfont 实体资源继续为 `BLOCKED`，本轮未添加虚构字体包、Unicode 或 CSS 映射。

## 48. 2026-08-22 找回密码页布局层级收敛

本轮重新读取 Figma 节点 `680:275`，并按实时画板重构 `/forgot-password` 的左右卡片层级。左卡片的邮箱字段与操作区由卡片的 `28px` 间距直接分隔；右卡片将成功图标、标题和说明归入 `16px` 内容组，返回按钮独立使用 Figma 的 `360×46px`、`12px` 圆角样式。表单仍保留真实 `requestPasswordReset` 接口和提交后的状态提示。

| 验收项 | 当前证据 |
|---|---|
| 桌面视口 | `1440×900`，DPR `1.0000000149011612`，页面宽度 `1440` |
| 左侧几何 | 卡片 `x=260,w=440,h=416.4`；输入 `y=452.2,h=50`；发送按钮 `y=530.2,h=50` |
| 右侧几何 | 卡片 `x=740,w=440,h=306.4`；返回按钮 `x=780,y=462.2,w=360,h=46` |
| Figma 参考 | `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/forgot-password-page-latest.png` |
| 浏览器 RGBA | `foodmate-ui/.qa/figma-pixel-acceptance/forgot-password-page-browser-current-rgba.png` |
| PNG diff | `differentRatio=99.92716%`、`meanAbsoluteError=1.10110`、`RMSE=6.82914`，保持 `DIFF_REVIEW` |
| 行为回归 | `AuthPages.test.tsx`：`13/13`；`npm run typecheck` 通过；触及文件 Prettier 通过 |

- [x] 左右卡片均无页面级横向溢出，右侧成功卡片的默认结构与 Figma 成功态保持一致。
- [x] 发送重置邮件、返回登录和提交后 `role=status` 提示继续保持可操作；真实模式仍只调用既有密码找回接口。
- [ ] 该画板仍不能标记 `PASS`：PNG 自动 diff 与完整人工视觉复核门槛尚未关闭；`differentRatio` 不能被“视觉接近”替代。
- [ ] iconfont 实体资源继续为 `BLOCKED`，本轮继续使用 Lucide 标准图标。

## 49. 2026-08-22 重置密码页布局层级收敛

本轮重新读取 Figma 节点 `680:307`，并按 `680:318`、`680:331`、`680:340` 将 `/reset-password` 的密码字段组、强度组和提交操作组拆为卡片的独立层级。字段组内部使用 `16px` 间距，卡片组间使用 Figma 的 `28px` 间距；标题组和字段标签行高也按实时节点的 `8px`/`normal` 约束覆盖。真实 token 校验、密码确认、提交和返回登录行为保持不变。

| 验收项 | 当前证据 |
|---|---|
| 桌面视口 | `1440×900`，DPR `1.0000000149011612`，页面宽度 `1440` |
| 卡片几何 | `x=490,y=166.2,w=460,h=567.6`；内容宽 `380px` |
| 输入几何 | 两个输入 `y=376.6/467.8px`，均为 `380×50px` |
| 强度/操作组 | 强度条 `y=571.8,h=6`；确认按钮组 `y=605.8,h=88` |
| Figma 参考 | `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/reset-password-page-latest.png` |
| 浏览器 RGBA | `foodmate-ui/.qa/figma-pixel-acceptance/reset-password-page-browser-current-rgba.png` |
| PNG diff | `differentRatio=99.13426%`、`meanAbsoluteError=1.46441`、`RMSE=10.02255`，保持 `DIFF_REVIEW` |
| 行为回归 | `AuthPages.test.tsx`：`13/13`；`npm run typecheck` 通过；触及文件 Prettier 通过 |

- [x] 字段、强度条和提交操作已按 Figma 层级拆分，页面无横向溢出。
- [x] 前端保持空值密码输入和 token 缺失保护；真实模式仍调用既有 `confirmPasswordReset`，不伪造成功响应。
- [ ] 该画板仍不能标记 `PASS`：Figma 静态示例值与交互页面 placeholder 状态不同，自动 diff 和完整人工复核门槛尚未关闭。
- [ ] iconfont 实体资源继续为 `BLOCKED`，本轮未添加虚构字体包、Unicode 或 CSS 映射。

## 50. 2026-08-22 Token 状态页结构与资产对齐

本轮重新读取 Figma 节点 `680:738`、`680:757`、`680:776`，并按各自的 `error-card`、状态内容组和操作组重构 `/token-status?state=invalid|expired|used`。Figma 返回的真实 SVG 已登记到 `foodmate-ui/public/assets/figma/auth/`：品牌 fork-knife、错误三角、过期时钟和已使用信息图标。未创建虚构 iconfont glyph。

| 状态 | Figma 卡片 | 浏览器卡片 | 操作结构 | PNG diff |
|---|---|---|---|---|
| 无效 `680:738` | `x=490,y=242,w=460,h=416` | `x=490,y=242,w=460,h=416` | 重新发送 `380×52`；返回登录行 `380×25` | `differentRatio=99.99877%`，`MAE=14.14673`，`RMSE=25.36159`，`DIFF_REVIEW` |
| 过期 `680:757` | `x=490,y=242,w=460,h=416` | `x=490,y=242,w=460,h=416` | 重新发送 `380×52`；返回登录行 `380×25` | `differentRatio=99.99877%`，`MAE=14.18704`，`RMSE=25.47945`，`DIFF_REVIEW` |
| 已使用 `680:776` | `x=490,y=197,w=460,h=506` | `x=490,y=197,w=460,h=506` | 重新发送/联系客服均 `380×52`；返回登录行 `380×25` | `differentRatio=99.99414%`，`MAE=14.27139`，`RMSE=25.74397`，`DIFF_REVIEW` |

| 验收项 | 当前证据 |
|---|---|
| Figma 节点 | `680:738`、`680:757`、`680:776`；卡片内层均为 `380px` |
| 浏览器桌面 | `1440×900`、DPR `1.0000000149011612`、字体状态 `loaded`、三态 `scrollWidth=clientWidth=1440` |
| 浏览器移动 | `390×844`；三态 `scrollWidth=clientWidth=390`，所有图标均完成加载 |
| Figma PNG | `docxs/设计/figma-png/token-invalid.png`、`token-expired.png`、`token-used.png` |
| 浏览器 PNG | `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/token-invalid-browser.png`、`token-expired-browser.png`、`token-used-browser.png` |
| diff JSON | `foodmate-ui/.qa/figma-pixel-acceptance/figma-105-diff-results.json#token-invalid|token-expired|token-used` |
| 行为回归 | `TokenStatusPage.test.tsx` 与 `AuthPages.test.tsx`：`16/16`；`npm run typecheck`、目标文件 Prettier 和 `git diff --check` 通过 |

- [x] 品牌、状态内容、操作组已按 Figma 层级拆分；三态真实导航行为保持不变。
- [x] 使用 Figma 节点返回的真实 SVG 资产；标准按钮继续使用 shadcn `Button`。
- [ ] 三态仍不能标记 `PASS`：自动 diff 仍存在差异，完整 105 画板人工视觉复核也未关闭。
- [ ] iconfont 实体资源继续为 `BLOCKED`，本轮没有创建字体包、CSS/Unicode 映射或伪造许可证信息。

## 51. 2026-08-23 餐食规划列表默认态卡片复核

本轮重新读取 Figma 节点 `692:2662`，并修正 `/planning?state=list` 前端 fixture 的默认展示规则。Figma 默认画板在“进行中”标签选中时仍同时呈现进行中、草稿和已归档三张计划卡；原实现只呈现进行中卡片，造成主内容区域与 Figma 不一致。当前实现默认展示三张卡，切换到“草稿箱”或“已归档”后继续按状态筛选；真实模式仍按服务端计划状态筛选。

| 验收项 | 当前证据 |
|---|---|
| Figma 节点与视口 | `692:2662`，`1440×1024` |
| 前端入口 | `/planning?state=list` |
| 浏览器检查 | 字体 `loaded`；三张计划卡、三种状态标签、进入计划和更多操作均存在；`document/body` 无横向溢出 |
| Figma 参考 | `docxs/设计/figma-png/meal-plan-list.png` |
| 浏览器 RGBA | `foodmate-ui/.qa/figma-pixel-acceptance/meal-plan-list-browser-current-rgba.png` |
| diff JSON | `foodmate-ui/.qa/figma-pixel-acceptance/meal-plan-list-current-diff.json`、`figma-105-diff-results.json#meal-plan-list` |
| PNG diff | `differentRatio=28.28437%`、`MAE=3.94548`、`RMSE=19.39610`，保持 `DIFF_REVIEW` |
| 行为回归 | `PlanningPage.test.tsx`：`8/8`；`npm run typecheck`、Prettier、`git diff --check` 通过 |

- [x] 本轮仅修改前端列表默认展示逻辑和对应测试；Figma 设计稿未修改。
- [x] 前端左上角红、黄、绿窗口装饰点检查结果为 `0`；业务状态圆点不属于窗口装饰点，继续保留。
- [ ] 该画板仍不能标记 `PASS`：卡片几何、内容密度、字体和图标光栅化仍存在差异；105 张画板汇总仍为 `105 DIFF_REVIEW / 0 UNMAPPED / 0 SIZE_MISMATCH / 0 PASS`。
- [ ] iconfont 实体资源继续为 `BLOCKED`，本轮未创建虚构字体、Unicode 或 CSS 映射。

## 52. 2026-08-23 餐食规划列表菜单图标复核

- [x] Figma 节点 `692:2662` 的计划卡更多操作图标已重新核对为三条横线菜单图标；前端使用已存在的 Lucide `Menu`，不创建未经登记的 iconfont 资源。
- [x] `/planning?state=list` 浏览器复核确认三个计划卡的更多操作按钮均存在，`scrollWidth=1440`，无页面级横向溢出。
- [x] 当前证据继续使用 `foodmate-ui/.qa/figma-pixel-acceptance/meal-plan-list-browser-current-rgba.png`，独立 diff 为 `meal-plan-list-current-diff.json`；PNG diff 为 `28.3198% / MAE 3.9498 / RMSE 19.4020`，保持 `DIFF_REVIEW`。
- [ ] 该画板仍不能标记 `PASS`；剩余卡片几何、内容密度、字体与图标光栅化差异仍需后续逐项收口，iconfont 实体资源继续为 `BLOCKED`。

## 53. 2026-08-23 餐食规划列表顶部头像资源复核

- [x] 实时读取 Figma `692:2662` 的原始图片资产，确认顶部用户头像应使用 Figma 返回的男性肖像，而不是旧的渐变字标图；新增本地资源 `foodmate-ui/public/assets/figma/planning/meal-plan-list-topbar-avatar.png`。
- [x] `/planning?state=list` 浏览器实测顶部头像 `src` 为 `/assets/figma/planning/meal-plan-list-topbar-avatar.png`，图片加载完成；Figma 设计稿未修改，业务默认头像资源未改写。
- [x] 当前浏览器 RGBA 证据为 `foodmate-ui/.qa/figma-pixel-acceptance/meal-plan-list-browser-current-rgba.png`，PNG diff 为 `28.3485% / MAE 3.9456 / RMSE 19.3731`，保持 `DIFF_REVIEW`。
- [ ] 卡片几何、内容密度、字体和图标光栅化仍需继续验收；iconfont 实体资源继续为 `BLOCKED`。

## 54. 2026-08-23 餐食规划列表排版尺寸复核

- [x] `/planning?state=list` 继续以 Figma 节点 `692:2662` 和 `1440×1024` 为唯一视觉依据，收紧列表副标题、新建按钮、Tab、计划日期的字号和行高。
- [x] 最新浏览器截图已完成字体加载和同尺寸转换，证据为 `foodmate-ui/.qa/figma-pixel-acceptance/meal-plan-list-browser-current-rgba.png`；页面几何检查保持通过，未发现横向溢出。
- [x] `scripts/png-diff.mjs` 最新结果：`differentPixels=411560`、差异比例 `27.9107%`、`MAE=3.8105`、`RMSE=18.9605`、最大通道差异 `255`；机器结果锚点为 `figma-105-diff-results.json#meal-plan-list`。
- [x] 排版调整后差异指标相较头像资源版本有所下降，但仍存在卡片几何、内容密度、字体和图标光栅化差异。
- [ ] 该画板继续保持 `DIFF_REVIEW`，不能因局部指标改善标记为像素级 `PASS`；iconfont 实体资源继续为 `BLOCKED`。

## 55. 2026-08-23 餐食规划列表顶部头像圆形裁切复核

- [x] 修正 WorkspaceLayout 顶部头像容器的裁切边界：头像保持 `32×32`、`border-radius: 50%`，并增加 `overflow: hidden`；侧栏头像和业务状态圆点未改变。
- [x] 在 `1440×1024`、DPR `1.0000000149011612`、字体 `loaded` 的浏览器环境重新采集截图；Figma 设计稿未修改。
- [x] 浏览器实测顶部头像资源加载成功，容器 `overflow=hidden`，前端左上角红、黄、绿窗口装饰点数量仍为 `0`。
- [x] 最新 PNG diff：`differentPixels=411026`、差异比例 `27.8745%`、`MAE=3.8013`、`RMSE=18.9252`、最大通道差异 `235`；机器结果锚点为 `figma-105-diff-results.json#meal-plan-list`。
- [ ] 该画板继续保持 `DIFF_REVIEW`；本项只完成头像边界修正，不代表整页像素级通过、全量 shadcn 迁移或 iconfont 解阻塞。

## 56. 2026-08-23 餐食规划列表底部说明面板几何复核

- [x] 依据 Figma 节点 `692:2662` 回读值，将桌面端“计划卡片操作与状态”面板对齐到 `x=260、y=802、width=1116、height=222、bottom=1024`；面板原有颜色、圆角、内边距和文字内容保持不变。
- [x] 计划卡片区域仍保持 `x=292、width=1116`，仅修正底部说明面板相对右侧内容区的左边界、固定高度和底部贴合关系；移动端恢复原有自适应高度和边距。
- [x] 浏览器验收使用 `1440×1024`、DPR `1.0000000149011612`、字体 `loaded`；最新 PNG diff：`differentPixels=340303`、差异比例 `23.0783%`、`MAE=3.6995`、`RMSE=18.7132`、最大通道差异 `235`。
- [ ] 该画板仍为 `DIFF_REVIEW`，剩余卡片细节、字体和图标光栅化差异需要继续收口；本项不代表整页 `PASS` 或全量页面迁移完成。

## 57. 2026-08-23 餐食规划列表操作组间距复核

- [x] 依据 Figma 节点 `692:2761`、`692:2778`、`692:2795` 的操作组定义，将三个计划卡“进入计划”和菜单按钮之间的间距从 `12px` 修正为 `16px`；菜单按钮右边界保持不变。
- [x] 浏览器实测三个操作组均为 `gap=16px`，无横向溢出；视口为 `1440×1024`、DPR `1.0000000149011612`、字体 `loaded`。
- [x] 最新 PNG diff：`differentPixels=340784`、差异比例 `23.1109%`、`MAE=3.6961`、`RMSE=18.7010`、最大通道差异 `235`。差异比例局部重排后略升，但 MAE/RMSE 下降，且 `16px` 是 Figma 明确几何值，因此保留设计对齐结果。
- [ ] 该画板继续保持 `DIFF_REVIEW`；不能以单一差异比例替代 Figma 几何证据，也不能标记整页 `PASS`。

## 58. 2026-08-23 餐食规划列表信息标签样式复核

- [x] 依据 Figma 节点 `692:2758`、`692:2775`、`692:2792`，将计划卡“经济适用/优质食材/家庭量贩”标签从共享状态样式中拆出，修正为 `12px`、粗体、`#c79654` 前景和 `rgba(255,246,226,0.1)` 背景。
- [x] 说明面板固定高度下补充顶部内容对齐，保留 Figma 要求的 `gap=8px`，避免 CSS Grid 将空余高度分配到文字行之间。
- [x] 浏览器实测标签样式与说明面板几何：标签 `font-size=12px`、`font-weight=700`、面板 `x=260,y=802,width=1116,height=222`；视口 `1440×1024`、DPR `1.0000000149011612`、字体 `loaded`。
- [x] 最新 PNG diff：`differentPixels=384882`、差异比例 `26.1015%`、`MAE=3.8913`、`RMSE=19.4011`、最大通道差异 `234`。
- [ ] 该画板仍保持 `DIFF_REVIEW`；该项按 Figma 样式值完成，不能因整页 diff 未下降而回退到错误的绿色 `11px` 标签。

## 60. 2026-08-23 Intake Analysis 当前版本验收证据更新

- [x] 重新核对实时 Figma 节点 `640:773` 与 `/analysis?state=v2`；本轮 Figma 参考图改用当前文件导出的 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/intake-analysis-v2-current.png`，不再使用缺少会话列表和数据质量面板的旧基线。
- [x] 浏览器证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/intake-analysis-v2-browser-current.png`；视口 `1440×1024`、DPR `1.0000000149011612`、字体状态 `loaded`、页面无横向或纵向溢出、文字越界 `0`。
- [x] 当前版本几何复核记录：侧栏品牌区 `y=52`、新建任务 `y=104`、搜索框 `y=161`、工作台 `y=217`、Agent 对话 `y=259`；账户停靠区折叠条 `y=866,h=28`、状态条 `y=910,h=38`、用户资料 `y=964,h=36`；Figma fixture 顶栏品牌宽 `136px`、导航起点 `x=444`、导航间距 `16px`、搜索框高 `32px`、用户区宽 `84px`。
- [x] 前端左上角红、黄、绿窗口装饰点数量为 `0`；仅保留用于 Figma 垂直布局对齐的空白占位，未修改 Figma 设计稿；其它圆形业务控件未按窗口装饰点处理。
- [x] `scripts/png-diff.mjs` 同尺寸比较结果：`differentPixels=396008`、差异比例 `26.8560%`、`MAE=3.2500`、`RMSE=17.4654`、最大通道差异 `234`。
- [ ] 本页继续保持 `DIFF_REVIEW`，不能标记 `PASS`；图标处理、字体光栅化和主体视觉处理仍有可见差异。105 张画板汇总仍为 `105 DIFF_REVIEW / 0 UNMAPPED / 0 SIZE_MISMATCH / 0 PASS`，iconfont 继续为 `BLOCKED`。

## 61. 2026-08-23 Meal Planning 当前版本验收证据更新

- [x] `640:901` `/planning?state=v2` 已使用当前 Figma 截图 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/meal-planning-v2-current.png`，浏览器证据为同尺寸 RGBA PNG `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/meal-planning-v2-browser-current-rgba.png`。
- [x] 浏览器运行时实测视口为 `1440×1024`、字体状态为 `loaded`、`document` 和 `body` 均无横向溢出；默认目标文案为 `2,400千卡`，购物清单复选框为独立 `14×14px` 控件。
- [x] 前端左上角红、黄、绿窗口装饰点检查结果为 `0`，因此没有对应代码需要删除；首页活跃会话中的 `sessionDot` 属于业务状态指示器，不属于窗口装饰点，保留不变；Figma 设计稿未修改。
- [x] 当前 PNG diff：差异比例 `23.8253%`、`MAE=3.0472`、`RMSE=16.8015`、最大通道差异 `234`；机器结果锚点为 `figma-105-diff-results.json#meal-planning-v2`。
- [ ] 本页继续保持 `DIFF_REVIEW`；计划工具栏、导航、餐卡几何、字体和内容密度仍存在视觉差异，不能标记 `PASS`。

## 62. 2026-08-23 Agent Clarification 当前版本验收证据更新

- [x] `687:642` `/chat?state=awaiting-clarification` 已重新读取实时 Figma 画板并保存当前参考图 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/agent-awaiting-clarification-current.png`；浏览器证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-awaiting-clarification-browser-current-rgba.png`。
- [x] 浏览器运行时实测视口为 `1440×1024`、DPR `1.0000000149011612`、字体状态为 `loaded`、`document` 和 `body` 均无横向溢出；澄清选项文案与实时 Figma 完全一致：`补充食物和份量`、`上传照片识别`。
- [x] 前端左上角红、黄、绿窗口装饰点检查结果为 `0`；当前实现保留 Figma 所需的顶部空白布局占位，不包含窗口装饰点；Figma 设计稿未修改。
- [x] 当前 PNG diff：差异比例 `15.4844%`、`MAE=1.8229`、`RMSE=12.9095`、最大通道差异 `251`；机器结果锚点为 `figma-105-diff-results.json#agent-awaiting-clarification`。
- [ ] 本页继续保持 `DIFF_REVIEW`；剩余差异主要来自头像处理及字体/图标光栅化，不能标记 `PASS`。

## 63. 2026-08-23 Agent Write Confirmation 当前画板证据复核

- [x] `687:773` `/chat?state=write-confirmation` 已重新读取实时 Figma 画板并保存当前参考图 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/agent-write-confirmation-current.png`；浏览器证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-write-confirmation-browser-current.png`。
- [x] 浏览器运行时实测视口为 `1440×1024`、DPR `1.0000000149011612`、字体状态为 `loaded`、`document` 和 `body` 均无横向溢出、文字越界 `0`；写入目标、日期、食物、热量、蛋白质、来源、估算假设和确认/取消操作均存在。
- [x] 前端左上角红、黄、绿窗口装饰点数量为 `0`；当前实现没有对应窗口装饰点，Figma 设计稿未修改。
- [x] `scripts/png-diff.mjs` 同尺寸比较结果：`differentPixels=379283`、差异比例 `25.7218%`、`MAE=2.9444`、`RMSE=16.2155`、最大通道差异 `237`；机器结果锚点为 `figma-105-diff-results.json#agent-write-confirmation`。
- [ ] 本页继续保持 `DIFF_REVIEW`，不能标记 `PASS`；卡片几何、边框、操作样式、头像和字体/图标光栅化仍存在可见差异。105 张画板汇总仍为 `105 DIFF_REVIEW / 0 UNMAPPED / 0 SIZE_MISMATCH / 0 PASS`，iconfont 继续为 `BLOCKED`。

## 64. 2026-08-23 Agent Budget Limit 当前画板证据复核

- [x] `687:918` `/chat?state=budget-limit` 已重新读取实时 Figma 画板并保存当前参考图 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/agent-budget-limit-current.png`；浏览器证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-budget-limit-browser-current.png`。
- [x] 浏览器运行时实测视口为 `1440×1024`、DPR `1.0000000149011612`、字体状态为 `loaded`、`document` 和 `body` 均无横向溢出、文字越界 `0`；`50,000 tokens`、`100%`、预计费用、追加预算和结束会话均存在。
- [x] 点击“追加 20,000 tokens”后的状态为“fixture 已记录追加预算动作，当前 Run 不会被伪造为新会话。”；真实模式继续使用既有预算追加接口，结束动作继续使用既有取消接口。
- [x] 前端左上角红、黄、绿窗口装饰点数量为 `0`；Figma 设计稿未修改。
- [x] `scripts/png-diff.mjs` 同尺寸比较结果：`differentPixels=373778`、差异比例 `25.3484%`、`MAE=3.7123`、`RMSE=18.7204`、最大通道差异 `240`；机器结果锚点为 `figma-105-diff-results.json#agent-budget-limit`。
- [ ] 本页继续保持 `DIFF_REVIEW`，不能标记 `PASS`；卡片几何、状态色、头像和字体/图标光栅化仍存在可见差异。105 张画板汇总仍为 `105 DIFF_REVIEW / 0 UNMAPPED / 0 SIZE_MISMATCH / 0 PASS`，iconfont 继续为 `BLOCKED`。

## 65. 2026-08-23 Agent Tool Failed Retryable 当前画板证据复核

- [x] `687:1439` `/chat?state=tool-failed-retryable` 已重新读取实时 Figma 画板并保存当前参考图 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/agent-tool-failed-retryable-current.png`；浏览器证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-tool-failed-retryable-browser-current.png`。
- [x] 浏览器运行时实测视口为 `1440×1024`、DPR `1.0000000149011612`、字体状态为 `loaded`、`document` 和 `body` 均无横向溢出、文字越界 `0`；工具超时、外部知识库不可用、错误码 `TOOL_TIMEOUT_001`、重试和跳过动作均存在。
- [x] 重试动作在 fixture 中只记录等待新工具事件；真实模式继续调用既有运行恢复接口，不把前端动作伪造成成功结果；跳过动作明确记录后续结果数据范围受限。
- [x] 前端左上角红、黄、绿窗口装饰点数量为 `0`；Figma 设计稿未修改。
- [x] `scripts/png-diff.mjs` 同尺寸比较结果：`differentPixels=319638`、差异比例 `21.6768%`、`MAE=3.0042`、`RMSE=15.9168`、最大通道差异 `245`；机器结果锚点为 `figma-105-diff-results.json#agent-tool-failed-retryable`。
- [ ] 本页继续保持 `DIFF_REVIEW`，不能标记 `PASS`；告警卡几何、颜色、头像和字体/图标光栅化仍存在可见差异。105 张画板汇总仍为 `105 DIFF_REVIEW / 0 UNMAPPED / 0 SIZE_MISMATCH / 0 PASS`，iconfont 继续为 `BLOCKED`。

## 66. 2026-08-23 Agent Safety Degraded 当前画板证据复核

- [x] `687:1563` `/chat?state=safety-degraded` 已重新读取实时 Figma 画板并保存当前参考图 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/agent-safety-degraded-current.png`；浏览器证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-safety-degraded-browser-current.png`。
- [x] 浏览器运行时实测视口为 `1440×1024`、DPR `1.0000000149011612`、字体状态为 `loaded`、`document` 和 `body` 均无横向溢出、文字越界 `0`；安全降级警告、有限数据说明、个人高血压条件未完整应用提示和追问入口均存在。
- [x] 追问输入保持可用；页面明确说明结果基于有限数据，未把降级结果包装成完整分析或完整引用。
- [x] 前端左上角红、黄、绿窗口装饰点数量为 `0`；Figma 设计稿未修改。
- [x] `scripts/png-diff.mjs` 同尺寸比较结果：`differentPixels=377335`、差异比例 `25.5897%`、`MAE=3.3499`、`RMSE=16.7858`、最大通道差异 `249`；机器结果锚点为 `figma-105-diff-results.json#agent-safety-degraded`。
- [ ] 本页继续保持 `DIFF_REVIEW`，不能标记 `PASS`；告警层级、卡片几何、头像和字体/图标光栅化仍存在可见差异。105 张画板汇总仍为 `105 DIFF_REVIEW / 0 UNMAPPED / 0 SIZE_MISMATCH / 0 PASS`，iconfont 继续为 `BLOCKED`。

## 67. 2026-08-23 Agent User Cancelled 当前画板证据复核

- [x] `687:1684` `/chat?state=user-cancelled` 已重新读取实时 Figma 画板并保存当前参考图 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/agent-user-cancelled-current.png`；浏览器证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-user-cancelled-browser-current.png`。
- [x] 浏览器运行时实测视口为 `1440×1024`、DPR `1.0000000149011612`、字体状态为 `loaded`、`document` 和 `body` 均无横向溢出、文字越界 `0`；已接收部分文本、用户取消原因和重新开始入口均存在。
- [x] 运行时检查确认页面没有“运行失败”文案；重新开始动作显示“已准备重新开始；真实运行需要由后端创建新的 Run。”，未伪造新的运行结果。
- [x] 前端左上角红、黄、绿窗口装饰点数量为 `0`；Figma 设计稿未修改。
- [x] `scripts/png-diff.mjs` 同尺寸比较结果：`differentPixels=272934`、差异比例 `18.5095%`、`MAE=2.3318`、`RMSE=14.4348`、最大通道差异 `235`；机器结果锚点为 `figma-105-diff-results.json#agent-user-cancelled`。
- [ ] 本页继续保持 `DIFF_REVIEW`，不能标记 `PASS`；取消后操作布局、头像和字体/图标光栅化仍存在可见差异。105 张画板汇总仍为 `105 DIFF_REVIEW / 0 UNMAPPED / 0 SIZE_MISMATCH / 0 PASS`，iconfont 继续为 `BLOCKED`。

## 68. 2026-08-23 Agent SSE Reconnecting 当前画板证据复核

- [x] `687:1803` `/chat?state=sse-reconnecting` 已重新读取实时 Figma 画板并保存当前参考图 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/agent-sse-reconnecting-current.png`；浏览器证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/agent-sse-reconnecting-browser-current.png`。
- [x] 浏览器运行时实测视口为 `1440×1024`、DPR `1.0000000149011612`、字体状态为 `loaded`、`document` 和 `body` 均无横向溢出、文字越界 `0`；“第 2 次重连尝试 (最多 5 次)”和刷新提示均存在。
- [x] Composer 在重连期间保持禁用，已显示的查询文本保留；真实 SSE 使用 `Last-Event-ID` 和 `sse_event_id` 去重，终态完成/失败/取消/取代后关闭连接，达到上限进入稳定错误状态。
- [x] 前端左上角红、黄、绿窗口装饰点数量为 `0`；Figma 设计稿未修改。
- [x] `scripts/png-diff.mjs` 同尺寸比较结果：`differentPixels=400092`、差异比例 `27.1330%`、`MAE=2.9698`、`RMSE=14.6906`、最大通道差异 `234`；机器结果锚点为 `figma-105-diff-results.json#agent-sse-reconnecting`。
- [ ] 本页继续保持 `DIFF_REVIEW`，不能标记 `PASS`；重连提示宽度、位置、头像和字体/图标光栅化仍存在可见差异。105 张画板汇总仍为 `105 DIFF_REVIEW / 0 UNMAPPED / 0 SIZE_MISMATCH / 0 PASS`，iconfont 继续为 `BLOCKED`。

## 69. 2026-08-23 摄入分析错误态当前画板收口

- [x] `692:2139` `/analysis?state=error` 已重新读取当前 Figma 画板，并保存 `recaptured-figma/intake-analysis-error-current.png`；浏览器证据为 `recaptured/intake-analysis-error-browser-current.jpg` 和 RGBA 归一化 PNG。
- [x] 页面错误态不再显示 Figma 未包含的“自定义范围”和“全部餐次”控件；筛选容器收口为内容宽度，错误卡片高度、内部间距、重载按钮高度和警告色按当前 Figma 结构调整。
- [x] 浏览器实测视口为 `1440×1024`、DPR `1.0000000149011612`、字体 `loaded`、根节点无横向溢出、文字越界 `0`；前端左上角红黄绿窗口装饰点仍为 `0`，Figma 设计稿未修改。
- [x] `scripts/png-diff.mjs` 同尺寸结果：`differentPixels=177158`、差异比例 `12.0143%`、`MAE=1.5468`、`RMSE=12.2186`、最大通道差异 `230`；独立结果见 `intake-analysis-error-current-diff.json`。
- [ ] 本页继续保持 `DIFF_REVIEW`，剩余差异主要为头像、侧栏/图标光栅化和字体渲染；不能标记像素级 `PASS`。iconfont 继续为 `BLOCKED`。

## 70. 2026-08-23 摄入分析加载态指标骨架对齐

- [x] 实时读取 Figma 节点 `692:1901`，前端入口为 `/analysis?state=loading`；Figma 参考图为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/intake-analysis-loading-current.png`。
- [x] 按 Figma 结构新增 loading 专用指标区域：三张卡均为 `126px` 高，卡内保持 `20px` padding、`12px` 间距、`32px` 主骨架和 `16px` 详情骨架；普通分析指标卡不受影响。
- [x] 浏览器实测三张指标卡均为 `126px` 高，指标容器为 `1116×126px`；视口为 `1440×1024`，字体加载完成，页面无横向溢出。
- [x] 浏览器原始截图为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/intake-analysis-loading-browser-current.jpg`，RGBA 证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/intake-analysis-loading-browser-current-rgba.png`。
- [x] `scripts/png-diff.mjs` 同尺寸结果：`differentPixels=447209`、差异比例 `30.3283%`、`MAE=2.2087`、`RMSE=12.5071`、最大通道差异 `230`；结果锚点为 `figma-105-diff-results.json#intake-analysis-loading`，独立结果为 `intake-analysis-loading-current-diff.json`。
- [x] `AnalysisPage.test.tsx` loading/error/empty 定向测试 `4/4`，`npm run typecheck` 和 `git diff --check` 通过。
- [ ] 本页继续保持 `DIFF_REVIEW`，剩余导航上下文、头像、字体和图标光栅化差异不能被本次 loading 骨架对齐覆盖；不能标记 `PASS`。
- [ ] iconfont 实体包、CSS/Unicode 映射、来源和许可证仍为 `BLOCKED`；Figma 设计稿未修改，前端左上角红黄绿窗口装饰点仍为 `0`，业务状态圆点保留。

## 71. 2026-08-23 摄入分析空态图标资源对齐

- [x] 实时读取 Figma 节点 `692:2026`，前端入口为 `/analysis?state=empty`；当前 Figma 参考图为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured-figma/intake-analysis-empty-current.png`。
- [x] 空态图表区继续保持 Figma 的 `60px` padding、`64px` 图标容器、`20px` 内容间距、标题/说明/操作层级；空态图标改为 Figma 节点返回的真实 SVG `public/assets/figma/analysis/intake-analysis-empty-chart-column.svg`。
- [x] 浏览器实测空态图表卡为 `1116px` 宽、内容区域为 `1066.4×320px`，视口为 `1440×1024`，页面无横向溢出；浏览器原始截图为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/intake-analysis-empty-browser-current.jpg`，RGBA 证据已生成。
- [x] `scripts/png-diff.mjs` 同尺寸结果：`differentPixels=274336`、差异比例 `18.6046%`、`MAE=2.4987`、`RMSE=15.8118`、最大通道差异 `237`；结果锚点为 `figma-105-diff-results.json#intake-analysis-empty`，独立结果为 `intake-analysis-empty-current-diff.json`。
- [x] `AnalysisPage.test.tsx` 定向测试 `4/4`，新增真实 Figma 图标资源路径断言；`npm run typecheck`、Prettier 和 `git diff --check` 通过。
- [ ] 本页继续保持 `DIFF_REVIEW`，剩余头像、字体和图标/浏览器光栅化差异不能标记 `PASS`；iconfont 实体资源继续为 `BLOCKED`。

## 72. 2026-08-23 餐食规划列表操作按钮高度复核

- [x] 依据 Figma 节点 `692:2762`、`692:2779`、`692:2796` 的 `px=14、py=8、36px` 操作 frame，将三个“进入计划”按钮显式设为 `height=36px`，覆盖 shadcn 默认 `h-10`，菜单按钮保持 `36px`。
- [x] 浏览器实测三个操作组的按钮均为 `36×81.6px`，菜单按钮均为 `36×36px`，共同垂直居中且间距保持 `16px`；页面无横向溢出。
- [x] 最新 PNG diff：`differentPixels=384908`、差异比例 `26.1032%`、`MAE=3.8917`、`RMSE=19.4005`、最大通道差异 `234`。
- [ ] 该画板继续保持 `DIFF_REVIEW`；本项完成按钮 frame 几何对齐，不代表整页像素级 `PASS`。

## 73. 2026-08-23 餐食规划列表更新时间文本行高复核

- [x] 依据 Figma 节点 `692:2760`、`692:2777`、`692:2794`，将三个“最后修改”文本保持为 `12px`、`font-weight=400`、`line-height=normal`、`#6b7280`，不再沿用 `1.3` 的共享行高。
- [x] 浏览器实测首张计划卡更新时间文本为 `12px / 400 / normal`，高度 `16.8px`；卡片操作按钮仍为 `36px`，页面无横向溢出。
- [x] 最新 PNG diff：`differentPixels=384990`、差异比例 `26.1088%`、`MAE=3.8937`、`RMSE=19.4070`、最大通道差异 `234`。
- [ ] 该画板继续保持 `DIFF_REVIEW`；本项只完成 Figma 指定的更新时间文本行高，不代表整页像素级 `PASS`。

## 74. 2026-08-23 餐食规划列表说明面板字体排版复核

- [x] 依据 Figma 节点 `976:3` 至 `976:8`，将说明面板字体入口调整为 `Noto Sans SC`；标题行盒为 `22px`，普通说明和绿色操作行盒为 `18px`，灰色辅助行也固定为 `18px`。
- [x] 浏览器实测面板外框仍为 `x=260,y=802,width=1116,height=222`，五个文字行保持顶部堆叠和 `8px` 间距；视口 `1440×1024`、字体 `loaded`、无横向溢出。
- [x] 最新 PNG diff：`differentPixels=384960`、差异比例 `26.1068%`、`MAE=3.8953`、`RMSE=19.4073`、最大通道差异 `234`。
- [ ] 该画板继续保持 `DIFF_REVIEW`；本项只完成说明面板字体和行盒对齐，不代表整页像素级 `PASS`。

## 75. 2026-08-23 餐食规划列表状态徽章文字复核

- [x] 依据 Figma 节点 `692:2753`、`692:2770`、`692:2787`，将三个状态徽章文字设为 `11px`、`font-weight=700`、`line-height=normal`；背景色和语义颜色保持进行中/草稿/已归档的独立值。
- [x] 浏览器实测三个状态徽章均为 `24px` 高、`11px` 粗体，页面视口 `1440×1024`、字体 `loaded`、无横向溢出。
- [x] 最新 PNG diff：`differentPixels=412502`、差异比例 `27.9746%`、`MAE=4.0662`、`RMSE=19.9112`、最大通道差异 `236`。
- [ ] 该画板继续保持 `DIFF_REVIEW`；本项按 Figma 文字行盒完成，不代表整页像素级 `PASS`。

## 76. 2026-08-23 餐食规划列表新建按钮文案结构对齐

- [x] 重新读取 Figma 节点 `692:2739/2740`，确认顶部操作是单一文本节点 `+ 新建膳食计划`，不是图标与文本的组合。
- [x] `/planning?state=list` 已移除该按钮中的 Lucide `Plus`，改为 Figma 对应的连续文案；真实导航仍进入 `wizard-step1`，真实模式测试已同步新的可访问名称。
- [x] 浏览器实测按钮名称为 `+ 新建膳食计划`，旧名称不存在；视口为 `1440×1024`，字体为 `loaded`，页面无横向溢出。
- [x] 前端左上角红、黄、绿窗口装饰点仍为 `0`，因此没有删除任何无关业务圆点；Figma 设计稿未修改。
- [x] 最新同尺寸 PNG diff：`differentPixels=411835`、差异比例 `27.9293%`、`MAE=4.0463`、`RMSE=19.8687`、最大通道差异 `236`；结果已写入 `meal-plan-list-current-diff.json` 和 `figma-105-diff-results.json#meal-plan-list`。
- [ ] 本次 in-app 浏览器实际 DPR 为 `1.25`，DPR 1 门禁未通过；同时卡片几何、内容密度、字体与图标光栅化仍有差异，画板继续保持 `DIFF_REVIEW`，不能标记 `PASS`。

## 77. 2026-08-23 餐食规划列表计划卡水平布局对齐

- [x] 依据 Figma 节点 `692:2749`、`692:2750` 和 `692:2761`，确认计划卡内容列宽 `936px`、操作组从卡片内部 `x=960` 开始，卡片容器不设置额外 flex gap。
- [x] 前端移除 `.planListCard` 的额外 `gap:24px`；浏览器实测内容列由 `908.8px` 增至 `932.8px`，操作组仍保持右侧对齐，卡片宽度 `1116px` 不变。
- [x] 本次调整后的截图与前一证据 SHA-256 相同，PNG diff 如实保持 `27.9293% / MAE 4.0463 / RMSE 19.8687`；未用无变化的 diff 数字冒充像素改善。
- [ ] 计划卡垂直行盒和总高度仍需后续按 Figma `133px` 卡片节点继续收口；当前画板保持 `DIFF_REVIEW`，DPR 1 门禁仍未通过，不能标记 `PASS`。

## 78. 2026-08-23 餐食规划列表计划卡文字行盒对齐

- [x] 依据 Figma `692:2750`、`692:2751`、`692:2756`、`692:2757` 和状态/标签子节点，将计划卡标题行、状态徽章、日期、详情、信息标签和更新时间分别收口到 `22px / 21px / 16px / 17px / 22px / 14px`。
- [x] 浏览器实测计划卡主列高度为 `85px`，与 Figma `692:2750` 的 `85px` 一致；三张卡文字行盒均无溢出，页面无横向溢出。
- [x] 最新同尺寸 PNG diff：`differentPixels=347138`、差异比例 `23.5418%`、`MAE=3.5356`、`RMSE=18.2830`、最大通道差异 `234`；结果已更新到 `meal-plan-list-current-diff.json` 和 `figma-105-diff-results.json#meal-plan-list`。
- [ ] 卡片总高度当前为 `134.6px`，Figma 节点为 `133px`，剩余约 `1.6px` 来自边框布局处理；画板继续 `DIFF_REVIEW`，不能标记 `PASS`。

## 83. 2026-08-23 摄入分析空态指标卡高度对齐

- [x] 重新读取 Figma 节点 `692:2026`，确认三张空态指标卡目标高度为 `100px`；前端仅对 Figma fixture 的分析区域增加作用域，将指标容器和三张卡从 `107px` 对齐为 `100px`，真实模式和其他分析状态不受影响。
- [x] 浏览器实测三张指标卡均为 `100px`，图表卡从原 `y=295px` 调整为 `y=288px`，与 Figma `692:2129` 的位置一致；页面 `1440×1024` 无横向溢出，当前前端没有左上角红黄绿窗口装饰点，业务状态圆点保留，Figma 设计稿未修改。
- [x] 当前浏览器截图为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/intake-analysis-empty-browser-2026-08-23.jpg`，RGBA 证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/intake-analysis-empty-browser-current-rgba.png`；同尺寸 diff 为 `16.6444% / MAE 1.8523 / RMSE 13.0926 / maxChannelDelta 233`，已同步独立 diff 和 `figma-105-diff-results.json#intake-analysis-empty`。
- [x] `AnalysisPage.test.tsx` 定向测试 `4/4`、`npm run typecheck`、构建、目标文件 Prettier 和 `git diff --check` 通过。
- [ ] 该画板继续保持 `DIFF_REVIEW`：图表空态区域当前仍为 `320px` 高，Figma 目标为 `308px`；当前 in-app 浏览器 DPR 为 `1.25`，不能关闭 DPR 1 门禁，也不能标记像素级 `PASS`。iconfont 继续为 `BLOCKED`。

## 83. 2026-08-23 摄入分析空态指标卡高度对齐

- [x] 重新读取 Figma 节点 `692:2026`，确认三张空态指标卡目标高度为 `100px`；前端仅对 Figma fixture 的分析区域增加作用域，将指标容器和三张卡从 `107px` 对齐为 `100px`，真实模式和其他分析状态不受影响。
- [x] 浏览器实测三张指标卡均为 `100px`，图表卡从原 `y=295px` 调整为 `y=288px`，与 Figma `692:2129` 的位置一致；页面 `1440×1024` 无横向溢出，当前前端没有左上角红黄绿窗口装饰点，业务状态圆点保留，Figma 设计稿未修改。
- [x] 当前浏览器截图为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/intake-analysis-empty-browser-2026-08-23.jpg`，RGBA 证据为 `foodmate-ui/.qa/figma-pixel-acceptance/recaptured/intake-analysis-empty-browser-current-rgba.png`；同尺寸 diff 为 `16.6444% / MAE 1.8523 / RMSE 13.0926 / maxChannelDelta 233`，已同步独立 diff 和 `figma-105-diff-results.json#intake-analysis-empty`。
- [x] `AnalysisPage.test.tsx` 定向测试 `4/4`、`npm run typecheck`、构建、目标文件 Prettier 和 `git diff --check` 通过。
- [ ] 该画板继续保持 `DIFF_REVIEW`：图表空态区域当前仍为 `320px` 高，Figma 目标为 `308px`；当前 in-app 浏览器 DPR 为 `1.25`，不能关闭 DPR 1 门禁，也不能标记像素级 `PASS`。iconfont 继续为 `BLOCKED`。
