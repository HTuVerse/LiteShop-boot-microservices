"""检查 Nacos 注册脚本只在业务接口确认成功时报告完成。"""

import io
import json
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

import register_exporter


class FakeResponse(io.BytesIO):
    status = 200

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()


class RegisterExporterTest(unittest.TestCase):
    def setUp(self):
        self.environment = patch.dict(
            "os.environ",
            {
                "EXPORTER_KIND": "mysql",
                "EXPORTER_HOST": "10.0.0.12",
                "EXPORTER_PORT": "9104",
                "NACOS_SERVER_ADDR": "http://127.0.0.1:8848",
            },
            clear=True,
        )
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def run_with_response(self, body):
        with patch("register_exporter.urllib.request.urlopen", return_value=FakeResponse(body)):
            with redirect_stdout(io.StringIO()) as output:
                register_exporter.main()
                return output.getvalue()

    def test_success_response(self):
        output = self.run_with_response(json.dumps({"code": 0, "data": True}).encode())
        self.assertIn("已注册 liteshop-mysql-exporter", output)

    def test_business_failure_is_not_reported_as_success(self):
        with self.assertRaisesRegex(RuntimeError, "权限不足"):
            self.run_with_response(json.dumps({"code": 403, "message": "权限不足", "data": False}).encode())

    def test_invalid_response_is_not_reported_as_success(self):
        with self.assertRaisesRegex(RuntimeError, "有效 JSON"):
            self.run_with_response(b"not json")


if __name__ == "__main__":
    unittest.main()
