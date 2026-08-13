# FoodMate Figma 前端像素级验收报告

更新时间：2026-08-12

## 1. 结论

本报告记录两类不同验收结果：

1. Figma 文件内部结构、组件系统、Prototype 和画板截图回读已完成。
2. 前端代码与 Figma 画板的自动化像素差异目前只覆盖 30 个已建立映射的页面/状态，30 个结果均为 `DIFF_REVIEW`，不能标记为像素级通过。

因此当前不能宣称“Figma 105 张画板已全部完成前端像素级验收”。已经完成的是可复核的 Figma 全量结构验收和 30 个映射页面的差异证据收集。

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

以下结果来自 2026-08-12 及 2026-08-13 重新运行的 `png-diff.mjs`。当前认证页截图为 `1440×900`；其他历史页面按各自 Figma 目标尺寸记录。

| 页面/状态 | Figma 节点 | 尺寸 | 差异比例 | RMSE | 结论 |
|---|---|---:|---:|---:|---|
| Workspace Home | `640:256` | 1440×1024 | 42.26% | 20.09 | `DIFF_REVIEW` |
| Agent Chat | `640:428` | 1440×1024 | 24.19% | 16.51 | `DIFF_REVIEW` |
| Diet Records | `640:588` | 1440×1024 | 37.94% | 17.38 | `DIFF_REVIEW` |
| Intake Analysis | `640:773` | 1440×1024 | 28.07% | 18.50 | `DIFF_REVIEW` |
| Meal Planning | `640:901` | 1440×1024 | 24.50% | 16.00 | `DIFF_REVIEW` |
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

## 5. 未映射画板

Figma Design 页共有 105 张顶层画板。本轮仅有上表及认证补充共 30 个页面/状态具备独立前端截图映射，剩余 75 张画板记录为 `UNMAPPED`，包括但不限于：

- 登录、注册、找回密码及其它账户状态画板。
- 饮食记录、摄入分析、餐食规划的编辑、删除、失败、空态、确认和任务状态画板。
- 知识库批量上传与索引状态画板，以及其它尚未建立前端 fixture 的知识库状态。
- 个人中心更多确认层、设备、导出和注销状态画板。
- Admin 用户详情的详情态已映射；操作确认、操作审计、Run、Tool Call、SQL Audit、Trace 等其它独立状态画板仍未映射。
- User/Admin Component Gallery 和 Foundations 页面。

这些画板已经完成 Figma 内部截图或结构检查，但没有对应的前端独立路由/状态截图，因此不能进行程序化像素 diff。

## 6. 其它检查

- 页面级横向溢出检查：已覆盖多个桌面和移动视口，当前记录为通过；这只证明没有页面级横向溢出，不等于像素级通过。
- Figma 可见文字边界：此前全文件扫描未发现越界或零尺寸文本。
- Prototype：所有带目标的 reaction 目标均有效；该结果不等于浏览器端每条交互已经真实接通。
- 字体：生产构建已使用 `@fontsource/noto-sans-sc`、`@fontsource/space-mono` 和 `@fontsource/montserrat` 的真实 woff2 产物。
- iconfont：仍为 `BLOCKED`，因为实体字体包、CSS 映射、来源和授权尚未提供。

## 7. 后续验收门槛

1. 为剩余 75 张画板建立明确的路由、查询参数或状态 fixture 映射。
2. 使用同一视口、同一 DPR、同一字体加载完成条件重新采集截图。
3. 对每个映射页分别进行几何、文字、颜色、状态和像素差异复核。
4. 只有在证据和人工复核都满足时，才将单页从 `DIFF_REVIEW` 改为 `PASS`。
5. iconfont 资源登记必须在收到真实包、CSS、来源和许可证后单独关闭，不能用 Lucide 或虚构字体替代。

## 8. Knowledge 状态补充验收

本轮补充了 Knowledge 默认态、检索失败和来源不可用三种前端状态的独立浏览器证据。Figma 结构依据为 `795:838`、`795:968`、`795:1145`、`795:1151` 和 `795:1328`；状态卡在完整画板中的绝对位置均为 `x=550,y=300`、`600×260`，空态仍使用已有 `560×220` 画板。

| 状态 | Figma 证据 | 浏览器证据 | 结果 |
|---|---|---|---|
| 默认态 | `user-knowledge-default-figma-latest.png` | `user-knowledge-default-browser-rgba.png` | `DIFF_REVIEW` |
| 检索失败 | `user-knowledge-search-failed-figma-latest.png` | `user-knowledge-search-failed-browser-full-rgba.png` | `DIFF_REVIEW` |
| 来源不可用 | `user-knowledge-source-unavailable-figma-latest.png` | `user-knowledge-source-unavailable-browser-full-rgba.png` | `DIFF_REVIEW` |

默认态 Figma 节点本身是主区域 `1180×1024`，因此浏览器证据按 `x=260` 裁剪后比较；两个状态画板使用完整 `1440×1024` 截图。三组结果均使用 `scripts/png-diff.mjs`，没有将视觉接近写成 `PASS`。状态层的半透明遮罩、色条、状态标签、标题、技术字段和重试入口均已通过截图人工复核。

本轮只补齐前端状态映射和像素差异证据，不代表真实 RAG 检索、文档导入、ACL 过滤、引用详情接口或 iconfont 实体资源已经完成。

## 9. Admin User Detail 补充验收

本轮完成 Figma `801:215` 到 `/admin/users` 的独立前端映射。Figma 画板关键几何为：侧栏 `260px`、顶栏 `64px`、左侧用户列表 `x=284,y=88,w=692,h=912`、右侧详情 `x=996,y=88,w=420,h=912`；列表为 4 行 `60px`，详情卡内边距 `20px`，详情说明卡位于卡内 `x=19,y=454,w=380,h=220`。

前端 mock fixture 按 Figma 示例登记 `usr_098a1`、`usr_112b9`、`usr_774x2`、`usr_889d4`，包含角色、状态、邮箱、活跃会话数和选中用户详情。详情 Tab 顺序为 `资料`、`饮食`、`登录会话`、`历史`、`业务会话`；状态变更和撤销会话继续复用 Admin 页面已有的二次确认、提交中、成功/失败和审计状态机。真实模式仍调用 `/api/admin/users`、用户状态 PATCH 和撤销全部会话 POST，不替换真实响应。

证据文件：

- Figma：`admin-user-detail-figma.png`
- 浏览器原始截图：`admin-user-detail-browser.png`
- RGBA 归一化截图：`admin-user-detail-browser-rgba.png`
- diff：`1440×1024`，差异比例 `22.36%`，RMSE `17.62`，结论 `DIFF_REVIEW`

本轮浏览器验证还确认了 CSS 几何与 Figma 一致；浏览器 DPR 为 `1.25`，因此 diff 使用 RGBA 归一化副本，不把截图编码或 DPR 差异误报为页面结论。`重置凭证` 当前只有明确的未接入提示，不执行伪造请求。

## 10. Admin Operation Status 补充验收

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

## 11. 认证页面与异常状态代码迁移

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
