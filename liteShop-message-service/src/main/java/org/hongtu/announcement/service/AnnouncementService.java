package org.hongtu.announcement.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.hongtu.api.dto.UserContactDTO;
import org.hongtu.api.client.UserFeignClient;
import org.hongtu.api.util.FeignResultUtil;
import org.hongtu.announcement.dto.AnnouncementForm;
import org.hongtu.announcement.dto.AnnouncementAttachment;
import org.hongtu.announcement.dto.AnnouncementRecord;
import org.hongtu.announcement.dto.AnnouncementView;
import org.hongtu.announcement.dto.PageResult;
import org.hongtu.announcement.mapper.AnnouncementMapper;
import org.hongtu.announcement.realtime.AnnouncementRealtimeEvent;
import org.hongtu.announcement.realtime.AnnouncementRealtimePublisher;
import org.hongtu.exception.BusinessException;
import org.hongtu.tenant.StoreContext;
import org.hongtu.tenant.TenantContext;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {
    private static final List<String> LEVELS = List.of("NORMAL", "IMPORTANT", "URGENT");

    private final AnnouncementMapper mapper;
    private final AnnouncementRealtimePublisher realtimePublisher;
    private final UserFeignClient userFeignClient;

    public PageResult<AnnouncementView> adminPage(long current, long size, String status, String keyword) {
        long safeCurrent = Math.max(1, current);
        long safeSize = Math.min(100, Math.max(1, size));
        Long tenantId = managedTenantId();
        String normalizedStatus = normalizeOptional(status);
        if (normalizedStatus != null && !List.of("DRAFT", "PUBLISHED", "WITHDRAWN").contains(normalizedStatus)) {
            throw new BusinessException(400, "公告状态无效");
        }
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        List<AnnouncementView> records = mapper.findAdminPage(tenantId, normalizedStatus, normalizedKeyword,
                        (safeCurrent - 1) * safeSize, safeSize)
                .stream().map(this::toView).toList();
        return new PageResult<>(records, mapper.countAdmin(tenantId, normalizedStatus, normalizedKeyword),
                safeCurrent, safeSize);
    }

    public AnnouncementView detail(Long id) {
        return toView(requireManaged(id));
    }

    @Transactional(rollbackFor = Exception.class)
    public AnnouncementView createDraft(AnnouncementForm form) {
        ValidatedForm value = validate(form);
        LocalDateTime now = LocalDateTime.now();
        PublisherIdentity publisher = currentPublisher();
        AnnouncementRecord record = new AnnouncementRecord();
        record.setId(IdWorker.getId());
        record.setTenantId(value.tenantId());
        record.setTitle(value.title());
        record.setContent(value.content());
        record.setLevel(value.level());
        record.setStatus("DRAFT");
        record.setPublisherId(publisher.id());
        record.setPublisherName(publisher.name());
        record.setVersion(1);
        record.setExpireTime(value.expireTime());
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        mapper.insertAnnouncement(record);
        replaceTargets(record.getId(), value.storeIds());
        replaceAttachments(record.getId(), value.attachments());
        return toView(record);
    }

    @Transactional(rollbackFor = Exception.class)
    public AnnouncementView updateDraft(Long id, AnnouncementForm form) {
        AnnouncementRecord existing = requireManaged(id);
        if (!"DRAFT".equals(existing.getStatus())) {
            throw new BusinessException(409, "只有草稿公告可以编辑");
        }
        ValidatedForm value = validate(form);
        if (!Objects.equals(existing.getTenantId(), value.tenantId())) {
            throw new BusinessException(403, "不能修改公告所属范围");
        }
        existing.setTitle(value.title());
        existing.setContent(value.content());
        existing.setLevel(value.level());
        existing.setExpireTime(value.expireTime());
        existing.setUpdatedAt(LocalDateTime.now());
        if (mapper.updateDraft(existing) != 1) throw new BusinessException(409, "公告状态已变化，请刷新后重试");
        replaceTargets(id, value.storeIds());
        replaceAttachments(id, value.attachments());
        existing.setVersion(existing.getVersion() + 1);
        return toView(existing);
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        AnnouncementRecord existing = requireManaged(id);
        if (!List.of("DRAFT", "WITHDRAWN").contains(existing.getStatus())) {
            throw new BusinessException(409, "当前状态不能发布");
        }
        if (existing.getExpireTime() != null && !existing.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "过期时间必须晚于发布时间");
        }
        LocalDateTime now = LocalDateTime.now();
        PublisherIdentity publisher = currentPublisher();
        if (mapper.publish(id, publisher.id(), publisher.name(), now) != 1) {
            throw new BusinessException(409, "公告状态已变化，请刷新后重试");
        }
        publishAfterCommit(new AnnouncementRealtimeEvent("PUBLISHED", id, existing.getTenantId(),
                mapper.findStoreTargets(id), existing.getVersion() + 1));
    }

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        AnnouncementRecord existing = requireManaged(id);
        if (!"PUBLISHED".equals(existing.getStatus())) throw new BusinessException(409, "只有已发布公告可以撤回");
        if (mapper.withdraw(id, LocalDateTime.now()) != 1) {
            throw new BusinessException(409, "公告状态已变化，请刷新后重试");
        }
        publishAfterCommit(new AnnouncementRealtimeEvent("WITHDRAWN", id, existing.getTenantId(),
                mapper.findStoreTargets(id), existing.getVersion() + 1));
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteDraft(Long id) {
        requireManaged(id);
        mapper.deleteTargets(id);
        mapper.deleteAttachments(id);
        if (mapper.deleteDraft(id) != 1) throw new BusinessException(409, "只有草稿公告可以删除");
    }

    public List<AnnouncementView> current(int size) {
        UserScope scope = userScope();
        return mapper.findCurrent(scope.tenantId(), StpUtil.getLoginIdAsLong(), scope.unrestricted(),
                        scope.storeIds(), Math.min(20, Math.max(1, size)))
                .stream().map(this::toView).toList();
    }

    public long unreadCount() {
        UserScope scope = userScope();
        return mapper.countUnread(scope.tenantId(), StpUtil.getLoginIdAsLong(), scope.unrestricted(), scope.storeIds());
    }

    public void markRead(Long id) {
        AnnouncementRecord record = mapper.findById(id);
        if (record == null || !isVisible(record)) throw new BusinessException(404, "公告不存在或不可见");
        mapper.markRead(id, StpUtil.getLoginIdAsLong(), TenantContext.getTenantIdOrNull());
    }

    private boolean isVisible(AnnouncementRecord record) {
        LocalDateTime now = LocalDateTime.now();
        if (!"PUBLISHED".equals(record.getStatus()) || record.getPublishTime() == null
                || record.getPublishTime().isAfter(now)
                || (record.getExpireTime() != null && !record.getExpireTime().isAfter(now))) return false;
        Long tenantId = TenantContext.getTenantIdOrNull();
        if (record.getTenantId() != null && !record.getTenantId().equals(tenantId)) return false;
        List<Long> targets = mapper.findStoreTargets(record.getId());
        return targets.isEmpty() || !StoreContext.isRestricted()
                || StoreContext.getStoreIds().stream().anyMatch(targets::contains);
    }

    private AnnouncementRecord requireManaged(Long id) {
        if (id == null) throw new BusinessException(400, "公告ID不能为空");
        AnnouncementRecord record = mapper.findById(id);
        if (record == null || !Objects.equals(record.getTenantId(), managedTenantId())) {
            throw new BusinessException(404, "公告不存在或无权访问");
        }
        return record;
    }

    private Long managedTenantId() {
        Long tenantId = TenantContext.getTenantIdOrNull();
        if (tenantId == null && !TenantContext.isSuperAdmin()) {
            throw new BusinessException(403, "缺少可管理的商户范围");
        }
        return tenantId;
    }

    private UserScope userScope() {
        return new UserScope(TenantContext.getTenantIdOrNull(), !StoreContext.isRestricted(), StoreContext.getStoreIds());
    }

    private ValidatedForm validate(AnnouncementForm form) {
        if (form == null) throw new BusinessException(400, "公告内容不能为空");
        String title = form.getTitle() == null ? "" : form.getTitle().trim();
        String content = form.getContent() == null ? "" : form.getContent().trim();
        if (title.isEmpty() || title.length() > 200) throw new BusinessException(400, "公告标题长度应为 1-200 个字符");
        if (content.isEmpty() || content.length() > 100000) {
            throw new BusinessException(400, "公告内容长度应为 1-100000 个字符");
        }
        String level = normalizeOptional(form.getLevel());
        level = level == null ? "NORMAL" : level;
        if (!LEVELS.contains(level)) throw new BusinessException(400, "公告级别无效");
        Long tenantId = managedTenantId();
        String scopeType = normalizeOptional(form.getScopeType());
        scopeType = scopeType == null ? (tenantId == null ? "PLATFORM" : "TENANT") : scopeType;
        List<Long> storeIds = form.getStoreIds() == null ? List.of()
                : form.getStoreIds().stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (tenantId == null) {
            if (!"PLATFORM".equals(scopeType)) throw new BusinessException(400, "平台视角只能发布全平台公告");
            storeIds = List.of();
        } else if ("STORE".equals(scopeType)) {
            if (storeIds.isEmpty()) throw new BusinessException(400, "门店公告至少选择一家门店");
            if (StoreContext.isRestricted() && !StoreContext.getStoreIds().containsAll(storeIds)) {
                throw new BusinessException(403, "不能向未授权门店发布公告");
            }
        } else if ("TENANT".equals(scopeType)) {
            storeIds = List.of();
        } else {
            throw new BusinessException(400, "商户视角只能发布商户或门店公告");
        }
        if (form.getExpireTime() != null && !form.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new BusinessException(400, "过期时间必须晚于当前时间");
        }
        List<AnnouncementAttachment> attachments = validateAttachments(form.getAttachments());
        return new ValidatedForm(tenantId, title, content, level, storeIds, attachments, form.getExpireTime());
    }

    private void replaceTargets(Long announcementId, List<Long> storeIds) {
        mapper.deleteTargets(announcementId);
        storeIds.forEach(storeId -> mapper.insertStoreTarget(IdWorker.getId(), announcementId, storeId));
    }

    private List<AnnouncementAttachment> validateAttachments(List<AnnouncementAttachment> attachments) {
        if (attachments == null) return List.of();
        if (attachments.size() > 5) throw new BusinessException(400, "每条公告最多上传 5 个附件");
        return attachments.stream().map(item -> {
            if (item == null || item.getFileName() == null || item.getFileName().isBlank()
                    || item.getFileUrl() == null || item.getFileUrl().isBlank()) {
                throw new BusinessException(400, "附件名称和地址不能为空");
            }
            String fileName = item.getFileName().trim();
            String fileUrl = item.getFileUrl().trim();
            if (fileName.length() > 255 || fileUrl.length() > 1000) {
                throw new BusinessException(400, "附件名称或地址过长");
            }
            if ((!fileUrl.startsWith("/") || fileUrl.startsWith("//"))
                    && !fileUrl.matches("(?i)^https?://[^\\s]+$")) {
                throw new BusinessException(400, "附件地址无效");
            }
            long fileSize = item.getFileSize() == null ? 0 : item.getFileSize();
            if (fileSize < 0 || fileSize > 20L * 1024 * 1024) {
                throw new BusinessException(400, "单个附件不能超过 20MB");
            }
            AnnouncementAttachment value = new AnnouncementAttachment();
            value.setFileName(fileName);
            value.setFileUrl(fileUrl);
            value.setFileSize(fileSize);
            value.setContentType(item.getContentType() == null ? null
                    : item.getContentType().trim().substring(0, Math.min(120, item.getContentType().trim().length())));
            return value;
        }).toList();
    }

    private void replaceAttachments(Long announcementId, List<AnnouncementAttachment> attachments) {
        mapper.deleteAttachments(announcementId);
        for (int index = 0; index < attachments.size(); index++) {
            AnnouncementAttachment item = attachments.get(index);
            mapper.insertAttachment(IdWorker.getId(), announcementId, item.getFileName(), item.getFileUrl(),
                    item.getFileSize(), item.getContentType(), index);
        }
    }

    private AnnouncementView toView(AnnouncementRecord record) {
        AnnouncementView view = new AnnouncementView();
        BeanUtils.copyProperties(record, view);
        List<Long> storeIds = mapper.findStoreTargets(record.getId());
        view.setStoreIds(storeIds);
        view.setAttachments(mapper.findAttachments(record.getId()));
        view.setScopeType(record.getTenantId() == null ? "PLATFORM" : storeIds.isEmpty() ? "TENANT" : "STORE");
        if (view.getRead() == null) view.setRead(false);
        return view;
    }

    private void publishAfterCommit(AnnouncementRealtimeEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            realtimePublisher.publish(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                realtimePublisher.publish(event);
            }
        });
    }

    private PublisherIdentity currentPublisher() {
        long userId = StpUtil.getLoginIdAsLong();
        String fallback = "用户 " + userId;
        try {
            UserContactDTO user = FeignResultUtil.unwrap(userFeignClient.getUserById(userId));
            if (user == null) return new PublisherIdentity(userId, fallback);
            String nickname = normalizeDisplayName(user.getNickname());
            if (nickname != null) return new PublisherIdentity(userId, nickname);
            String username = normalizeDisplayName(user.getUsername());
            return new PublisherIdentity(userId, username == null ? fallback : username);
        } catch (Exception exception) {
            log.warn("查询公告发布人失败，使用用户ID兜底：userId={}, reason={}", userId, exception.getMessage());
            return new PublisherIdentity(userId, fallback);
        }
    }

    private String normalizeDisplayName(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        return normalized.substring(0, Math.min(100, normalized.length()));
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private record ValidatedForm(Long tenantId, String title, String content, String level,
                                 List<Long> storeIds, List<AnnouncementAttachment> attachments,
                                 LocalDateTime expireTime) {
    }

    private record UserScope(Long tenantId, boolean unrestricted, List<Long> storeIds) {
    }

    private record PublisherIdentity(Long id, String name) {
    }
}
