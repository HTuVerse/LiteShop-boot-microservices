package org.hongtu.restaurant.config;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.config.RemoteAccessGuard;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.tenant.TenantConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 餐饮服务角色适配：门店管理员可复用已有 ADMIN 管理接口，再由门店 SQL 范围限制数据。 */
@Configuration
@RequiredArgsConstructor
public class RestaurantStpInterfaceConfig {

    private final UserFeignClient userFeignClient;
    private final RemoteAccessGuard remoteAccessGuard;

    @Bean
    public StpInterface restaurantStpInterface() {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                List<String> permissions = remoteAccessGuard.call(() -> FeignResultUtil.unwrap(
                        userFeignClient.getPermissionList(Long.valueOf(loginId.toString()))));
                return permissions == null ? List.of() : permissions;
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                List<String> remoteRoles = remoteAccessGuard.call(() -> FeignResultUtil.unwrap(
                        userFeignClient.getRoleList(Long.valueOf(loginId.toString()))));
                LinkedHashSet<String> roles = new LinkedHashSet<>();
                if (remoteRoles != null) roles.addAll(remoteRoles);
                if (roles.contains(TenantConstants.ROLE_STORE_ADMIN)) {
                    roles.add(TenantConstants.ROLE_ADMIN);
                }
                return new ArrayList<>(roles);
            }
        };
    }
}
