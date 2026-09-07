package org.hongtu.api.config;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 远程权限加载实现（微服务版 StpInterfaceImpl）
 * <p>
 * 单体架构中角色/权限数据由 user 模块的 StpInterfaceImpl 查库加载，
 * 拆分后其余服务通过 Feign 调 user-service 获取，使 @SaCheckRole/@SaCheckPermission 注解继续生效。
 * user-service 自身保留原查库实现，本类通过 @ConditionalOnMissingBean 自动让位。
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteStpInterface implements StpInterface {

    private final UserFeignClient userFeignClient;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        try {
            Result<List<String>> result = userFeignClient.getPermissionList(Long.valueOf(loginId.toString()));
            return result != null && result.getData() != null ? result.getData() : new ArrayList<>();
        } catch (Exception e) {
            log.error("远程获取用户权限失败, loginId={}", loginId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        try {
            Result<List<String>> result = userFeignClient.getRoleList(Long.valueOf(loginId.toString()));
            return result != null && result.getData() != null ? result.getData() : new ArrayList<>();
        } catch (Exception e) {
            log.error("远程获取用户角色失败, loginId={}", loginId, e);
            return new ArrayList<>();
        }
    }
}
