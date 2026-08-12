# FoodMate Figma 前端像素级验收报告

更新时间：2026-08-12

## 1. 结论

本报告记录两类不同验收结果：

1. Figma 文件内部结构、组件系统、Prototype 和画板截图回读已完成。
2. 前端代码与 Figma 画板的自动化像素差异目前只覆盖 14 个已建立映射的页面/状态，14 个结果均为 `DIFF_REVIEW`，不能标记为像素级通过。

因此当前不能宣称“Figma 105 张画板已全部完成前端像素级验收”。已经完成的是可复核的 Figma 全量结构验收和 14 个映射页面的差异证据收集。

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

以下结果来自 2026-08-12 重新运行的 `png-diff.mjs`。除 Login 外，历史截图为 `1440×1024`；Login 使用 Figma 目标尺寸 `1440×900`。

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
| Profile Basic | `806:1119` | 1440×1024 | 67.46% | 20.74 | `DIFF_REVIEW` |
| Profile Memories | `806:1281` | 1440×1024 | 50.35% | 23.05 | `DIFF_REVIEW` |
| Profile Security | `806:1445` | 1440×1024 | 60.25% | 19.68 | `DIFF_REVIEW` |
| Profile Privacy | `806:1585` | 1440×1024 | 37.09% | 17.96 | `DIFF_REVIEW` |
| Login | `647:214` | 1440×900 | 99.19% | 7.50 | `DIFF_REVIEW` |

Login 的高差异比例主要来自大面积抗锯齿、透明叠加和斜向背景边界；几何已按 Figma 读取结果对齐：表单 `400×471`，位置 `x=490,y=214.5`，品牌区 `163px`，字段区 `156px`，按钮 `52px`，分隔区 `56px`，注册行 `44px`。该页仍保留 `DIFF_REVIEW`，不将人工“基本重合”写成自动化 PASS。

证据目录：[`.qa/figma-pixel-acceptance`](../../foodmate-ui/.qa/figma-pixel-acceptance)

## 5. 未映射画板

Figma Design 页共有 105 张顶层画板。本轮仅有上表 14 个页面/状态具备独立前端截图映射，剩余 91 张画板记录为 `UNMAPPED`，包括但不限于：

- 登录、注册、找回密码及其它账户状态画板。
- 饮食记录、摄入分析、餐食规划的编辑、删除、失败、空态、确认和任务状态画板。
- 知识库检索失败、来源不可用、批量上传与索引状态画板。
- 个人中心更多确认层、设备、导出和注销状态画板。
- Admin 用户详情、操作确认、操作审计、Run、Tool Call、SQL Audit、Trace 等独立状态画板。
- User/Admin Component Gallery 和 Foundations 页面。

这些画板已经完成 Figma 内部截图或结构检查，但没有对应的前端独立路由/状态截图，因此不能进行程序化像素 diff。

## 6. 其它检查

- 页面级横向溢出检查：已覆盖多个桌面和移动视口，当前记录为通过；这只证明没有页面级横向溢出，不等于像素级通过。
- Figma 可见文字边界：此前全文件扫描未发现越界或零尺寸文本。
- Prototype：所有带目标的 reaction 目标均有效；该结果不等于浏览器端每条交互已经真实接通。
- 字体：生产构建已使用 `@fontsource/noto-sans-sc`、`@fontsource/space-mono` 和 `@fontsource/montserrat` 的真实 woff2 产物。
- iconfont：仍为 `BLOCKED`，因为实体字体包、CSS 映射、来源和授权尚未提供。

## 7. 后续验收门槛

1. 为剩余 91 张画板建立明确的路由、查询参数或状态 fixture 映射。
2. 使用同一视口、同一 DPR、同一字体加载完成条件重新采集截图。
3. 对每个映射页分别进行几何、文字、颜色、状态和像素差异复核。
4. 只有在证据和人工复核都满足时，才将单页从 `DIFF_REVIEW` 改为 `PASS`。
5. iconfont 资源登记必须在收到真实包、CSS、来源和许可证后单独关闭，不能用 Lucide 或虚构字体替代。
