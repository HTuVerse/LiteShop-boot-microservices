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

## 密钥处理

模板中的 `${...}` 是运行时环境变量占位符。Nacos 用于统一配置管理，但不应被当作专用密钥保险库。
数据库密码、JWT 密钥、支付私钥、邮件、短信和对象存储密钥应由部署环境注入；生产环境可进一步接入
Kubernetes Secret、Vault 或云厂商 KMS。

此前已写入仓库的真实密钥必须在对应平台轮换，删除明文并不能撤销已经泄露的凭据。

## 版本注意

Spring Cloud Alibaba 2025.1.x 必须使用 `spring.config.import`，不再支持 `bootstrap.yml`；旧的
`spring.cloud.nacos.config.shared-configs` 也已废弃。本项目使用两条 `nacos:` import 实现公共配置和服务配置叠加，
两条 import 与 Nacos 连接参数统一放在 `liteShop-nacos-starter` 的 `liteshop-nacos.yml` 中。

注意：`nacos:` import 的解析依赖同一文档中的 `spring.cloud.nacos.config.*` 连接参数
（Spring Boot ConfigData 的解析顺序限制），因此连接参数与 import 语句必须放在同一个文件里，
不要把它们拆开到不同文件。
