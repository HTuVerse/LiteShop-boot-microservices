# LiteShop 分布式监控部署清单

## 监控对象

仓库中有一个网关和十个业务服务，均通过 Nacos 注册。生产机器、实例地址和数据库拓扑必须由部署人员在 `inventory.example.yml` 的副本中填写；仓库中的开发默认地址不能当作生产目标。

| 类型 | 目标 | 采集方式 |
| --- | --- | --- |
| 主机 | 每台 Linux 服务器 | node_exporter，每台一个 |
| 应用 | gateway、user、coupon、mall、lottery、restaurant、pay、message、file、config、ai | 每个运行实例的 `/actuator/prometheus` |
| MySQL | 每个 MySQL **实例**，不是每个 schema | mysqld_exporter；主从节点分别采集 |
| Redis | 每个 Redis 实例 | redis_exporter |
| RabbitMQ | 每个 RabbitMQ 节点 | RabbitMQ Prometheus 插件 |

开发配置涉及 `liteshop_user`、`liteshop_coupon`、`liteshop_mall`、`liteshop_lottery`、`liteshop_restaurant`、`liteshop_pay`、`liteshop_message`、`liteshop_file`、`liteshop_config` 等业务库。多个 schema 如果在同一个 MySQL 实例上，共用一个 MySQL 采集目标。实际库名与实例分布以部署清单为准。

## 标签与访问范围

所有目标应标注 `environment`、`host`、`service` 或 `db_cluster`。`instance` 使用稳定且唯一的 `主机:端口`。不要把用户 ID、订单 ID、租户 ID 等高基数字段作为指标标签。

Prometheus、exporter 与 Actuator 仅允许监控内网访问；不要经公网或业务网关暴露采集接口。看板经 LiteShop 后端授权后查询指标。数据库 exporter 使用专用只读监控账号，凭据放在部署机密管理中，不提交仓库。

## 部署顺序

1. 复制 `inventory.example.yml`，填入真实主机、服务实例、数据库实例和通知接收人。
2. 在每台 Linux 主机安装 node_exporter；每个数据库实例配置相应 exporter；在每个 Java 服务启用 Actuator Prometheus 端点。
3. 将真实目标写入 `prometheus/targets/*.yml`，启动 Prometheus 与 Alertmanager。
4. 验证 Prometheus Targets 全部为 UP，再配置告警接收器和 LiteShop 监控接口的 `PROMETHEUS_BASE_URL`。
5. 模拟单个主机、服务和数据库目标离线，核对看板与告警。

Nacos 是业务服务注册中心。监控初期使用 Prometheus 文件发现目标；当实例动态扩缩容时，由部署流水线或 Nacos 适配器更新目标文件，避免把 Nacos 注册状态误当成实际可抓取状态。
