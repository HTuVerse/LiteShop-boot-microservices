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

1. 复制 `inventory.example.yml`，记录生产拓扑用于验收。应用 IP 不需要写进 Prometheus 配置。
2. 在每台 Linux 主机安装 node_exporter，并用 `EXPORTER_KIND=host` 将每台主机注册为 Nacos 持久目标；每个数据库实例配置相应 exporter。项目已给 Java 服务接入 Actuator Prometheus 端点。
3. 将 `prometheus/prometheus.example.yml` 复制为 `prometheus/prometheus.yml`，只填写配置服务的**内网引导地址**。Prometheus 随后每 30 秒从 `/inner/config/monitor/discovery` 获取 Nacos 中的服务实例和承载主机。
4. 给配置服务设置 `PROMETHEUS_BASE_URL` 和随机生成的 `MONITOR_DISCOVERY_TOKEN`；将同一令牌放在 Prometheus 的 `discovery.token` 文件中，限制文件读取权限。
5. 对每个 MySQL、Redis、RabbitMQ 实例，部署 exporter 后执行 `register_exporter.py`。脚本将它们注册为 Nacos 持久实例，之后新地址会自动进入采集列表；停止 exporter 仍能触发失联告警。
6. 将 `prometheus/alertmanager.example.yml` 复制为 `prometheus/alertmanager.yml`，换成真实通知接收地址。可用 `docker compose -f compose.yml up -d` 启动监控组件；跨机器访问时将 `MONITOR_BIND_ADDRESS` 设置为监控机的内网 IP。
7. 在 `liteshop_user` 执行 `sql/vertical-split/103_platform_monitor_menu.sql` 并重新登录平台管理员账号。验证 Targets 全部为 UP、看板可见，并模拟单个主机、服务及数据库失联。

注册 exporter 示例（环境变量由部署系统注入，不要写入仓库）：

```sh
EXPORTER_KIND=mysql EXPORTER_HOST=10.0.0.12 EXPORTER_PORT=9104 \
NACOS_SERVER_ADDR=http://10.0.0.10:8848 DB_CLUSTER=primary \
python3 register_exporter.py
```

监控必须有一个固定引导地址：Prometheus 需要先找到配置服务，才能获得 Nacos 动态目标。仅凭应用注册信息无法猜到没有注册的独立数据库服务器；部署其 exporter 并执行注册脚本后，后续地址发现和状态更新自动完成。配置服务自身有固定的 bootstrap 抓取目标，避免发现接口宕机时所有目标悄然消失。

Prometheus 和 Alertmanager 的安装方式可由部署环境选择，配置模板不要求应用和监控容器运行在同一机器。应用可使用 `/actuator/health` 和 `/actuator/prometheus`，监控内网须允许 Prometheus 连到对应服务端口；业务网关不转发 `/actuator/**`。

验收重点：

- 新增一个 Nacos 服务实例，30 秒内进入 Prometheus 目标列表和看板。
- 关闭某服务的全部实例，确认服务缺席告警在规则持续时间后触发。
- 关闭单个应用副本并检查 Prometheus 目标变化：Nacos 临时实例被注销后，该副本会从自动发现列表移除，现有规则不能保证单副本失联告警。若需对固定副本数告警，部署时还需提供持久的预期实例清单或副本数指标。
- 关闭一个已持久注册的 node_exporter 或数据库 exporter，确认目标失联告警触发。
- 独立数据库 exporter 注册后，无需修改 Prometheus 的数据库目标列表即可采集。
- 模拟配置服务、主机、MySQL exporter 断连，确认告警通知和看板状态。
