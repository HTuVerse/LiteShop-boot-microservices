# 垂直拆分数据库初始化说明

当前目录按“平台 → 租户（商户）→ 门店 → 业务数据”的共享表多租户模型设计，目标数据库为 MySQL 8.0。

## 空库初始化（当前重构项目使用）

请按下面顺序执行：

1. `01_liteshop_user.sql`
2. `02_liteshop_restaurant.sql`
3. `03_liteshop_coupon.sql`
4. `04_liteshop_lottery.sql`
5. `05_liteshop_file.sql`
6. `06_liteshop_config.sql`
7. `08_seed_development.sql`（仅开发/联调环境）

`01` 到 `06` 都会重建对应表，不能用于保留数据的生产升级。

当前数据库没有旧数据，因此不要执行：

- `07_multi_tenant_upgrade.sql`：只用于已经运行旧版表结构、需要保留数据的环境。
- `99_migrate_from_hongtu.sql`：只用于从旧 `hongtu` 单库迁移历史记录。

## 开发账号

`08_seed_development.sql` 生成以下数据，统一初始密码为 `Admin@123456`：

| 身份 | tenantCode | 用户名 | 数据范围 |
|---|---|---|---|
| 平台超级管理员 | 不传 | `superadmin` | 全平台；可进入指定租户作用域 |
| 商户管理员 | `DEMO` | `tenant_admin` | 演示租户全部门店 |
| 门店管理员 | `DEMO` | `store_admin` | 已授权门店 |
| 门店员工 | `DEMO` | `store_staff` | 已授权门店 |
| 普通会员 | `DEMO` | `demo_user` | 自己的消费数据 |

脚本还会生成演示租户 `DEMO`、演示门店、全局会员等级、管理菜单、角色权限和基础租户配置。生产环境不得执行该种子脚本，开发环境首次登录后也应修改密码。

## 多租户数据库约束

- 平台超级管理员和平台角色的 `tenant_id` 为 `NULL`，业务数据的 `tenant_id` 必须非空。
- 业务表不设置 `tenant_id DEFAULT 1`；调用链缺少租户上下文时，写入必须失败。
- 高频复合索引以 `tenant_id` 开头，使租户过滤后的列表、状态和时间查询可命中索引。
- `sys_user`、`sys_role` 使用生成列 `tenant_scope_id` 处理 MySQL 唯一索引的 `NULL` 语义，确保平台账号/角色同样唯一。
- 垂直拆库后不建立跨库外键；租户、门店和业务归属由应用事务校验。删除租户采用停用/归档，不做级联物理删除。
- 所有业务主键由应用的雪花 ID 生成；多实例部署时，每个实例必须配置不同的 `worker-id` / `datacenter-id`。

## 上线前建议

- 已执行旧版“餐台控制台”菜单的环境，发布本版前执行 `18_merge_table_console_into_cashier.sql`，将原角色授权迁移至“餐桌收银”并移除重复入口。
- 已有 `table_info` 数据的环境执行 `19_table_reservation_details.sql`，补充当前预留的手机号、预计到店时间和备注字段；空库初始化已包含这些字段，无需重复执行。

- 再创建第二个测试租户，验证同名账号、同名门店及交叉 ID 访问。
- 用 `EXPLAIN` 检查订单、菜品、优惠券等主要列表查询是否命中 `tenant_id` 前缀索引。
- 生产数据量上来后再按慢 SQL 增删索引，不要为每个字段预建单列索引。
- 定时任务必须逐租户执行或显式使用受控的租户旁路，不能在无租户条件下直接更新全表。
