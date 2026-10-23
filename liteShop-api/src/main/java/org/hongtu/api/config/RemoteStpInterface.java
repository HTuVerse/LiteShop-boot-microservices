package org.hongtu.api.config;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.util.FeignResultUtil;

import java.util.Collections;
import java.util.List;

/**
 * 远程权限加载实现（微服务版 StpInterfaceImpl）
 * <p>
 * 单体架构中角色/权限数据由 user 模块的 StpInterfaceImpl 查库加载，
 * 拆分后其余服务通过 Feign 调 user-service 获取，使 @SaCheckRole/@SaCheckPermission 注解继续生效。
 * user-service 自身保留原查库实现，本类通过 @ConditionalOnMissingBean 自动让位。
 */
@RequiredArgsConstructor
public class RemoteStpInterface implements StpInterface {

    private final UserFeignClient userFeignClient;
    private final RemoteAccessGuard guard;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        List<String> permissions = guard.call(() -> FeignResultUtil.unwrap(
                userFeignClient.getPermissionList(Long.valueOf(loginId.toString()))));
        return permissions == null ? Collections.emptyList() : permissions;
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        List<String> roles = guard.call(() -> FeignResultUtil.unwrap(
                userFeignClient.getRoleList(Long.valueOf(loginId.toString()))));
        return roles == null ? Collections.emptyList() : roles;
    }
}
