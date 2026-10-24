package org.hongtu.monitor;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.utils.IpUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 系统监控的活动上报与按权限隔离的查询接口。 */
@RestController
@RequestMapping("/system-monitor")
@RequiredArgsConstructor
public class SystemMonitorController {
    private final SystemMonitorService monitor;
    private final IpUtil ipUtil;

    /** 页面进入时统计一次访问，定时心跳只更新在线时间。 */
    @PostMapping("/{client}/pulse")
    public Result<Boolean> pulse(@PathVariable String client,
                                 @RequestParam(defaultValue = "false") boolean visit,
                                 HttpServletRequest request) {
        monitor.pulse(client, visit, ipUtil.getClientIp(request));
        return Result.success(true);
    }

    /** 分页查询当前权限范围的在线会话。 */
    @GetMapping("/{client}/online")
    public Result<Map<String, Object>> online(@PathVariable String client,
                                               @RequestParam(required = false) Long tenantId,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String ip,
                                               @RequestParam(defaultValue = "1") int currentPage,
                                               @RequestParam(defaultValue = "20") int pageSize) {
        if (currentPage < 1 || pageSize < 1 || pageSize > 100)
            throw new IllegalArgumentException("分页参数不合法");
        return Result.success(monitor.online(client, tenantId, keyword, ip, currentPage, pageSize));
    }

    /** 返回当前范围的统计卡片、近七日趋势和活跃小时。 */
    @GetMapping("/{client}/overview")
    public Result<Map<String, Object>> overview(@PathVariable String client,
                                                 @RequestParam(required = false) Long tenantId) {
        return Result.success(monitor.overview(client, tenantId));
    }
}
