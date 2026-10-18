# 统一资源引用迁移

## 范围与顺序

不要执行旧的 `78_business_resource_ids.sql`。新版不向业务表添加资源 ID 列；现有 URL 列继续服务旧后台和小程序。`file_upload` 的 `storage_type`、`storage_profile_id`、`bucket`、`storage_key` 仍决定真实文件位置，切换默认 Local、MinIO、七牛或阿里云 OSS 不修改历史文件。

1. 先对 `liteshop_file`、`liteshop_config`、`liteshop_restaurant`、`liteshop_user` 做同一时间点的数据库备份，保存 `SHOW CREATE TABLE file_upload` 与业务表定义，并在隔离环境确认备份可恢复。
2. 已使用对象存储的环境先确认 75、76、77 号资源迁移状态。随后执行 `sql/vertical-split/80_file_resource_reference.sql`；该脚本不会改写业务 URL，只将门店多图 URL 列扩为 `text`，并默认关闭资源物理删除与公开转私有。
3. 同步部署文件、配置、餐饮、用户服务以及后台页面。文件服务的内部引用接口须仅允许服务间访问；公网网关继续拦截 `/inner/**`。
4. 对下列回填接口逐页先以 `apply=false` 预览，再以 `apply=true` 回填。每次保存返回的 `nextId` 作为下一页 `afterId`，`scanned=0` 时结束。`limit` 上限为 200，重复执行只会跳过已有引用。

| 服务 | 接口 | `type` |
| --- | --- | --- |
| config | `POST /config/admin/resource-reference-backfill` | 无 |
| restaurant | `POST /restaurant/admin/resource-reference-backfill` | `dish`、`category`、`store`、`template`、`spec` |
| user | `POST /user/admin/resource-reference-backfill` | 无 |

全部接口要求 `SUPER_ADMIN`。示例参数为 `afterId=0&limit=100&apply=false`；切换为 `apply=true` 才建立引用。回填按完整旧 URL、受信任统一入口 URL 或可与文件源站生成地址完全相等的随机对象 Key 匹配。歧义、外部 URL 和无法确定格式的 JSON 保留原值，`unresolvedSample` 用于人工核对。多图按原位置记录，重复引用会得到不同 `sort_order`。

## 验证与打开删除

回填后逐类核对 `scanned`、`matched`、`unmatched`、`applied`，并检查历史未匹配清单。业务表的 URL 和 JSON 不应发生变化。资源详情的“查看引用”应列出相同图片在不同业务、字段及位置上的所有记录。重点抽查混合外部 URL、重复图片、菜品 JSON 图片、规格值图片和平台账号头像。

```sql
USE `liteshop_file`;
SELECT COUNT(*) AS orphan_references FROM file_resource_reference r
LEFT JOIN file_upload f ON f.id = r.resource_id WHERE f.id IS NULL;
SELECT tenant_id, business_type, business_id, field_name, sort_order, COUNT(*) AS candidate_count
FROM file_resource_reference
GROUP BY tenant_id, business_type, business_id, field_name, sort_order
HAVING COUNT(*) > 1;
SELECT deletion_state, COUNT(*) FROM file_upload GROUP BY deletion_state;
```

同一位置多行可由尚未完成的保存产生。先查业务实际 URL 并对账，不能直接删除引用记录。`DELETE_FAILED` 或 `CHANGE_FAILED` 需要核实数据库状态、对象源站和旧 Bucket 后恢复。当前代码不会自动清理这些失败记录，避免掩盖对象存储的部分成功。

确认所有写入入口已升级、历史回填完成且未匹配记录已人工处置后，才单独执行：

```sql
UPDATE `liteshop_file`.`file_resource_reference_control`
SET `deletion_enabled` = 1 WHERE `id` = 1;
```

文件服务删除时会先锁定资源行并切为 `DELETING`，引用登记只接受 `ACTIVE`，外键禁止删除仍有引用的文件行。业务跨库保存先登记新引用、提交后清理旧引用；异常中断可能留下多余引用，这会阻止删除，应按实际业务记录对账后处理。不要让绕过业务服务的脚本直接修改图片 URL，否则引用表无法同步。
