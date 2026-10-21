package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.hongtu.DAO.ResourceFolderMapper;
import org.hongtu.DAO.FileUploadMapper;
import org.hongtu.DTO.FileUpload;
import org.hongtu.DTO.ResourceFolder;
import org.hongtu.Result.Result;
import org.hongtu.service.FileUploadService;
import org.hongtu.storage.StorageManager;
import org.hongtu.storage.ResourceReferenceGuard;
import org.hongtu.storage.ResourceReferenceService;
import org.hongtu.storage.ResourceCapacityGuard;
import org.hongtu.storage.ResourceLibraryAccess;
import org.springframework.transaction.annotation.Transactional;
import org.hongtu.tenant.TenantContext;
import org.hongtu.tenant.TenantIgnore;
import org.hongtu.tenant.TenantScope;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

/** 平台和租户共用的资源库；租户范围由服务端会话确定。 */
@RestController
@RequiredArgsConstructor
@SaCheckLogin
@RequestMapping("/file-upload/admin/resources")
public class ResourceLibraryController {
    private final FileUploadService files;
    private final ResourceFolderMapper folders;
    private final FileUploadMapper fileMapper;
    private final ResourceLibraryAccess access;
    private final StorageManager storage;
    private final ResourceReferenceGuard references;
    private final ResourceReferenceService referenceService;
    private final ResourceCapacityGuard capacity;

    /** 分页查询资源，可按名称、类型、商户、上传者与文件夹筛选。 */
    @GetMapping
    public Result<Page<FileUpload>> page(@RequestParam(defaultValue = "1") long currentPage,
                                         @RequestParam(defaultValue = "20") long pageSize,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String visibility,
                                         @RequestParam(required = false) Long tenantId,
                                         @RequestParam(required = false) Long uploaderId,
                                         @RequestParam(required = false) Long folderId,
                                         @RequestParam(required = false) String purpose) {
        requireManager();
        if (currentPage < 1 || pageSize < 1 || pageSize > 100) return Result.error("分页参数不合法");
        if (folderId != null && folderId < 0) return Result.error("文件夹参数不合法");
        Long owner = tenantId == null ? libraryTenantId() : tenantId;
        int required = "SELECT".equals(purpose) ? ResourceLibraryAccess.SELECT : ResourceLibraryAccess.VIEW;
        int unclassifiedPermissions = access.permissionsForFolder(owner, null);
        if (folderId != null && folderId > 0 && !owner.equals(folderOwner(folderId)))
            return Result.error("文件夹不属于所选资源库");
        if (folderId != null && folderId > 0
                && (access.permissionsForFolder(owner, folderId) & required) != required)
            return Result.success(new Page<>(currentPage, pageSize));
        if (folderId != null && folderId == 0 && (unclassifiedPermissions & required) != required)
            return Result.success(new Page<>(currentPage, pageSize));
        // 未指定文件夹时只聚合有权访问的文件夹；根目录授权仅补充未分类文件。
        List<Long> allowed = folderId == null
                ? access.allowedFolders(owner, TenantContext.getTenantIdOrNull(), required) : List.of();
        if (folderId == null && (unclassifiedPermissions & required) != required && allowed.isEmpty())
            return Result.success(new Page<>(currentPage, pageSize));
        LambdaQueryWrapper<FileUpload> query = new LambdaQueryWrapper<FileUpload>()
                .like(keyword != null && !keyword.isBlank(), FileUpload::getOriginalFileName, keyword == null ? "" : keyword.trim())
                .eq(FileUpload::getTenantId, owner)
                .eq(uploaderId != null, FileUpload::getUploaderId, uploaderId)
                .eq(visibility != null && !visibility.isBlank(), FileUpload::getVisibility, visibility)
                .eq(folderId != null && folderId > 0, FileUpload::getFolderId, folderId)
                .isNull(folderId != null && folderId == 0, FileUpload::getFolderId)
                .likeRight("image".equals(type), FileUpload::getContentType, "image/")
                .and("file".equals(type), part -> part.isNull(FileUpload::getContentType)
                        .or().notLikeRight(FileUpload::getContentType, "image/"))
                .orderByDesc(FileUpload::getCreatedAt, FileUpload::getId);
        if (folderId == null && (unclassifiedPermissions & required) == required && !allowed.isEmpty())
            query.and(part -> part.isNull(FileUpload::getFolderId).or().in(FileUpload::getFolderId, allowed));
        else if (folderId == null && (unclassifiedPermissions & required) == required)
            query.isNull(FileUpload::getFolderId);
        else if (folderId == null)
            query.in(FileUpload::getFolderId, allowed);
        Page<FileUpload> page = new Page<>(currentPage, pageSize);
        inOwner(owner, () -> files.page(page, query));
        String deliveryBase = storage.currentSettings().getPublicDeliveryBaseUrl();
        page.getRecords().forEach(record -> storage.forResponse(record, deliveryBase));
        return Result.success(page);
    }

    /** 列出平台共享的分类文件夹。 */
    @GetMapping("/folders")
    public Result<List<ResourceFolder>> folders(@RequestParam(required = false) Long tenantId) {
        requireManager();
        Long owner = tenantId == null ? libraryTenantId() : tenantId;
        if (access.permissionsFor(owner) == 0 && !access.hasFolderAccess(owner,
                TenantContext.getTenantIdOrNull(), ResourceLibraryAccess.VIEW)
                && !access.hasFolderAccess(owner, TenantContext.getTenantIdOrNull(), ResourceLibraryAccess.UPLOAD))
            throw new SecurityException("无权访问该租户资源库");
        List<ResourceFolder> all = inOwner(owner, () -> folders.selectList(new LambdaQueryWrapper<ResourceFolder>()
                .eq(ResourceFolder::getTenantId, owner).orderByAsc(ResourceFolder::getName)));
        if (access.platform() || owner.equals(TenantContext.getTenantIdOrNull())) {
            all.forEach(folder -> { folder.setMyPermissions(15); folder.setNavigationOnly(false); });
            return Result.success(all);
        }
        Map<Long, ResourceFolder> byId = all.stream().collect(Collectors.toMap(ResourceFolder::getId, Function.identity()));
        Set<Long> visible = new HashSet<>();
        for (ResourceFolder folder : all) {
            int bits = access.permissionsForFolder(owner, folder.getId());
            folder.setMyPermissions(bits);
            folder.setNavigationOnly(bits == 0);
            if (bits == 0) continue;
            Long current = folder.getId();
            while (current != null && visible.add(current)) {
                ResourceFolder ancestor = byId.get(current);
                current = ancestor == null ? null : ancestor.getParentId();
            }
        }
        return Result.success(all.stream().filter(folder -> visible.contains(folder.getId())).toList());
    }

    @GetMapping("/roots")
    public Result<List<ResourceLibraryAccess.Root>> roots() {
        requireManager();
        return Result.success(access.roots());
    }

    @PutMapping("/roots/{tenantId}/access")
    public Result<ResourceLibraryAccess.Root> configureRoot(@PathVariable Long tenantId,
            @RequestBody RootAccessBody body) {
        requireManager();
        return Result.success(access.configure(tenantId, body.mode(), body.permissions(), body.grants()));
    }

    @GetMapping("/folders/{id}/access")
    public Result<ResourceLibraryAccess.FolderAccess> folderAccess(@PathVariable Long id) {
        requireManager();
        Long owner = folderOwner(id);
        if (!access.platform()) throw new SecurityException("仅平台管理员可配置文件夹权限");
        return Result.success(access.folderAccess(owner, id));
    }

    @PutMapping("/folders/{id}/access")
    public Result<ResourceLibraryAccess.FolderAccess> configureFolder(@PathVariable Long id,
            @RequestBody RootAccessBody body) {
        requireManager();
        return Result.success(access.configureFolder(folderOwner(id), id, body.mode(), body.permissions(), body.grants()));
    }

    /** 新建分类文件夹。 */
    @PostMapping("/folders")
    public Result<ResourceFolder> createFolder(@Valid @RequestBody NameBody body) {
        requireManager();
        Long owner = body.parentId() == null
                ? (body.tenantId() == null ? libraryTenantId() : body.tenantId())
                : folderOwner(body.parentId());
        access.requireFolder(owner, body.parentId(), ResourceLibraryAccess.EDIT);
        if (body.parentId() == null) access.requireExisting(owner);
        return inOwner(owner, () -> createFolderInScope(body, owner));
    }

    private Result<ResourceFolder> createFolderInScope(NameBody body, Long owner) {
        String name = cleanName(body.name());
        if (body.parentId() != null && !owner.equals(folderOwner(body.parentId())))
            return Result.error("父文件夹不存在");
        if (folders.selectCount(new LambdaQueryWrapper<ResourceFolder>().eq(ResourceFolder::getTenantId, owner)
                .eq(body.parentId() != null, ResourceFolder::getParentId, body.parentId())
                .isNull(body.parentId() == null, ResourceFolder::getParentId)
                .eq(ResourceFolder::getName, name)) > 0)
            return Result.error("文件夹名称已存在");
        ResourceFolder folder = new ResourceFolder();
        folder.setName(name);
        folder.setTenantId(owner);
        folder.setParentId(body.parentId());
        folders.insert(folder);
        return Result.success(folder);
    }

    /** 修改文件夹名称。 */
    @PutMapping("/folders/{id}")
    public Result<ResourceFolder> renameFolder(@PathVariable Long id, @Valid @RequestBody NameBody body) {
        requireManager();
        Long owner = folderOwner(id);
        access.requireFolder(owner, id, ResourceLibraryAccess.EDIT);
        return inOwner(owner, () -> renameFolderInScope(id, body, owner));
    }

    private Result<ResourceFolder> renameFolderInScope(Long id, NameBody body, Long owner) {
        ResourceFolder folder = folders.selectById(id);
        if (folder == null || !owner.equals(folder.getTenantId())) return Result.error("文件夹不存在");
        String name = cleanName(body.name());
        if (folders.selectCount(new LambdaQueryWrapper<ResourceFolder>().eq(ResourceFolder::getTenantId, owner)
                .eq(folder.getParentId() != null, ResourceFolder::getParentId, folder.getParentId())
                .isNull(folder.getParentId() == null, ResourceFolder::getParentId)
                .eq(ResourceFolder::getName, name).ne(ResourceFolder::getId, id)) > 0)
            return Result.error("文件夹名称已存在");
        folder.setName(name);
        folders.updateById(folder);
        return Result.success(folder);
    }

    /** 删除空文件夹，避免资源失去分类。 */
    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable Long id) {
        requireManager();
        Long owner = folderOwner(id);
        access.requireFolder(owner, id, ResourceLibraryAccess.EDIT);
        return inOwner(owner, () -> deleteFolderInScope(id, owner));
    }

    private Result<Void> deleteFolderInScope(Long id, Long owner) {
        if (!folderExists(id, owner)) return Result.error("文件夹不存在");
        if (folders.selectCount(new LambdaQueryWrapper<ResourceFolder>().eq(ResourceFolder::getParentId, id)) > 0)
            return Result.error("请先删除子文件夹");
        if (files.count(new LambdaQueryWrapper<FileUpload>().eq(FileUpload::getFolderId, id)) > 0)
            return Result.error("请先移动或删除文件夹中的资源");
        folders.deleteById(id);
        return Result.success();
    }

    /** 上传单个或多个资源，全部使用现有文件服务的类型和大小限制。 */
    @PostMapping("/upload")
    @Transactional(rollbackFor = Exception.class)
    public Result<List<FileUpload>> upload(@RequestParam("files") MultipartFile[] uploads,
                                           @RequestParam(required = false) Long folderId,
                                           @RequestParam(required = false) Long tenantId,
                                           @RequestParam(defaultValue = "PUBLIC") String visibility) throws Exception {
        requireManager();
        Long owner = folderId != null ? folderOwner(folderId)
                : (tenantId == null ? libraryTenantId() : tenantId);
        access.requireFolder(owner, folderId, ResourceLibraryAccess.UPLOAD);
        if (folderId == null) access.requireExisting(owner);
        if (folderId != null && tenantId != null && !owner.equals(tenantId)) return Result.error("文件夹不属于所选资源库");
        if (uploads == null || uploads.length == 0 || uploads.length > 20) return Result.error("每次可上传 1 至 20 个文件");
        if (folderId != null && !owner.equals(folderOwner(folderId))) return Result.error("文件夹不存在");
        if (!"PUBLIC".equalsIgnoreCase(visibility) && !"PRIVATE".equalsIgnoreCase(visibility))
            return Result.error("访问权限不合法");
        for (MultipartFile file : uploads) {
            cleanName(file.getOriginalFilename());
        }
        List<FileUpload> result = new java.util.ArrayList<>();
        try {
            for (MultipartFile file : uploads) {
                StorageManager.UploadResult uploaded = storage.upload(file, false, visibility,
                        size -> {
                            if (owner > 0) inOwner(owner, () -> { capacity.requireAvailable(size); return null; });
                        });
                FileUpload record = new FileUpload();
                record.setOriginalFileName(cleanName(file.getOriginalFilename()));
                record.setSize(uploaded.size());
                storage.apply(record, uploaded);
                record.setTenantId(owner);
                record.setUploaderId(StpUtil.getLoginIdAsLong());
                record.setFolderId(folderId);
                try { inOwner(owner, () -> files.save(record)); }
                catch (RuntimeException error) { storage.delete(record); throw error; }
                result.add(storage.forResponse(record));
            }
        } catch (Exception error) {
            for (FileUpload saved : result) {
                try { storage.delete(saved); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
            }
            throw error;
        }
        return Result.success(result);
    }

    /** 仅修改资源显示名称，保持被业务引用的 URL 不变。 */
    @PutMapping("/{id}/name")
    public Result<FileUpload> rename(@PathVariable Long id, @Valid @RequestBody NameBody body) {
        requireManager();
        Long owner = fileOwner(id);
        access.requireFolder(owner, fileMapper.findAcrossTenants(id).getFolderId(), ResourceLibraryAccess.EDIT);
        return inOwner(owner, () -> renameInScope(id, body));
    }

    private Result<FileUpload> renameInScope(Long id, NameBody body) {
        FileUpload record = files.getById(id);
        if (record == null) return Result.error("资源不存在");
        record.setOriginalFileName(cleanName(body.name()));
        files.updateById(record);
        return Result.success(storage.forResponse(record));
    }

    /** 将资源批量移动到指定分类，null 表示未分类。 */
    @PutMapping("/move")
    public Result<Void> move(@Valid @RequestBody BatchBody body) {
        requireManager();
        Long owner = body.folderId() != null ? folderOwner(body.folderId())
                : body.tenantId() != null ? body.tenantId() : fileOwner(body.ids().get(0));
        access.requireFolder(owner, body.folderId(), ResourceLibraryAccess.EDIT);
        if (body.ids().stream().anyMatch(id -> !owner.equals(fileOwner(id))))
            return Result.error("不能跨租户移动资源");
        for (Long id : body.ids())
            access.requireFolder(owner, fileMapper.findAcrossTenants(id).getFolderId(), ResourceLibraryAccess.EDIT);
        return inOwner(owner, () -> moveInScope(body, owner));
    }

    private Result<Void> moveInScope(BatchBody body, Long owner) {
        if (body.folderId() != null && !folderExists(body.folderId(), owner)) return Result.error("文件夹不存在");
        for (Long id : body.ids()) {
            if (files.getById(id) == null) return Result.error("资源不存在：" + id);
        }
        for (Long id : body.ids()) {
            files.lambdaUpdate().eq(FileUpload::getId, id).set(FileUpload::getFolderId, body.folderId()).update();
        }
        return Result.success();
    }

    /** 批量删除记录与对应的本地或对象存储文件。 */
    @PostMapping("/delete")
    public Result<Void> delete(@Valid @RequestBody BatchBody body) throws Exception {
        requireManager();
        Long owner = fileOwner(body.ids().get(0));
        for (Long id : body.ids())
            access.requireFolder(owner, fileMapper.findAcrossTenants(id).getFolderId(), ResourceLibraryAccess.EDIT);
        if (body.ids().stream().anyMatch(id -> !owner.equals(fileOwner(id))))
            return Result.error("不能跨租户批量删除资源");
        return inOwnerChecked(owner, () -> deleteInScope(body));
    }

    private Result<Void> deleteInScope(BatchBody body) throws Exception {
        for (Long id : body.ids()) {
            FileUpload record = files.getById(id);
            if (record == null) continue;
            requireUnused(record);
            referenceService.beginDelete(id);
            try {
                storage.delete(record);
                referenceService.finishDelete(id);
            } catch (Exception error) {
                referenceService.markDeleteFailed(id);
                throw error;
            }
        }
        return Result.success();
    }

    /** 分页查看该资源被哪些业务、字段和图片位置引用。 */
    @GetMapping("/{id}/references")
    public Result<java.util.Map<String, Object>> usages(@PathVariable Long id,
            @RequestParam(defaultValue = "1") int currentPage,
            @RequestParam(defaultValue = "20") int pageSize) {
        requireManager();
        Long owner = fileOwner(id);
        if (!access.platform() && !owner.equals(TenantContext.getTenantIdOrNull()))
            throw new SecurityException("仅资源归属租户可查看引用记录");
        return inOwner(owner, () -> usagesInScope(id, currentPage, pageSize));
    }

    private Result<java.util.Map<String, Object>> usagesInScope(Long id, int currentPage, int pageSize) {
        if (files.getById(id) == null) return Result.error("资源不存在");
        if (currentPage < 1 || pageSize < 1 || pageSize > 100) return Result.error("分页参数不合法");
        return Result.success(java.util.Map.of("total", referenceService.count(id),
                "records", referenceService.usages(id, (currentPage - 1) * pageSize, pageSize),
                "current", currentPage, "size", pageSize));
    }

    /** 以附件形式下载资源，文件名取当前显示名称。 */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws Exception {
        requireManager();
        Long owner = fileOwner(id);
        requireReadableFile(fileMapper.findAcrossTenants(id));
        return inOwnerChecked(owner, () -> downloadInScope(id));
    }

    private ResponseEntity<InputStreamResource> downloadInScope(Long id) throws Exception {
        FileUpload record = files.getById(id);
        if (record == null || !"ACTIVE".equals(record.getDeletionState())) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(record.getOriginalFileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(storage.open(record)));
    }

    /** 读取资源详情，私有文件不会返回永久公开地址。 */
    @GetMapping("/{id}")
    public Result<FileUpload> detail(@PathVariable Long id) {
        requireManager();
        Long owner = fileOwner(id);
        requireReadableFile(fileMapper.findAcrossTenants(id));
        return inOwner(owner, () -> detailInScope(id));
    }

    private Result<FileUpload> detailInScope(Long id) {
        FileUpload record = files.getById(id);
        return record == null ? Result.error("资源不存在") : Result.success(storage.forResponse(record));
    }

    /** 私有资源经角色鉴权后获取五分钟有效的直接访问地址。 */
    @GetMapping("/{id}/access")
    public Result<String> access(@PathVariable Long id) throws Exception {
        requireManager();
        Long owner = fileOwner(id);
        requireReadableFile(fileMapper.findAcrossTenants(id));
        return inOwnerChecked(owner, () -> accessInScope(id));
    }

    private Result<String> accessInScope(Long id) throws Exception {
        FileUpload record = files.getById(id);
        return record == null || !"ACTIVE".equals(record.getDeletionState())
                ? Result.error("资源不存在") : Result.success(storage.accessUrl(record));
    }

    /** 变更权限会将文件移到隔离 Bucket 或目录，再更新数据库访问策略。 */
    @PutMapping("/{id}/visibility")
    public Result<FileUpload> visibility(@PathVariable Long id, @RequestBody VisibilityBody body) throws Exception {
        requireManager();
        Long owner = fileOwner(id);
        access.requireFolder(owner, fileMapper.findAcrossTenants(id).getFolderId(), ResourceLibraryAccess.EDIT);
        return inOwnerChecked(owner, () -> visibilityInScope(id, body));
    }

    private Result<FileUpload> visibilityInScope(Long id, VisibilityBody body) throws Exception {
        FileUpload record = files.getById(id);
        if (record == null) return Result.error("资源不存在");
        var settings = storage.settings(record);
        String oldBucket = storage.bucket(record, settings);
        String oldVisibility = storage.visibility(record);
        String oldUrl = record.getUrl();
        if (oldVisibility.equals(body.visibility())) return Result.success(storage.forResponse(record));
        if ("PUBLIC".equals(oldVisibility)) requireUnused(record);
        referenceService.beginVisibilityChange(id);
        try {
            storage.changeVisibility(record, body.visibility());
            String key = storage.key(record);
            try {
                // 先撤销旧位置的访问能力，尤其不能让公开对象在切为私有后残留。
                storage.driver(record).delete(settings, oldBucket, key);
            } catch (Exception error) {
                try { storage.driver(record).delete(settings, record.getBucket(), key); }
                catch (Exception cleanup) { error.addSuppressed(cleanup); }
                throw error;
            }
            try {
                // MyBatis 默认跳过 null，必须显式清空私有文件的旧公开 URL。
                boolean updated = files.lambdaUpdate().eq(FileUpload::getId, id)
                        .set(FileUpload::getBucket, record.getBucket())
                        .set(FileUpload::getStorageKey, key)
                        .set(FileUpload::getVisibility, record.getVisibility())
                        .set(FileUpload::getUrl, record.getUrl()).update();
                if (!updated) throw new IllegalStateException("资源记录已被删除");
            } catch (RuntimeException error) {
                try {
                    storage.driver(record).copy(settings, record.getBucket(), oldBucket, key);
                    storage.driver(record).delete(settings, record.getBucket(), key);
                } catch (Exception cleanup) { error.addSuppressed(cleanup); }
                record.setBucket(oldBucket);
                record.setVisibility(oldVisibility);
                record.setUrl(oldUrl);
                throw error;
            }
            referenceService.finishVisibilityChange(id);
            return Result.success(storage.forResponse(record));
        } catch (Exception error) {
            referenceService.markVisibilityFailed(id);
            throw error;
        }
    }

    /** 检查当前默认驱动的公开、私有存储位置是否可连接。 */
    @PostMapping("/connection")
    public Result<String> connection() throws Exception {
        if (!TenantContext.isSuperAdmin()) throw new SecurityException("仅平台管理员可管理存储配置");
        var settings = storage.currentSettings();
        var type = storage.activeType(settings);
        var driver = storage.driver(type);
        driver.checkConnection(settings, storage.bucket(settings, type, "PUBLIC"));
        if (type != org.hongtu.enums.StorageTypeEnum.QINIU
                || (settings.getQiniuPrivateBucket() != null && !settings.getQiniuPrivateBucket().isBlank()))
            driver.checkConnection(settings, storage.bucket(settings, type, "PRIVATE"));
        return Result.success("连接成功");
    }

    private String cleanName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 255 || name.contains("/") || name.contains("\\"))
            throw new IllegalArgumentException("名称须为 1 至 255 字符，且不能包含路径分隔符");
        return name;
    }

    /** 同时核对历史业务 URL 和当前派生 URL，防止切换入口后误删仍被引用的文件。 */
    private void requireUnused(FileUpload record) {
        referenceService.requireMigrationReady(record);
        if (referenceService.count(record.getId()) > 0)
            throw new IllegalStateException("该资源仍被业务引用，请先移除关联");
        references.requireUnused(record.getUrl());
        String derived = storage.publicUrl(record);
        if (derived != null && !derived.equals(record.getUrl())) references.requireUnused(derived);
        String origin = storage.originPublicUrl(record);
        if (origin != null && !origin.equals(record.getUrl()) && !origin.equals(derived)) references.requireUnused(origin);
    }

    /** 创建或重命名时提交的名称。 */
    private Long libraryTenantId() {
        if (TenantContext.hasTenant()) return TenantContext.getTenantId();
        if (!TenantContext.isSuperAdmin()) throw new SecurityException("缺少租户上下文");
        return 0L;
    }

    private void requireManager() {
        if (!StpUtil.hasRole("SUPER_ADMIN") && !StpUtil.hasRole("ADMIN"))
            throw new SecurityException("无权管理资源库");
        if (!TenantContext.hasTenant() && !TenantContext.isSuperAdmin())
            throw new SecurityException("缺少租户上下文");
    }

    private boolean folderExists(Long id, Long owner) {
        ResourceFolder folder = folders.selectById(id);
        return folder != null && owner.equals(folder.getTenantId());
    }

    private Long folderOwner(Long id) {
        ResourceFolder folder = folders.findAcrossTenants(id);
        if (folder == null) throw new IllegalArgumentException("文件夹不存在");
        return folder.getTenantId();
    }

    private Long fileOwner(Long id) {
        FileUpload file = fileMapper.findAcrossTenants(id);
        if (file == null) throw new IllegalArgumentException("资源不存在");
        return file.getTenantId();
    }

    private void requireReadableFile(FileUpload file) {
        if ((access.permissionsForFolder(file.getTenantId(), file.getFolderId())
                & (ResourceLibraryAccess.VIEW | ResourceLibraryAccess.SELECT)) == 0)
            throw new SecurityException("无权查看该租户资源");
    }

    private <T> T inOwner(Long owner, java.util.function.Supplier<T> action) {
        if (owner > 0) return TenantScope.call(owner, action);
        return TenantContext.hasTenant() ? TenantIgnore.get(action) : action.get();
    }

    private <T> T inOwnerChecked(Long owner, CheckedAction<T> action) throws Exception {
        try {
            return inOwner(owner, () -> {
                try { return action.run(); }
                catch (Exception error) { throw new CheckedFailure(error); }
            });
        } catch (CheckedFailure failure) {
            throw (Exception) failure.getCause();
        }
    }

    @FunctionalInterface private interface CheckedAction<T> { T run() throws Exception; }
    private static class CheckedFailure extends RuntimeException {
        CheckedFailure(Exception cause) { super(cause); }
    }

    public record NameBody(@NotBlank @Size(max = 255) String name, Long parentId, Long tenantId) { }

    /** 批量资源操作参数。 */
    public record BatchBody(@NotEmpty @Size(max = 100) List<Long> ids, Long folderId, Long tenantId) { }
    public record RootAccessBody(String mode, int permissions, List<ResourceLibraryAccess.Grant> grants) { }

    /** 公开与私有切换参数。 */
    public record VisibilityBody(@NotBlank String visibility) { }
}
