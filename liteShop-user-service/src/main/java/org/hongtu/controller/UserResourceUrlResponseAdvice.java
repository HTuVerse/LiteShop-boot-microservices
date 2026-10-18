package org.hongtu.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.hongtu.DTO.SysUser;
import org.hongtu.Result.Result;
import org.hongtu.VO.CashierMemberVO;
import org.hongtu.VO.SysUserVO;
import org.hongtu.api.util.ResourceBindingCoordinator;
import org.hongtu.api.dto.ResourceBinding;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

/** 用户响应继续输出 avatar，关联资源则按 ID 派生当前公开地址。 */
@ControllerAdvice(basePackages = "org.hongtu.controller")
@RequiredArgsConstructor
public class UserResourceUrlResponseAdvice implements ResponseBodyAdvice<Object> {
    private final ResourceBindingCoordinator links;

    @Override
    public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter method, MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> converter,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof Result<?> result) hydrate(result.getData());
        return body;
    }

    private void hydrate(Object value) {
        if (value == null) return;
        if (value instanceof IPage<?> page) { page.getRecords().forEach(this::hydrate); return; }
        if (value instanceof Iterable<?> items) { items.forEach(this::hydrate); return; }
        if (value instanceof Map<?, ?> map) { map.values().forEach(this::hydrate); return; }
        if (value instanceof SysUser user) {
            ResourceBinding avatar = links.references(user.getTenantId() == null ? 0L : user.getTenantId(),
                    "USER", user.getId()).stream().findFirst().orElse(null);
            if (avatar != null) { user.setAvatar(avatar.url()); user.setAvatarResourceId(avatar.resourceId()); }
        } else if (value instanceof SysUserVO user) {
            ResourceBinding avatar = links.references(user.getTenantId() == null ? 0L : user.getTenantId(),
                    "USER", user.getId()).stream().findFirst().orElse(null);
            if (avatar != null) { user.setAvatar(avatar.url()); user.setAvatarResourceId(avatar.resourceId()); }
        } else if (value instanceof CashierMemberVO member) {
            member.setAvatar(links.currentSingle("USER", member.getId(), "avatar", member.getAvatar()));
            member.setAvatarResourceId(links.currentSingleId("USER", member.getId(), "avatar"));
        }
    }
}
