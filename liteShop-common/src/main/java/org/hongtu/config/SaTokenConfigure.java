package org.hongtu.config;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.jwt.StpLogicJwtForSimple;
import cn.dev33.satoken.router.SaHttpMethod;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import org.hongtu.tenant.TenantAccessValidator;
import org.hongtu.tenant.TenantInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    /**
     * 文件上传目录（微服务架构下仅 file-service 配置该项，其余服务为空则跳过静态资源映射）
     */
    @Value("${file.upload-path:}")
    private String uploadPath;

    @Value("${liteshop.internal-call.secret:}")
    private String internalCallSecret;

    @Autowired
    private ObjectProvider<TenantAccessValidator> tenantAccessValidators;

    // 定义不需要拦截的路径
    private final List<String> excludePaths = Arrays.asList(
            "/user/Login",
            "/user/admin/Login",
            "/user/admin/captcha",
            "/config/website/public",
            "/user/admin/select-tenant",
            "/user/admin/phoneOrEmailVerifyCodeLogin",
            "/user/phoneOrEmailVerifyCodeLogin",
            "/user/resetPassword",
            "/msg/sendVerifyCode",
            "/msg/sendSmsCode",
            "/msg/sendEmailCode",
            "/user/wxLogin",
            "/images/**",  // 图片资源路径
            "/favicon.ico",
            "/inner/**"    // 服务间内部接口（外部访问已在网关层拦截）
    );

    // 配置静态资源映射
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射 /images/** 路径到本地上传目录（仅配置了 file.upload-path 的服务生效）
        if (uploadPath != null && !uploadPath.isEmpty()) {
            registry.addResourceHandler("/images/**")
                    .addResourceLocations("file:" + uploadPath);
        }
    }

    // 注册 Sa-Token 的拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 租户上下文解析：必须在鉴权拦截器之前注册，保证鉴权查询角色时租户上下文已就位
        registry.addInterceptor(new TenantInterceptor(internalCallSecret, tenantId -> {
            TenantAccessValidator validator = tenantAccessValidators.getIfAvailable();
            return validator != null && validator.isActive(tenantId);
        })).addPathPatterns("/**");

        // 注册路由拦截器，自定义认证规则
        registry.addInterceptor(new SaInterceptor(handler -> {
            // 登录校验 -- 拦截所有路由，并排除登录接口和静态资源
            SaRouter.match("/**")
                    .notMatch(excludePaths)  // 排除不需要拦截的路径
                    .check(r -> StpUtil.checkLogin());

            // Business endpoints declare operation permissions with @SaCheckPermission.
/*
            // 权限校验 -- 不同模块校验不同权限
            SaRouter.match("/user/**", r -> StpUtil.checkPermission("user"));
            SaRouter.match("/admin/**", r -> StpUtil.checkPermission("admin"));
         */
        })).addPathPatterns("/**");
    }

    // Sa-Token 整合 jwt (Simple 简单模式)
    @Bean
    public StpLogic getStpLogicJwt() {
        return new StpLogicJwtForSimple();
    }

    /**
     * 注册 [Sa-Token 全局过滤器]
     */
    @Bean
    public SaServletFilter getSaServletFilter() {
        return new SaServletFilter()
                // 指定 [拦截路由] 与 [放行路由]
                .addInclude("/**")
                .addExclude("/favicon.ico", "/images/**")  // 排除图片资源路径

                // 认证函数: 每次请求执行
                .setAuth(obj -> {
                    SaManager.getLog().debug("请求path={}", SaHolder.getRequest().getRequestPath());
                    // ...
                })

                // 异常处理函数：每次认证函数发生异常时执行此函数
                .setError(e -> {
                    return SaResult.error(e.getMessage());
                })

                // 前置函数：在每次认证函数之前执行
                .setBeforeAuth(obj -> {
                    SaHolder.getResponse()
                            // ---------- 设置跨域响应头 ----------
                            // 允许指定域访问跨域资源
                            .setHeader("Access-Control-Allow-Origin", "*")
                            // 允许所有请求方式
                            .setHeader("Access-Control-Allow-Methods", "*")
                            // 允许的header参数
                            .setHeader("Access-Control-Allow-Headers", "*")
                            // 有效时间
                            .setHeader("Access-Control-Max-Age", "3600")
                            // ---------- 禁止 CDN/浏览器 缓存接口响应，避免套 CDN 后提交成功但数据不更新 ----------
                            .setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
                            .setHeader("Pragma", "no-cache")
                    ;
                    // 如果是预检请求，则立即返回到前端
                    SaRouter.match(SaHttpMethod.OPTIONS)
                            .free(r -> System.out.println("--------OPTIONS预检请求，不做处理"))
                            .back();
                });
    }
}

