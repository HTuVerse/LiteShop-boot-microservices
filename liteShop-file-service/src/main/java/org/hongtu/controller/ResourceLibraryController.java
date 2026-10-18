package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.hongtu.DAO.ResourceFolderMapper;
import org.hongtu.DTO.FileUpload;
import org.hongtu.DTO.ResourceFolder;
import org.hongtu.Result.Result;
import org.hongtu.service.FileUploadService;
import org.hongtu.storage.StorageManager;
import org.hongtu.storage.ResourceReferenceGuard;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;

import java.util.List;
import java.nio.charset.StandardCharsets;

/** 超级管理员统一管理全部商户的上传资源。 */
@RestController
@RequiredArgsConstructor
@SaCheckRole("SUPER_ADMIN")
@RequestMapping("/file-upload/admin/resources")
public class ResourceLibraryController {
    private final FileUploadService files;
    private final ResourceFolderMapper folders;
    private final StorageManager storage;
    private final ResourceReferenceGuard references;

    /** 分页查询资源，可按名称、类型、商户、上传者与文件夹筛选。 */
    @GetMapping
    public Result<Page<FileUpload>> page(@RequestParam(defaultValue = "1") long currentPage,
                                         @RequestParam(defaultValue = "20") long pageSize,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String visibility,
                                         @RequestParam(required = false) Long tenantId,
                                         @RequestParam(required = false) Long uploaderId,
                                         @RequestParam(required = false) Long folderId) {
        if (currentPage < 1 || pageSize < 1 || pageSize > 100) return Result.error("分页参数不合法");
        LambdaQueryWrapper<FileUpload> query = new LambdaQueryWrapper<FileUpload>()
                .like(keyword != null && !keyword.isBlank(), FileUpload::getOriginalFileName, keyword == null ? "" : keyword.trim())
                .eq(tenantId != null, FileUpload::getTenantId, tenantId)
                .eq(uploaderId != null, FileUpload::getUploaderId, uploaderId)
                .eq(visibility != null && !visibility.isBlank(), FileUpload::getVisibility, visibility)
                .eq(folderId != null && folderId > 0, FileUpload::getFolderId, folderId)
                .isNull(folderId != null && folderId == 0, FileUpload::getFolderId)
                .likeRight("image".equals(type), FileUpload::getContentType, "image/")
                .and("file".equals(type), part -> part.isNull(FileUpload::getContentType)
                        .or().notLikeRight(FileUpload::getContentType, "image/"))
                .orderByDesc(FileUpload::getCreatedAt, FileUpload::getId);
        return Result.success(files.page(new Page<>(currentPage, pageSize), query));
    }

    /** 列出平台共享的分类文件夹。 */
    @GetMapping("/folders")
    public Result<List<ResourceFolder>> folders() {
        return Result.success(folders.selectList(new LambdaQueryWrapper<ResourceFolder>().orderByAsc(ResourceFolder::getName)));
    }

    /** 新建分类文件夹。 */
    @PostMapping("/folders")
    public Result<ResourceFolder> createFolder(@Valid @RequestBody NameBody body) {
        String name = cleanName(body.name());
        if (folders.selectCount(new LambdaQueryWrapper<ResourceFolder>().eq(ResourceFolder::getName, name)) > 0)
            return Result.error("文件夹名称已存在");
        ResourceFolder folder = new ResourceFolder();
        folder.setName(name);
        folder.setTenantId(0L);
        folders.insert(folder);
        return Result.success(folder);
    }

    /** 修改文件夹名称。 */
    @PutMapping("/folders/{id}")
    public Result<ResourceFolder> renameFolder(@PathVariable Long id, @Valid @RequestBody NameBody body) {
        ResourceFolder folder = folders.selectById(id);
        if (folder == null) return Result.error("文件夹不存在");
        String name = cleanName(body.name());
        if (folders.selectCount(new LambdaQueryWrapper<ResourceFolder>().eq(ResourceFolder::getName, name).ne(ResourceFolder::getId, id)) > 0)
            return Result.error("文件夹名称已存在");
        folder.setName(name);
        folders.updateById(folder);
        return Result.success(folder);
    }

    /** 删除空文件夹，避免资源失去分类。 */
    @DeleteMapping("/folders/{id}")
    public Result<Void> deleteFolder(@PathVariable Long id) {
        if (folders.selectById(id) == null) return Result.error("文件夹不存在");
        if (files.count(new LambdaQueryWrapper<FileUpload>().eq(FileUpload::getFolderId, id)) > 0)
            return Result.error("请先移动或删除文件夹中的资源");
        folders.deleteById(id);
        return Result.success();
    }

    /** 上传单个或多个资源，全部使用现有文件服务的类型和大小限制。 */
    @PostMapping("/upload")
    public Result<List<FileUpload>> upload(@RequestParam("files") MultipartFile[] uploads,
                                           @RequestParam(required = false) Long folderId,
                                           @RequestParam(defaultValue = "PUBLIC") String visibility) throws Exception {
        if (uploads == null || uploads.length == 0 || uploads.length > 20) return Result.error("每次可上传 1 至 20 个文件");
        if (folderId != null && folders.selectById(folderId) == null) return Result.error("文件夹不存在");
        if (!"PUBLIC".equalsIgnoreCase(visibility) && !"PRIVATE".equalsIgnoreCase(visibility))
            return Result.error("访问权限不合法");
        for (MultipartFile file : uploads) {
            cleanName(file.getOriginalFilename());
        }
        List<FileUpload> result = new java.util.ArrayList<>();
        for (MultipartFile file : uploads) {
            StorageManager.UploadResult uploaded = storage.upload(file, false, visibility);
            FileUpload record = new FileUpload();
            record.setOriginalFileName(cleanName(file.getOriginalFilename()));
            record.setSize(file.getSize());
            storage.apply(record, uploaded);
            record.setTenantId(0L);
            record.setUploaderId(StpUtil.getLoginIdAsLong());
            record.setFolderId(folderId);
            try { files.save(record); }
            catch (RuntimeException error) { storage.delete(record); throw error; }
            result.add(record);
        }
        return Result.success(result);
    }

    /** 仅修改资源显示名称，保持被业务引用的 URL 不变。 */
    @PutMapping("/{id}/name")
    public Result<FileUpload> rename(@PathVariable Long id, @Valid @RequestBody NameBody body) {
        FileUpload record = files.getById(id);
        if (record == null) return Result.error("资源不存在");
        record.setOriginalFileName(cleanName(body.name()));
        files.updateById(record);
        return Result.success(record);
    }

    /** 将资源批量移动到指定分类，null 表示未分类。 */
    @PutMapping("/move")
    public Result<Void> move(@Valid @RequestBody BatchBody body) {
        if (body.folderId() != null && folders.selectById(body.folderId()) == null) return Result.error("文件夹不存在");
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
        for (Long id : body.ids()) {
            FileUpload record = files.getById(id);
            if (record == null) continue;
            references.requireUnused(record.getUrl());
            storage.delete(record);
            files.removeById(id);
        }
        return Result.success();
    }

    /** 以附件形式下载资源，文件名取当前显示名称。 */
    @GetMapping("/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws Exception {
        FileUpload record = files.getById(id);
        if (record == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(record.getOriginalFileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(storage.open(record)));
    }

    /** 读取资源详情，私有文件不会返回永久公开地址。 */
    @GetMapping("/{id}")
    public Result<FileUpload> detail(@PathVariable Long id) {
        FileUpload record = files.getById(id);
        return record == null ? Result.error("资源不存在") : Result.success(record);
    }

    /** 私有资源经角色鉴权后获取五分钟有效的直接访问地址。 */
    @GetMapping("/{id}/access")
    public Result<String> access(@PathVariable Long id) throws Exception {
        FileUpload record = files.getById(id);
        return record == null ? Result.error("资源不存在") : Result.success(storage.accessUrl(record));
    }

    /** 变更权限会将文件移到隔离 Bucket 或目录，再更新数据库访问策略。 */
    @PutMapping("/{id}/visibility")
    public Result<FileUpload> visibility(@PathVariable Long id, @RequestBody VisibilityBody body) throws Exception {
        FileUpload record = files.getById(id);
        if (record == null) return Result.error("资源不存在");
        var settings = storage.settings(record);
        String oldBucket = storage.bucket(record, settings);
        String oldVisibility = storage.visibility(record);
        String oldUrl = record.getUrl();
        if (oldVisibility.equals(body.visibility())) return Result.success(record);
        if ("PUBLIC".equals(oldVisibility)) references.requireUnused(record.getUrl());
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
        return Result.success(record);
    }

    /** 检查当前默认驱动的公开、私有存储位置是否可连接。 */
    @PostMapping("/connection")
    public Result<String> connection() throws Exception {
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

    /** 创建或重命名时提交的名称。 */
    public record NameBody(@NotBlank @Size(max = 255) String name) { }

    /** 批量资源操作参数。 */
    public record BatchBody(@NotEmpty @Size(max = 100) List<Long> ids, Long folderId) { }

    /** 公开与私有切换参数。 */
    public record VisibilityBody(@NotBlank String visibility) { }
}
