# 审计签名密钥轮换

审计日志的 `key_version` 决定完整性校验所用的 HMAC 密钥。所有用户服务实例必须使用相同的密钥配置。

轮换时先将旧版本移入 `historical-keys`，再启用新版本和新密钥。例如：

```yaml
liteshop:
  audit:
    key-version: v2
    hmac-key: ${AUDIT_HMAC_KEY_V2}
    historical-keys:
      v1: ${AUDIT_HMAC_KEY_V1}
```

历史密钥须保留到对应审计记录及锚点的留存期结束。配置完成后，对旧记录和新记录分别运行完整性校验。每日新锚点包含密钥版本；轮换前生成的锚点应按当时的版本和密钥单独保存。
