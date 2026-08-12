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
