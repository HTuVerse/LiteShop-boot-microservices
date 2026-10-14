# liteShop-ai-service

Spring AI 2.x 对话与 AI 店长服务，端口 `9009`，通过 Nacos 注册并由 Gateway 的 `/ai/**` 路由访问。AI 店长只通过内部 Feign 接口读取经过租户、门店权限裁剪的经营指标，不直接连接餐饮业务数据库。

包结构：

- `controller`：对外接口、权限和套餐校验。
- `application`：一次生成的编排，包括额度预占和范围校验。
- `prompt`：系统提示和任务模板。
- `model`：请求、结构化结果和返回前端的响应。
- `tool`：经营追问的只读工具。
- `client`：模型客户端，以及餐饮服务经营数据客户端。
- `config`：后台线程池和工具调用轮次。

模型、接口地址、API Key 和启用状态统一由「网站配置 → AI 模型配置」保存到数据库，
AI 服务按配置版本动态读取，不再从 YAML 或环境变量回退模型凭据：

```powershell
mvn -pl liteShop-ai-service -am spring-boot:run
```

对于 DeepSeek、通义千问、讯飞星辰或本地 vLLM 等 OpenAI-compatible 服务，在后台填写对应
Base URL、模型 ID 和 API Key 即可。系统统一调用兼容的 `/chat/completions` 接口；厂商若不兼容
OpenAI Chat Completions 协议，则需要单独实现适配器。

若运行环境不能直连模型厂商，可设置 `AI_PROXY_URL`，或使用标准的 `HTTPS_PROXY` /
`HTTP_PROXY` / `ALL_PROXY` 环境变量；`NO_PROXY` / `no_proxy` 用于排除无需代理的模型地址。
代理仅作用于模型请求，不影响服务间 Feign 调用。

提示词在 `src/main/resources/prompts/`，由 `PromptTemplate` 渲染。用户问题、营销目标和预算只进入用户模板，不进入系统提示。简报、菜品顾问和营销草稿把模型输出解析为对应结果类，再映射到 `content`、`evidence` 和 `actions`。经营追问不预取全量指标，只注册销售概况、菜品表现和授权门店三个只读工具；工具会重新校验门店范围，单次最多 3 次，不能发券、改价或上下架。

接口：

- `GET /ai/manager/status`：检查当前租户权益和模型是否启用。
- `GET /ai/manager/scope`：获取当前账号可用的租户/门店经营范围。
- `POST /ai/manager/briefing`：生成经营简报。
- `POST /ai/manager/dish-advice`：生成近30天菜品经营建议。
- `POST /ai/manager/marketing-draft`：生成营销方案草稿，不执行发券或改价。
- `POST /ai/manager/chat`：基于当前经营范围回答数据问题。

AI 店长请求示例：

```json
{
  "scopeType": "STORE",
  "storeId": 1001,
  "timeRange": "TODAY",
  "message": "今天最值得优先处理的问题是什么？"
}
```

模型未配置、Key 无效、额度耗尽或模型调用失败时，AI 店长返回不可用错误，不生成规则分析或硬编码建议。平台管理员获得网站配置中 AI 模型配置及额度处理提示；普通账号仅获得暂不可用提示。`GET /ai/manager/status` 检查服务器生效配置，额度和限流以实际调用结果为准。未鉴权的通用对话接口不再提供。

执行一次 `sql/vertical-split/47_ai_model_runtime_config.sql` 并部署本次配置服务、AI 服务及前端变更后，超级管理员可在「网站配置 → AI 模型配置」保存 Key、URL、模型名及启用状态。后续替换不需修改 YAML、发布 Nacos 或重启服务。已有 Key 不回显，输入留空则保留原值；密钥在数据库中以 AES-GCM 密文保存，仅鉴权内部接口向 AI 服务提供运行凭据。AI 服务每次请求读取配置版本，多实例均可在下一次请求时应用新配置，正在执行的请求继续使用原模型。

数据库版本为 0 表示尚未首次后台保存，此时模型不可用；保存后包括关闭状态均以后台配置为准。配置服务读取失败时返回暂不可用，不回退到旧凭据。加密凭据只使用 `liteshop.ai.config-encryption-key`，未配置时不能加密或读取 Key，也不会回退到 `liteshop.internal-call.secret`。各配置服务实例必须使用同一密钥；更换密钥后需重新保存 API Key。

上线步骤、数据边界及统计口径见 [`docs/ai-manager-phase1.md`](../docs/ai-manager-phase1.md)。AI 服务现在复用公共登录、租户会话和远程权限加载，需要与其他服务一致的 Redis/Sa-Token 配置和 `liteshop.internal-call.secret`；不需要餐饮数据库连接。

模型密钥只通过后台表单提交服务器并加密保存，不要提交到仓库或 Nacos。

AI 店长 V2 升级需依次执行 `sql/vertical-split/58_ai_manager_v2.sql` 和
`sql/vertical-split/59_ai_manager_p1_workspace.sql`。后者启用经营顾问会话与建议反馈。
