# FoodMate 项目规范

## 架构边界

- `foodmate-shared` 只放跨模块共享的契约、错误码、基础类型和小型工具，不依赖业务模块。
- `foodmate-application` 编排用例和事务，不直接依赖数据库表结构或具体基础设施客户端。
- `foodmate-infra` 实现持久化、消息、外部服务和文件存储适配器；接口由 application 或 shared 定义。
- `foodmate-api` 只负责 HTTP/SSE 参数转换、鉴权和响应包装，不写 SQL 或业务编排。
- `foodmate-bootstrap` 负责 Spring Boot 启动、配置和运行时装配。
- 模块之间通过接口和 DTO 通信；禁止为了复用实现类跨越上述职责边界。

## Java 代码约定

- 使用 Java 21 语言特性，文件统一 UTF-8，类和方法遵循 Google Java Format 的 AOSP 风格。
- import 按格式化工具排序；不要使用全限定类名规避 import。
- 一个方法只承担一个清晰职责；控制器保持薄，查询和写入放到 application/infra 对应边界。
- 构造函数注入依赖；不要在业务代码中使用静态可变状态或隐式获取 Spring Bean。
- 异常必须携带稳定错误码或明确上下文；不要吞掉异常，也不要用 `catch (Exception)` 隐藏失败原因。
- 对外 DTO、持久化模型和领域对象不要混用；字段变换集中在边界层完成。
- 公共类、公共方法和跨模块契约使用简洁 Javadoc；注释解释原因，不重复代码表面行为。

## 验证

- 提交前运行 `./mvnw verify`（Windows 使用 `mvnw.cmd verify`）。
- Spotless 在 `verify` 阶段检查全部 Java 源文件；格式问题先运行 `mvnw.cmd spotless:apply` 修复。
- 业务改动至少运行受影响模块的测试；涉及模块边界时补充或运行 ArchUnit 测试。
