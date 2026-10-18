# 资源访问与存储切换

## 数据与访问地址

`file_upload.storage_type`、`storage_profile_id`、`bucket`、`storage_key` 和 `visibility` 决定文件的真实位置与权限。新文件不再向 `file_upload.url` 写入公开 URL；接口仍提供派生的 `url` 字段，以兼容现有管理页和业务表单。旧记录的 `url` 暂时保留，用于无法推导 `storage_key` 的历史文件和业务引用检查。

先执行 76 号迁移，再执行 [`77_resource_storage_key_backfill.sql`](../sql/vertical-split/77_resource_storage_key_backfill.sql)。77 号迁移只回填符合历史随机文件名格式的 Key。末尾查询仍有结果时，逐条核对源站及对象 Key，不能直接把 URL 当作 Key。

## 统一公开入口

超级管理员可设置 `publicDeliveryBaseUrl`，例如 `https://static.example.com/images/resource`。公开资源接口返回 `https://static.example.com/images/resource/{文件ID}`。此路径须由资源域名反向代理到文件服务的 `/images/resource/{id}`；文件服务检查数据库权限后返回 302 到对应的 Local、MinIO、阿里云 OSS 或七牛云公开源站。文件字节由源站直接发送给浏览器，适合图片缓存和视频 Range 请求。权限检查响应使用 `no-store`，不要在代理或 CDN 上覆盖成长期缓存，否则公开改私有的撤销会延迟。

资源域名启用前，应先确认：

1. `https://static.example.com/images/resource/{id}` 可以到达文件服务，且普通访客无需登录即可收到跳转。
2. 每个公开 Bucket 的源站域名可被浏览器访问，并支持缓存及 Range；私有 Bucket 绝不能被该域名匿名访问。
3. MinIO 的 API Endpoint 用于服务器上传，浏览器可达 Endpoint 用于私有签名链接，两者的主机与路径必须符合签名校验要求。
4. 公开资源域名与对象存储源站域名最好分开。统一入口使用站点长期保留的域名，源站域名可以随驱动变化。

留空 `publicDeliveryBaseUrl` 时，接口继续从文件绑定的历史配置快照派生原驱动直链。配置统一入口后，新业务上传响应使用资源 ID 地址；原业务表中已保存的 MinIO、七牛或本地 URL 不会自动重写，因此必须继续保留旧域名或设置旧域名跳转。不要停止旧 Bucket，也不要删除旧配置快照。

## 私有文件

`GET /file-upload/resources/{id}/access` 对公开资源返回长期 URL，对私有资源仅允许上传人或超级管理员获取。对象存储返回约五分钟有效的签名 URL 和 `expiresAt`；本地存储返回 `/file-upload/resources/{id}/download`，该接口再次校验身份。临时签名 URL 不写入数据库。权限切换会复制对象到隔离 Bucket 或目录，删除旧位置后才更新数据库；删除前会检查现有 URL 业务引用。

## MinIO 切换到 OSS

在后台保存并测试 OSS 配置后，把默认驱动切到阿里云 OSS 或七牛云。旧 MinIO 文件的 `storage_type`、`storage_profile_id`、`bucket`、`storage_key` 均保持原值，新上传文件才绑定新驱动与快照。统一入口用文件 ID 定位历史记录，再按其原快照跳转，不依赖当前默认驱动。切换默认驱动不表示迁移文件；正式迁移须另做对象复制、校验、更新文件存储定位字段及回滚方案。

当前菜品、Banner 等业务表仍保留 URL 字段。资源与业务的关联统一记录在 `file_resource_reference`，业务接口按资源 ID 派生响应 URL；历史 URL 的回填和删除保护见 [资源引用迁移说明](resource-reference-migration.md)。资源域名若更换，须保留旧域名别名，直至历史引用完成核对。
