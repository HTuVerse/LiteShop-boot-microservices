package org.hongtu.monitor;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 管理端或小程序退出时立即清除对应在线会话。 */
@Component
@RequiredArgsConstructor
public class SystemMonitorLogoutFilter extends OncePerRequestFilter {
    private final SystemMonitorService monitor;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if ("/user/logout".equals(request.getRequestURI()) && StpUtil.getTokenValue() != null) {
            try {
                monitor.removeCurrent();
            } catch (RuntimeException ignored) {
                // 监控缓存异常不能阻止用户退出登录。
            }
        }
        chain.doFilter(request, response);
    }
}
