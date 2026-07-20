# ADR-0004：服务端会话与 Cookie 认证

状态：已采纳

日期：2026-07-19

## 决策

FoodMate Web 端采用“服务端会话表 + Cookie”认证，不再由前端保存或发送 Access Token、Refresh Token 或 Bearer Token。

认证 Cookie：

- `foodmate_session`：随机会话 ID，`HttpOnly`，生产环境 `Secure`，`SameSite=Lax`。
- `foodmate_csrf`：随机 CSRF Token，非 HttpOnly，仅用于前端读取后写入 `X-CSRF-Token` 请求头。

数据库新增 `user_auth_sessions`，只保存会话 ID 和 CSRF Token 的 SHA-256 哈希，不保存明文凭据。每次登录独立创建会话，因此 Edge、Chrome、不同设备可同时登录，并可被分别撤销。

## 请求流程

```text
POST /api/auth/login
  -> 校验密码
  -> 创建 user_auth_sessions 记录
  -> Set-Cookie foodmate_session (HttpOnly)
  -> Set-Cookie foodmate_csrf

后续 GET
  -> 浏览器自动带 foodmate_session
  -> Java 查询有效会话并识别用户

后续 POST/PUT/PATCH/DELETE
  -> 浏览器自动带 foodmate_session
  -> 前端从 foodmate_csrf 读取值并添加 X-CSRF-Token
  -> Java 校验会话、Origin 和 CSRF Token
```

登录和注册是公开入口，不要求已有 CSRF Token。退出、创建会话、发送消息、创建/取消 Run、修改资料等已认证写操作必须通过 CSRF 校验。

## 安全边界

- `HttpOnly` 降低 XSS 直接窃取会话 ID 的风险。
- `Secure=true` 是生产默认；`local-stub` 显式关闭，供 HTTP 本地开发使用。
- `SameSite=Lax`、Origin 校验和 CSRF Token 共同降低跨站请求伪造风险。
- 账号状态变为 `disabled` 或 `locked` 后，现有会话不再可用。
- 退出当前设备会撤销当前会话记录。

## 不在本次范围

- 退出全部设备。
- 会话设备管理页面。
- IP/设备指纹风险识别。
- Redis 会话缓存、分布式限流与登录审计。
- OAuth2/OIDC、移动端和第三方 API 认证。

这些能力可在会话表语义稳定后逐步增加，当前不引入。

## 关联服务认证

Java Control Plane 与 Python Agent Runtime 不再使用共享 Runtime Token。双方使用各自 Ed25519 私钥签发 60 秒有效的 Service JWT，并使用对方 X.509 公钥验签。JWT 必须校验 `iss`、`aud`、`scope`、`exp`、`jti` 与 `kid`；私钥只由各自服务的部署 Secret 持有。
