package org.hongtu.audit;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaCheckRole;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.audit.AuditQueryService.AuditQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 系统操作审计日志只读查询与受控导出接口。 */
@RestController
@RequestMapping("/system-audit")
@RequiredArgsConstructor
public class AuditController {
    private final AuditQueryService queries;
    private final AuditIntegrityService integrity;

    /** 平台管理员按租户范围校验日志内容和链头是否完整。 */
    @GetMapping("/integrity")
    @SaCheckRole("SUPER_ADMIN")
    public Result<Map<String, Object>> integrity(@RequestParam(defaultValue = "0") long tenantId) {
        if (tenantId < 0) return Result.error("租户范围不合法");
        return Result.success(integrity.verify(tenantId));
    }

    /** 分页查询本账号获准访问的审计记录。 */
    @GetMapping("/page")
    @SaCheckPermission(value = "system:audit", orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> page(@ModelAttribute AuditQuery query) {
        return Result.success(queries.page(query));
    }

    /** 查看单条日志和已脱敏的数据差异。 */
    @GetMapping("/{id}")
    @SaCheckPermission(value = "system:audit:detail", orRole = "SUPER_ADMIN")
    public Result<Map<String, Object>> detail(@PathVariable long id) {
        Map<String, Object> detail = queries.detail(id);
        return detail == null ? Result.error(Result.CODE_NOT_FOUND, "审计日志不存在")
                : Result.success(detail);
    }

    /** 按当前筛选条件导出最多一万条审计摘要，同时记录导出行为。 */
    @GetMapping("/export")
    @SaCheckPermission(value = "system:audit:export", orRole = "SUPER_ADMIN")
    @AuditOperation(module = "system:audit", type = "EXPORT", objectType = "AUDIT_LOG",
            summary = "导出系统操作审计日志")
    public void export(@ModelAttribute AuditQuery query, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rows = queries.export(query);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=system-audit.csv");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        response.getOutputStream().write(("日志ID,租户ID,操作时间,操作账号,IP地址,IP归属地,模块,类型,对象类型,对象ID,操作摘要,执行结果\r\n")
                .getBytes(StandardCharsets.UTF_8));
        for (Map<String, Object> row : rows) {
            String line = String.join(",", csv(row.get("id")), csv(row.get("tenantId")),
                    csv(row.get("occurredAt")), csv(row.get("actorAccount")),
                    csv(row.get("clientIp")), csv(row.get("ipLocation")),
                    csv(row.get("moduleCode")), csv(row.get("operationType")),
                    csv(row.get("objectType")), csv(row.get("objectId")),
                    csv(row.get("summary")), csv(row.get("result"))) + "\r\n";
            response.getOutputStream().write(line.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        // 防止电子表格软件将导出内容解释成公式。
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
