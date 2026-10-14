# Spring Boot 4 / Spring AI 升级说明

## 版本基线

| 组件 | 版本 |
|---|---:|
| Java | 21 LTS |
| Spring Boot | 4.0.7 |
| Spring Framework | 7.0.8（由 Boot BOM 管理） |
| Spring Cloud | 2025.1.2 |
| Spring Cloud Alibaba | 2025.1.0.0 |
| Nacos Client | 3.1.1 |
| Spring AI | 2.0.1 |
| MyBatis-Plus | 3.5.17（Boot 4 starter） |
| Sa-Token | 1.46.0（Boot 4 starter） |

版本只在 `liteShop-dependencies/pom.xml` 统一管理，不要在业务模块重复锁定 Spring、Jackson、Netty、MySQL 或 RabbitMQ 版本。

## 已处理的破坏性变化

- Servlet 服务改用 Boot 4 模块化的 `spring-boot-starter-webmvc`；Gateway 改用 `spring-cloud-starter-gateway-server-webflux`。
- Gateway 路由配置迁移到 `spring.cloud.gateway.server.webflux.routes`。
- MyBatis-Plus 的 `IService` / `ServiceImpl` 导入迁移到 `com.baomidou.mybatisplus.spring.service`。
- Sa-Token 替换为 Boot 4 starter。
- 手工 JSON 数据绑定迁移到 Jackson 3；RabbitMQ 改用 `JacksonJsonMessageConverter`。
- `DataSourceAutoConfiguration` 使用 Boot 4 新包名 `org.springframework.boot.jdbc.autoconfigure`。
- 移除旧版手工锁定的 Netty、Jackson、RabbitMQ、MySQL、Quartz 等版本，交由 Boot BOM 对齐。
- 为 LoadBalancer 增加 Caffeine 生产缓存实现。

## AI 服务

AI 服务端口为 `9009`，Gateway 路由为 `/ai/**`。模型配置由平台后台保存到数据库并动态读取，
未配置模型时服务仍可正常启动：

```powershell
mvn -pl liteShop-ai-service spring-boot:run
```

接口：

- `GET /ai/status`
- `POST /ai/chat`，JSON：`{"message":"你好"}`
- `POST /ai/chat/stream`，请求体相同，响应为 SSE

OpenAI-compatible 平台只需在「网站配置 → AI 模型配置」填写 Base URL、模型 ID 和 API Key。
密钥经配置服务加密后保存，禁止提交到仓库或 Nacos。

## 部署前检查

1. 先在测试环境验证现有 Nacos Server 与 Nacos Client 3.1.1 的注册、心跳和配置推送；生产升级 Nacos 前先备份配置与命名空间。
2. 构建、运行和容器基础镜像统一使用 Java 21；Maven 以 `--release 21` 编译。
3. 回归 MySQL、Redis、RabbitMQ、Quartz、支付回调、短信、文件上传和 Netty WebSocket 等外部集成。
4. 配置 AI 请求超时、重试、限流、鉴权、配额和内容审计后再开放公网入口。
5. 发布前执行 `mvn clean verify`。

## 已执行验证

- 14 个 Reactor 项目执行 `mvn clean verify` 成功。
- AI 服务在关闭 Nacos、未配置模型的情况下成功启动；状态接口返回 200，聊天接口返回预期 503。
- Gateway 在关闭 Nacos 的情况下成功启动，并成功匹配 `/ai/**` 路由。
- 关键依赖树确认 Spring Boot、Spring Framework、Spring Cloud、Netty、Jackson 由 BOM 统一管理。
