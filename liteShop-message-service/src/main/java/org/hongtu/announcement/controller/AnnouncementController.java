package org.hongtu.announcement.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.hongtu.Result.Result;
import org.hongtu.announcement.dto.AnnouncementForm;
import org.hongtu.announcement.dto.AnnouncementView;
import org.hongtu.announcement.dto.PageResult;
import org.hongtu.announcement.realtime.AnnouncementSseRegistry;
import org.hongtu.announcement.service.AnnouncementService;
import org.hongtu.tenant.StoreContext;
import org.hongtu.tenant.TenantContext;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/msg/announcements")
@RequiredArgsConstructor
public class AnnouncementController {
    private final AnnouncementService service;
    private final AnnouncementSseRegistry sseRegistry;

    @GetMapping("/admin/page")
    @SaCheckPermission(value = {"message:announcement"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<PageResult<AnnouncementView>> adminPage(
            @RequestParam(defaultValue = "1") long currentPage,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(service.adminPage(currentPage, pageSize, status, keyword));
    }

    @GetMapping("/admin/{id}")
    @SaCheckPermission(value = {"message:announcement"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<AnnouncementView> detail(@PathVariable Long id) {
        return Result.success(service.detail(id));
    }

    @PostMapping
    @SaCheckPermission(value = {"message:announcement:add"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<AnnouncementView> create(@RequestBody AnnouncementForm form) {
        return Result.success("草稿已保存", service.createDraft(form));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(value = {"message:announcement:edit"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<AnnouncementView> update(@PathVariable Long id, @RequestBody AnnouncementForm form) {
        return Result.success("草稿已更新", service.updateDraft(id, form));
    }

    @PostMapping("/{id}/publish")
    @SaCheckPermission(value = {"message:announcement:publish"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Void> publish(@PathVariable Long id) {
        service.publish(id);
        return Result.success("公告已发布");
    }

    @PostMapping("/{id}/withdraw")
    @SaCheckPermission(value = {"message:announcement:withdraw"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Void> withdraw(@PathVariable Long id) {
        service.withdraw(id);
        return Result.success("公告已撤回");
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(value = {"message:announcement:delete"}, mode = SaMode.OR, orRole = "SUPER_ADMIN")
    public Result<Void> delete(@PathVariable Long id) {
        service.deleteDraft(id);
        return Result.success("草稿已删除");
    }

    @GetMapping("/current")
    @SaCheckLogin
    public Result<List<AnnouncementView>> current(@RequestParam(defaultValue = "5") int size) {
        return Result.success(service.current(size));
    }

    @GetMapping("/unread-count")
    @SaCheckLogin
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("count", service.unreadCount()));
    }

    @PutMapping("/{id}/read")
    @SaCheckLogin
    public Result<Void> read(@PathVariable Long id) {
        service.markRead(id);
        return Result.success();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckLogin
    public ResponseEntity<SseEmitter> stream() {
        SseEmitter emitter = sseRegistry.subscribe(StpUtil.getLoginIdAsLong(),
                TenantContext.getTenantIdOrNull(), StoreContext.isRestricted(), StoreContext.getStoreIds());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }
}
