# FoodMate iconfont 实体资源登记

更新时间：2026-08-12

## 当前状态

**BLOCKED：未收到可验证的实体 iconfont 资源。**

当前 Figma 节点 `1065:2` 的 `Iconfont Resource Registry` 已登记阻塞状态。仓库和当前 Figma 文件中没有可验证的 `.woff`、`.woff2`、`.ttf`、`.otf`、`iconfont.css`、项目来源 URL 或许可证记录，因此本轮不创建虚假的 `@font-face`、`.icon-*` 类名或 Unicode codepoint。

## 已确认的使用边界

| 场景 | 当前实现 |
|---|---|
| send、stop、search、edit、delete、copy、download、refresh、expand | 使用 shadcn/Radix 组件中的 Lucide 命令图标 |
| food、nutrition、meal、ingredient、brand、领域状态 | 只有存在准确且授权明确的 iconfont 字形时才接入 |
| Figma 中已有且来源可追溯的 SVG | 可作为 iconfont 缺失时的回退资源，并登记来源和授权 |
| 无准确资源 | 使用许可明确的 SVG 或代码图标，不写原始 Unicode |

## 实体资源接收清单

收到资源后必须逐项填写，不允许只登记一个字体文件名：

| 字段 | 必填内容 | 当前值 |
|---|---|---|
| 资源包 | 文件名、版本、SHA-256 | 待提供 |
| 字体文件 | `.woff2`、`.woff`，必要时 `.ttf/.otf` | 缺失 |
| CSS 映射 | `iconfont.css` 或等价 class 映射 | 缺失 |
| 来源 | 项目主页、下载 URL、提交人 | 缺失 |
| 许可证 | SPDX 标识、原文链接、商用范围 | 缺失 |
| 授权范围 | Web、生产、再分发、修改权限 | 缺失 |
| glyph class | 例如 `icon-food-xxx` | 缺失 |
| Unicode codepoint | 十六进制 codepoint | 缺失 |
| weight | 字体 weight 或默认 `400` | 缺失 |
| 默认尺寸 | 16/20/24 等 | 缺失 |
| 语义名称 | 与 Figma 图层和产品语义一致的名称 | 缺失 |
| Figma 映射 | 对应节点 ID、字形截图或导出记录 | 缺失 |
| 无障碍标签 | `aria-label` 和 Tooltip 文案 | 缺失 |
| 验收 | 浅色/深色背景、加载失败、缩放和回退截图 | 未执行 |

## 接入规则

1. 资源包、CSS 和许可证必须来自同一可追溯版本。
2. 生产代码只通过一个 React iconfont wrapper 使用语义名称，不在页面中直接写字码。
3. 通用命令图标继续使用 Lucide，不为了凑数量导入 iconfont。
4. 每个 glyph 必须有 Figma 节点映射、默认尺寸、对比度和无障碍标签。
5. 字体加载失败时必须回退到已许可 SVG 或 Lucide，并在 UI 中保持可操作和可理解。
6. 接入前运行字体文件哈希、CSS class 扫描、页面截图和构建产物检查。

## 当前阻塞对交付的影响

- 不阻塞生产字体包：Noto Sans SC、Space Mono、Montserrat 已通过 `@fontsource` 作为文字字体接入。
- 不阻塞 shadcn/Radix 组件和 Lucide 命令图标使用。
- 阻塞 FoodMate 领域 iconfont 的最终实体登记、类名冻结、Figma 字形映射和生产接入。
## 2026-08-15 验收复核

- 状态继续保持 `BLOCKED`。
- 已核对仓库和 Figma 验收证据目录，仍未收到 `.woff2/.woff/.ttf/.otf` 实体包、`iconfont.css` 映射、来源 URL、SHA-256、SPDX/原始许可证和 glyph-Figma 映射。
- 本轮没有创建虚构 `@font-face`、class、Unicode codepoint 或 React wrapper；标准命令图标继续使用 Lucide，缺失领域图标继续使用可追溯的 SVG fallback。
- 关闭阻塞前仍需完成浅色/深色背景、字体加载失败、SVG/Lucide fallback、无障碍标签和生产构建产物检查。

## 2026-08-22 继续阻塞确认

- 状态仍为 `BLOCKED`；本轮 Chat 会话操作面板继续使用 Lucide 标准命令图标，没有接入未经登记的领域字形。
- 仍未收到实体字体包、完整 CSS/class/Unicode 映射、来源 URL、版本与 SHA-256、SPDX/原始许可证、授权范围或 glyph-Figma 映射，因此不创建虚构 `@font-face`、Unicode 或 React iconfont wrapper。
- 当前新增的 Figma/浏览器验收证据只覆盖页面视觉和交互，不构成 iconfont 资源到位证明；收到完整资料后仍需按本登记表执行哈希、构建产物、背景、加载失败、fallback 和无障碍验收。
