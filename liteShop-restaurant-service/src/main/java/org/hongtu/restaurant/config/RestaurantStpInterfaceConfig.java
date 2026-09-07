package org.hongtu.restaurant.config;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hongtu.Result.Result;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.tenant.TenantConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** 餐饮服务角色适配：门店管理员可复用已有 ADMIN 管理接口，再由门店 SQL 范围限制数据。 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RestaurantStpInterfaceConfig {

    private final UserFeignClient userFeignClient;

    @Bean
    public StpInterface restaurantStpInterface() {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                try {
                    Result<List<String>> result = userFeignClient.getPermissionList(Long.valueOf(loginId.toString()));
                    return result != null && result.getData() != null ? result.getData() : new ArrayList<>();
                } catch (Exception e) {
                    log.error("餐饮服务远程获取用户权限失败, loginId={}", loginId, e);
                    return new ArrayList<>();
                }
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                try {
                    Result<List<String>> result = userFeignClient.getRoleList(Long.valueOf(loginId.toString()));
                    LinkedHashSet<String> roles = new LinkedHashSet<>();
                    if (result != null && result.getData() != null) {
                        roles.addAll(result.getData());
                    }
                    if (roles.contains(TenantConstants.ROLE_STORE_ADMIN)) {
                        roles.add(TenantConstants.ROLE_ADMIN);
                    }
                    return new ArrayList<>(roles);
                } catch (Exception e) {
                    log.error("餐饮服务远程获取用户角色失败, loginId={}", loginId, e);
                    return new ArrayList<>();
                }
            }
        };
    }
}
