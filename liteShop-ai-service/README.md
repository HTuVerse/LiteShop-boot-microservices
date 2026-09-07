# liteShop-ai-service

Spring AI 2.x 对话服务，端口 `9009`，通过 Nacos 注册并由 Gateway 的 `/ai/**` 路由访问。

默认关闭模型自动配置，因此没有密钥时服务也能启动：

```powershell
$env:AI_CHAT_PROVIDER = 'openai'
$env:AI_API_KEY = 'your-api-key'
$env:AI_BASE_URL = 'https://api.openai.com'
$env:AI_MODEL = 'gpt-5-mini'
mvn -pl liteShop-ai-service -am spring-boot:run
```

对于 DeepSeek、通义千问或本地 vLLM 等 OpenAI-compatible 服务，修改 `AI_BASE_URL` 和 `AI_MODEL` 即可。

接口：

- `GET /ai/status`：检查模型是否启用。
- `POST /ai/chat`：同步对话，请求体 `{"message":"..."}`。
- `POST /ai/chat/stream`：SSE 流式对话，请求体同上。

密钥仅通过环境变量或外部配置中心提供，不要提交到仓库。
