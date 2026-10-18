package org.hongtu.storage;

import lombok.RequiredArgsConstructor;
import org.hongtu.api.client.ConfigResourceReferenceClient;
import org.hongtu.api.client.RestaurantResourceReferenceClient;
import org.hongtu.api.client.UserResourceReferenceClient;
import org.hongtu.api.util.FeignResultUtil;
import org.springframework.stereotype.Component;

/** 跨业务域检查资源引用；服务不可用时中止删除，避免误删线上素材。 */
@Component
@RequiredArgsConstructor
public class ResourceReferenceGuard {
    private final ConfigResourceReferenceClient config;
    private final RestaurantResourceReferenceClient restaurant;
    private final UserResourceReferenceClient users;

    public void requireUnused(String url) {
        if (url == null || url.isBlank()) return;
        if (Boolean.TRUE.equals(FeignResultUtil.unwrap(config.used(url)))
                || Boolean.TRUE.equals(FeignResultUtil.unwrap(restaurant.used(url)))
                || Boolean.TRUE.equals(FeignResultUtil.unwrap(users.used(url))))
            throw new IllegalStateException("该资源仍被网站、菜品、门店或用户引用，请先移除业务引用");
    }
}
