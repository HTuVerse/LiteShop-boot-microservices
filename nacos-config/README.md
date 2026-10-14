# LiteShop Nacos 配置中心

## 配置分层

- `liteshop-shared.yml`（Nacos）：数据库、Redis、RabbitMQ、MyBatis-Plus、Sa-Token 公共配置。
  所有服务统一导入；未引入对应 starter 的服务（gateway、ai-service）不会绑定这些属性，无副作用。
- `${spring.application.name}.yml`（Nacos）：每个服务自己的网关路由、支付、短信、文件或 AI 配置，
  优先级高于 `liteshop-shared.yml`。
- `liteShop-nacos-starter` 模块的 `liteshop-nacos.yml`（classpath）：**唯一**的 Nacos 连接参数
  （地址/命名空间/分组/账号）与两条 `nacos:` import 所在地。修改 Nacos 接入方式只改这一个文件。
- 各模块本地 `application.yml`：仅保留端口、服务名和一行
  `spring.config.import: "classpath:liteshop-nacos.yml"`。

模板文件名中的 `.example` 只用于仓库存放；发布时脚本会自动移除，例如
`liteshop-shared.example.yml` 发布为 Data ID `liteshop-shared.yml`。

## 发布模板

```powershell
.\nacos-config\publish.ps1 `
  -ServerAddr http://localhost:8848 `
  -Group LITESHOP_GROUP `
  -Namespace "" `
  -Username nacos `
  -Password your-nacos-password
```

应用启动时可设置：

```powershell
$env:NACOS_SERVER_ADDR = 'localhost:8848'
$env:NACOS_CONFIG_GROUP = 'LITESHOP_GROUP'
$env:NACOS_DISCOVERY_GROUP = 'LITESHOP_GROUP'
$env:NACOS_NAMESPACE = ''
$env:NACOS_USERNAME = 'nacos'
$env:NACOS_PASSWORD = 'your-nacos-password'
```

共享配置中的内部调用凭据默认使用 `liteshop-dev-secret`，仅用于无需额外环境变量的本地开发。
所有微服务都从同一个 `liteshop-shared.yml` 读取该值，不能为不同服务配置不同的凭据。
生产环境必须在启动每个微服务前设置同一个强随机值，例如：

```powershell
$env:INTERNAL_CALL_SECRET = '<至少 32 字符的强随机值>'
```

环境变量会覆盖开发默认值。修改模板后需重新执行 `nacos-config/publish.ps1`，并重启各微服务；
无需重启 Nacos。

## 本地 Docker RabbitMQ

本地 `rabbitmq` 容器的 AMQP 端口映射为 `5673:5672`，因此公共配置默认连接
`localhost:5673`。管理页面使用本机 `15673`，不能将管理端口用于 AMQP 连接。
其他部署应通过 `RABBITMQ_HOST`、`RABBITMQ_PORT` 指定实际可访问的地址和端口。
若应用也运行在 Docker 中且与 RabbitMQ 位于同一网络，应使用 RabbitMQ 的容器名和容器端口 `5672`。

若报 `Connection refused`，先检查容器是否运行以及宿主机端口映射是否匹配。
修改模板后需重新发布 `liteshop-shared.yml` 并重启受影响服务；已设置的环境变量会覆盖模板默认值。

## Feign 超时与重试

公共模板为 Feign 配置了 3 秒连接超时和 8 秒读取超时，支付客户端单独使用 5 秒连接超时、
20 秒读取超时。生产环境可通过 `FEIGN_*_TIMEOUT_MS`、`PAY_FEIGN_*_TIMEOUT_MS` 调整。
不要为所有 Feign 写请求开启统一自动重试；创建订单、扣库存、核销优惠券和退款等调用只能在具备
业务幂等键且调用方会查询最终状态时按接口单独设计重试。

## MySQL 连接认证

各服务的默认 JDBC URL 面向本地开发，使用 `useSSL=false&allowPublicKeyRetrieval=true`，
允许 Connector/J 获取 RSA 公钥，完成 MySQL `caching_sha2_password` 认证。
缺少此参数时可能报 `Public Key Retrieval is not allowed`，导致数据库连接和事务创建失败。

修改仓库模板后需要重新发布到 Nacos 并重启受影响服务。若设置了 `MYSQL_URL_RESTAURANT`
或其他 `MYSQL_URL_*` 环境变量，其完整 URL 会覆盖模板默认值，需同步调整对应环境变量。
也要检查 Nacos 配置中的 URL 是否已被替换为自定义值。

生产环境应通过各服务的 `MYSQL_URL_*` 配置使用 `sslMode=VERIFY_IDENTITY` 并配置可信证书，
或指定可信的 `serverRSAPublicKeyFile`；不要直接沿用本地开发的非 TLS 公钥获取方式。
认证参数说明见 [MySQL Connector/J 官方文档](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html)。

## 密钥处理

模板中的 `${...}` 是运行时环境变量占位符。Nacos 用于统一配置管理，但不应被当作专用密钥保险库。
数据库密码、JWT 密钥、支付私钥、邮件、短信和对象存储密钥应由部署环境注入；生产环境可进一步接入
Kubernetes Secret、Vault 或云厂商 KMS。

配置服务必须设置固定且足够随机的 `CONFIG_CREDENTIAL_ENCRYPTION_KEY`，用于加密数据库中的 AI Key 与租户支付密钥；
该值丢失或轮换后旧密文无法解密。餐饮服务还需设置公网 HTTPS 的 `WECHAT_ORDER_NOTIFY_URL` 与
`ALIPAY_ORDER_NOTIFY_URL`，填写不带租户 ID 的回调基础地址，系统会按订单租户自动追加路径参数。

此前已写入仓库的真实密钥必须在对应平台轮换，删除明文并不能撤销已经泄露的凭据。

## 版本注意

Spring Cloud Alibaba 2025.1.x 必须使用 `spring.config.import`，不再支持 `bootstrap.yml`；旧的
`spring.cloud.nacos.config.shared-configs` 也已废弃。本项目使用两条 `nacos:` import 实现公共配置和服务配置叠加，
两条 import 与 Nacos 连接参数统一放在 `liteShop-nacos-starter` 的 `liteshop-nacos.yml` 中。

注意：`nacos:` import 的解析依赖同一文档中的 `spring.cloud.nacos.config.*` 连接参数
（Spring Boot ConfigData 的解析顺序限制），因此连接参数与 import 语句必须放在同一个文件里，
不要把它们拆开到不同文件。
