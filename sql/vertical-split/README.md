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
8. `11_add_database_driven_menus.sql`
9. `12_member_level_schemes.sql`
10. `13_website_config.sql`
11. `15_member_level_template_grants.sql`
12. `16_menu_scope.sql`
13. `16_operation_permissions.sql`
14. `17_menu_scope_numeric.sql`
15. `18_merge_table_console_into_cashier.sql`
16. `21_table_reservation_menu.sql`
17. `22_saas_entitlements.sql`
18. `25_saas_restaurant_plans.sql`
19. `26_saas_plan_catalog_ui.sql`
20. `26_message_announcements.sql`
21. `27_announcement_richtext_attachments.sql`
22. `28_announcement_publisher_name.sql`
23. `36_pricing_permissions.sql`
24. `43_tenant_analysis_menu.sql`
25. `44_tenant_analysis_dirty_queue.sql`
26. `45_ai_manager_phase1.sql`
27. `47_ai_model_runtime_config.sql`（平台 AI 配置，可在后台替换 Key、URL 和模型）
28. `48_ai_manager_saas_entitlement.sql`（AI 店长权益；标准版默认关闭，商业版和企业版生成含 AI 的新发布版本，并登记单独加购项）
29. `49_ai_manager_call_quota.sql`（AI 店长周期调用次数、100 次加购包和生成预占记录）
30. `50_ai_manager_history.sql`（AI 店长历史记录与租户/门店范围）
31. `52_tenant_payment_account.sql`（已有配置库升级：创建租户独立支付账户表；空库执行也安全）
32. `54_payment_account_menu.sql`（创建仅租户总部可见的支付账户菜单并授权）
33. `57_saas_menu_restructure.sql`（将套餐与权益提升为顶级菜单，并拆分套餐、功能和租户权益子菜单）
34. `46_seed_current_demo.sql`（清理全部租户及非必要平台用户，并生成当前功能模拟数据）
35. `71_platform_service_config.sql`（复用 `sys_website_config` 第 2 行保存平台消息配置、第 3 行保存文件配置，并添加记录用途字段）

存量库升级时，支付账户还需按历史顺序执行 `52_tenant_payment_account.sql`、`53_tenant_payment_account_v2_key.sql`；小程序凭据执行 `69_tenant_miniapp_credential.sql`，用户库执行 `70_customer_profile_region.sql`，平台运行参数执行 `71_platform_service_config.sql`，最后执行 `72_remove_sys_config.sql`。`72` 将旧小程序展示、H5、打印及支付回调迁入新表后删除 `sys_config`。全新库的 `01`、`06` 已包含 `53`、`69`、`70`、`72` 所需结构，不要重复执行这些存量升级脚本。

优惠券服务升级到明确配对叠加及长期有效时，存量库先执行 `81_coupon_stack_and_long_term.sql`，再启动新版优惠券服务；新建库的 `03_liteshop_coupon.sql` 已包含对应结构。旧券不会自动产生叠加配对，需在后台逐张配置。

积分商城需依次执行 `84_points_mall.sql`、`85_mall_mixed_payment.sql`、`88_mall_categories.sql`。88 号迁移新增租户自定义分类和商品分类字段；旧商品保持未分类，仍可在小程序“全部”中展示，后台编辑商品时可为其归类。

已经部署过含图片、主题色、小程序普通链接前缀和二维码默认平台列的旧版 `tenant_miniapp_credential` 时，额外执行 `73_remove_miniapp_visual_config.sql` 删除这些列。新版 `06` 和 `72` 不创建这些列，不执行 `73`。

前序脚本已在独立 MySQL 8.4 实例验证；新增的 48 号迁移需在目标环境执行后验收。新版 `01` 已包含账号初始化需要的多租户字段和门店授权表；新版 `02` 已包含连锁菜单、定价、加退菜及日汇总结构，空库不要再执行 `30`、`31`、`35`、`41`、`42` 等重复建表/加列迁移。
48 号迁移不会改变旧订阅绑定的套餐版本。已有商业版或企业版租户需由运营分配新版本，或单独加购；加购项价格留空，由运营定价并在线下完成计费后分配。
49 号迁移再次创建商业版与企业版新版本，分别包含每订阅周期 100/500 次生成额度；标准版默认 0 次。原 AI 店长加购升级为每周期增加 100 次的加购包，已生效的旧加购快照也会补齐次数。旧订阅仍需由运营切换到新版本；调用次数包随当前订阅周期重置，不跨周期结转。
部署新 AI 服务和用户服务之前先执行 49 号迁移，生成预占表供幂等重试、失败退款及 Token 用量记录使用。相同生成请求的网络重试须复用 `requestId`；已成功请求直接返回保存的结果。
继续执行 50 号迁移，为生成记录补充租户/门店范围、今日/本周/本月实际周期及创建人索引。AI 店长页面依赖这些字段恢复后台任务并展示当前周期的历史版本。
`10_add_permission_management_menu.sql` 是旧环境菜单补丁；`08` 已包含该菜单，空库初始化不要执行 `10`。

`01` 到 `06` 都会重建对应表，不能用于保留数据的生产升级。

当前数据库没有旧数据，因此不要执行：

- `07_multi_tenant_upgrade.sql`：只用于已经运行旧版表结构、需要保留数据的环境。
- `99_migrate_from_hongtu.sql`：只用于从旧 `hongtu` 单库迁移历史记录。

## 基础初始化账号

`08_seed_development.sql` 生成以下数据，统一初始密码为 `Admin@123456`：

| 身份 | tenantCode | 用户名 | 数据范围 |
|---|---|---|---|
| 平台超级管理员 | 不传 | `superadmin` | 全平台；可进入指定租户作用域 |
| 商户管理员 | `DEMO` | `tenant_admin` | 演示租户全部门店 |
| 门店管理员 | `DEMO` | `store_admin` | 已授权门店 |
| 门店员工 | `DEMO` | `store_staff` | 已授权门店 |
| 普通会员 | `DEMO` | `demo_user` | 自己的消费数据 |

脚本还会生成演示租户 `DEMO`、演示门店、全局会员等级、管理菜单、角色权限和基础租户配置。生产环境不得执行该种子脚本，开发环境首次登录后也应修改密码。

## 当前功能模拟数据

已完成当前结构及菜单升级的测试环境，停止业务服务和定时任务后，直接执行 `46_seed_current_demo.sql`。无需先执行 `32`，不要重跑会重建表的 `01`～`06`。

`46` 在删除前检查结构、菜单、AI 店长及商业版套餐；清理所有租户和租户业务数据，并删除 `superadmin` 之外的平台用户，随后生成 `CHAIN_TEST`。整个数据替换过程在同一事务内完成，失败会回滚。平台超级管理员、平台角色权限、平台会员模板、正式套餐和全局配置保留。公告目标/附件按所属公告清理，订单触发器产生的刷新队列也会清理。此脚本会替换全部租户内手工新增的数据，仅限本地开发/联调环境执行。

登录使用 `tenantCode=CHAIN_TEST`，统一密码 `Admin@123456`：

| 账号 | 用途 |
|---|---|
| `chain_admin` | 总部管理、菜单审批、定价、总部分析、AI店长 |
| `chain_flagship` | 中心旗舰店管理员 |
| `chain_airport` | 机场店管理员 |
| `chain_campus` | 大学城店管理员 |
| `chain_franchise` | 加盟店管理员 |
| `chain_staff` | 旗舰店员工及收银 |
| `chain_member` | 银卡会员；500积分、300元余额、优惠券及3次抽奖机会 |

平台账号仍为 `superadmin`，不传 tenantCode。`DEMO` 账号在执行 `46` 后会被清理，后续使用上面的连锁模拟账号。

模拟数据包含：

- 4家门店、4个分类、8道菜、23条门店菜品配置、3个分组、6条菜单审批/审计记录。
- 会员价、第2件优惠、门店分组直接促销、订单满减，以及未来/已过期/停用活动。
- 最近30天共720笔历史订单，另有2笔待支付订单；1442条订单项，240条加退菜事件，订单成本和价格调整快照。
- 120条门店日汇总、240条菜品日汇总；支付/部分退款订单参与分析，取消及待支付订单不计入销售，赠品单独统计。
- 8张餐桌、1个进行中的桌位会话、5条预约生命周期样例、2个小票预览模板。
- 独立会员体系、最新版商业版订阅（20家门店额度、AI 店长每周期100次）、优惠券、积分及余额流水、抽奖活动和4条公告。

时间以执行时刻为基准，重复执行会重新生成最近30天的数据；无需修改写死的历史日期。AI店长使用这些真实业务表中的模拟记录，模型连接仍需按 AI 服务文档配置。

已移除停用的 `23_saas_demo.sql` 和被替代的 `33_seed_chain_restaurant_test_data.sql`。`14_member_level_demo.sql` 保留为可选平台会员模板样例；它不参与当前连锁租户初始化。前端 `docs/sql` 下的同名脚本仅为升级参考，不需要重复执行。

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
- 已有餐饮库在启用桌位码自动生成前执行 `74_table_qr_columns.sql`；新建库的 `02_liteshop_restaurant.sql` 已包含这些字段。
- 部署统一订单状态的餐饮服务前执行 `29_unify_order_status.sql`，将旧的订单状态 `0-未支付` 迁移为 `1-已下单`；支付结果仍以 `payment_status` 为准。
- 部署连锁菜单第一阶段前执行 `30_chain_menu_phase1.sql`，新增总部/门店菜品来源、门店价格与库存覆盖、门店上下架/清退和菜单策略，并自动回填存量菜品关系。
- 第一阶段完成后执行 `31_chain_menu_phase2.sql`，增加门店分组、菜单变更审批、定时生效、审计记录和配置版本。
- 已执行连锁菜单脚本的环境继续执行 `34_extend_store_menu_policy.sql`，增加“门店自建分类”和“调整菜品分类”两项策略；脚本可安全重复执行。
- 统一定价中心为不兼容升级：先备份，再在餐饮库执行 `35_pricing_center_breaking_upgrade.sql`。它会将正常售价改为 `sale_price`、删除旧活动价字段，并新增会员价、数量活动和订单定价快照表；执行后必须同步部署新版 restaurant-service 与前端。
- 随后在用户库执行 `36_pricing_permissions.sql`，新增“定价中心”菜单及新增、编辑、删除权限，并把权限补给原有菜品管理角色。
- 已执行旧版 `31`/`35` 且数据库仍保存英文枚举值的环境，在餐饮库继续执行 `37_numeric_enum_upgrade.sql`；它会把定价、菜单变更和打印纸宽枚举统一迁移为数值编码。使用新版 `02_liteshop_restaurant.sql` 初始化的空库无需执行。
- 文件库存在旧 `local/oss` 数据时，在 `liteshop_file` 执行 `38_file_storage_enum_upgrade.sql`，并同步部署新版 file-service。
- 已部署定价中心的餐饮库继续执行 `39_pricing_store_group_scope.sql`，让定价活动可绑定门店分组；新版 `02` 初始化的空库无需执行。
- 部署新版经营分析前在餐饮库执行 `40_optimize_restaurant_analysis.sql`，补齐历史支付/取消时间并建立经营统计索引；新版 `02` 初始化的空库无需执行。
- 启用加退菜分析前在餐饮库执行 `41_add_order_dish_change_log.sql`；脚本可回填仍存在的历史加菜，历史退菜无法可靠追溯，将从新版服务上线后开始累计。
- 部署租户级经营分析前，先在餐饮库执行 `42_tenant_business_analysis.sql`，新增下单成本快照和按日汇总表；再在用户库执行 `43_tenant_analysis_menu.sql`，新增总部分析菜单及权限；最后在餐饮库执行 `44_tenant_analysis_dirty_queue.sql`，启用按“租户-门店-营业日”增量刷新的持久化队列。
- 部署 AI 店长第一阶段时，在用户库执行 `45_ai_manager_phase1.sql`，将原“经营助手”入口升级为“AI店长”并保留已有角色授权。
- 启用 AI 店长后台分析与历史恢复前，在用户库依次执行 `49_ai_manager_call_quota.sql` 和 `50_ai_manager_history.sql`，再同步部署用户服务、AI 服务与前端。
- 部署 AI 店长 V2 前，在用户库执行 `58_ai_manager_v2.sql`，新增经营顾问入口，并为 AI 生成记录补充脱敏失败原因。
- 启用 AI 店长工作台时，在用户库执行 `59_ai_manager_p1_workspace.sql`，新增会话与建议反馈能力。
- 将 AI 店长从“运营工具”拆为独立顶级菜单时，在用户库执行 `61_ai_manager_top_menu.sql`；脚本会保留原页面地址和角色功能授权，并把经营洞察、经营顾问移入新目录。
- 完成 AI 店长 P0 产品纠偏时，在用户库执行 `62_ai_manager_p0_product_correction.sql`；脚本会收敛菜单命名并隐藏独立经营问答入口。
- 移除旧经营监控功能时，在用户库执行 `65_remove_ai_intelligence.sql`，删除旧菜单权限及其阈值、预警和报告表。
- 已部署过旧行动中心的环境执行 `67_remove_ai_action_center.sql`，删除对应菜单权限及专属任务、经验数据表。
- 将智能问数恢复为 AI 店长子菜单时，在用户库执行 `66_ai_smart_query_menu.sql`；脚本沿用原经营顾问权限与路由，菜单名称更新为“AI 智能问数”。
- 部署订单支付 P0 加固前，在餐饮库执行 `51_order_payment_p0_hardening.sql`，记录订单库存预占状态；脚本会把存量已支付且未取消订单标记为已扣库存。
- 部署 P1 业务加固时，先在餐饮库执行 `52_order_p1_hardening.sql`（下单幂等、取餐码唯一、打印任务），再在优惠券库执行 `53_coupon_p1_hardening.sql`（订单核销绑定、消息消费幂等）。RabbitMQ 现有 `coupon.issue.queue` 需按新配置重建为带死信参数的队列后再启动消费者。
- 部署 P2 查询优化时，在餐饮库执行 `54_restaurant_p2_indexes.sql`，在优惠券库执行 `55_coupon_p2_indexes.sql`；上线后结合真实慢 SQL 和 `EXPLAIN` 复核索引使用情况。
- 启用租户直连支付前，在配置库执行 `52_tenant_payment_account.sql`，在用户库执行 `54_payment_account_menu.sql`，并为配置服务设置固定的 `CONFIG_CREDENTIAL_ENCRYPTION_KEY`。微信、支付宝支付回调地址还需分别配置为 HTTPS 公网地址；租户总部保存并启用渠道后，其下门店自动沿用且无权查看密钥。
- 上线会员成长明细前，在用户库执行 `93_user_exp_records.sql`，创建经验流水表并为已有经验的会员写入期初记录；随后同步部署用户服务和包含 `/user-exp-record/**` 的网关路由。
- 同步升级顾客点餐端：桌台列表/详情返回的 `scanToken` 必须随首次 `/table-session/scan` 请求提交；扫码成功后保存返回的 `sessionToken`，后续共享购物车、会话订单接口使用 `X-Table-Session-Token` 请求头，WebSocket 使用 `sessionNo` 与 `token` 查询参数。旧二维码若未携带 `scanToken` 将被拒绝，需重新生成。
- 需要重置本地测试环境时，执行 `46_seed_current_demo.sql`；`32_cleanup_old_test_tenants.sql` 仅用于需要清理测试租户但不生成新数据的场景。

- 再创建第二个测试租户，验证同名账号、同名门店及交叉 ID 访问。
- 用 `EXPLAIN` 检查订单、菜品、优惠券等主要列表查询是否命中 `tenant_id` 前缀索引。
- 生产数据量上来后再按慢 SQL 增删索引，不要为每个字段预建单列索引。
- 定时任务必须逐租户执行或显式使用受控的租户旁路，不能在无租户条件下直接更新全表。
