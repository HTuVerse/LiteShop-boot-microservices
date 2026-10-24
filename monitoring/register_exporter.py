"""将 exporter 注册为 Nacos 持久实例，供 LiteShop 监控自动发现。

部署 exporter 后运行一次。持续可用性由 Prometheus 的 up 指标检查，
故注册为持久实例，采集端点故障时仍可保留目标并触发告警。
"""

import json
import os
import sys
import urllib.parse
import urllib.request


SERVICES = {
    "host": "liteshop-node-exporter",
    "mysql": "liteshop-mysql-exporter",
    "redis": "liteshop-redis-exporter",
    "rabbitmq": "liteshop-rabbitmq-exporter",
}


def required(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ValueError(f"缺少环境变量 {name}")
    return value


def main() -> None:
    kind = required("EXPORTER_KIND").lower()
    if kind not in SERVICES:
        raise ValueError("EXPORTER_KIND 只能是 host、mysql、redis、rabbitmq")
    port = int(required("EXPORTER_PORT"))
    if port < 1 or port > 65535:
        raise ValueError("EXPORTER_PORT 不在有效范围内")
    endpoint = required("NACOS_SERVER_ADDR").rstrip("/")
    if not endpoint.startswith(("http://", "https://")):
        endpoint = "http://" + endpoint
    endpoint += "/nacos/v2/ns/instance"
    params = {
        "serviceName": SERVICES[kind],
        "ip": required("EXPORTER_HOST"),
        "port": str(port),
        "namespaceId": os.environ.get("NACOS_NAMESPACE", "public") or "public",
        "groupName": os.environ.get("NACOS_DISCOVERY_GROUP", "LITESHOP_GROUP"),
        "ephemeral": "false",
        "enabled": "true",
        "healthy": "true",
        "metadata": json.dumps({"db_cluster": os.environ.get("DB_CLUSTER", "")}),
    }
    token = os.environ.get("NACOS_ACCESS_TOKEN", "").strip()
    if token:
        params["accessToken"] = token
    request = urllib.request.Request(
        endpoint,
        data=urllib.parse.urlencode(params).encode("utf-8"),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=10) as response:
        if response.status != 200:
            raise RuntimeError(f"Nacos 注册失败：HTTP {response.status}")
    print(f"已注册 {SERVICES[kind]}：{params['ip']}:{port}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, RuntimeError, OSError) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
