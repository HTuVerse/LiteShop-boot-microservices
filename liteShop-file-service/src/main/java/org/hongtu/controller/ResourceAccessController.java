package org.hongtu.controller;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.hongtu.DAO.FileUploadMapper;
import org.hongtu.DTO.FileUpload;
import org.hongtu.Result.Result;
import org.hongtu.storage.ResourceLibraryAccess;
import org.hongtu.storage.StorageManager;
import org.hongtu.tenant.TenantContext;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/** 各业务共用的公开资源入口与私有资源授权接口。 */
@RestController
@RequiredArgsConstructor
public class ResourceAccessController {
    private final FileUploadMapper files;
    private final StorageManager storage;
    private final ResourceLibraryAccess libraryAccess;

    /** 内部权益服务查询当前租户已使用容量。 */
    @GetMapping("/inner/file-upload/resources/used-bytes")
    public Result<Long> usedBytes() {
        return Result.success(files.usedBytes(TenantContext.getTenantId()));
    }

    /** 内部业务服务按资源 ID 派生 URL，仅允许读取公开资源。 */
    @GetMapping("/inner/file-upload/resources/{id}/public-url")
    public Result<String> internalPublicUrl(@PathVariable Long id) {
        FileUpload file = files.findAcrossTenants(id);
        if (file == null || !"ACTIVE".equals(file.getDeletionState())
                || !"PUBLIC".equals(storage.visibility(file))) return Result.error("公开资源不存在");
        return Result.success(storage.publicUrl(file));
    }

    /** 公开资源只跳转到源站，文件内容由对象存储或静态服务器直接提供。 */
    @GetMapping("/images/resource/{id}")
    public ResponseEntity<Void> publicResource(@PathVariable Long id) {
        FileUpload file = files.findAcrossTenants(id);
        if (file == null || !"ACTIVE".equals(file.getDeletionState())
                || !"PUBLIC".equals(storage.visibility(file))) return ResponseEntity.notFound().build();
        String origin = storage.originPublicUrl(file);
        if (origin == null || origin.isBlank()) return ResponseEntity.notFound().build();
        // 禁止缓存权限决策，公开转私有后不会继续签发旧入口跳转。
        return ResponseEntity.status(302).location(URI.create(origin))
                .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    /** 公开文件返回长期入口；私有文件验证上传人或超级管理员后签发五分钟链接。 */
    @GetMapping("/file-upload/resources/{id}/access")
    public Result<AccessAddress> access(@PathVariable Long id) throws Exception {
        FileUpload file = files.findAcrossTenants(id);
        if (file == null || !"ACTIVE".equals(file.getDeletionState())) return Result.error("资源不存在");
        if ("PUBLIC".equals(storage.visibility(file)))
            return Result.success(new AccessAddress("PUBLIC", storage.publicUrl(file), null));
        if (!canReadPrivate(file)) return Result.error("无权访问私有资源");
        String signed = storage.accessUrl(file);
        if (signed == null || signed.isBlank())
            return Result.success(new AccessAddress("PRIVATE", "/file-upload/resources/" + id + "/download", null));
        return Result.success(new AccessAddress("PRIVATE", signed, Instant.now().plusSeconds(300)));
    }

    /** 本地私有文件或不支持直链的场景，经相同权限校验后从后端下载。 */
    @GetMapping("/file-upload/resources/{id}/download")
    public ResponseEntity<?> download(@PathVariable Long id) throws Exception {
        FileUpload file = files.findAcrossTenants(id);
        if (file == null || !"ACTIVE".equals(file.getDeletionState())) return ResponseEntity.notFound().build();
        if ("PUBLIC".equals(storage.visibility(file))) {
            String origin = storage.originPublicUrl(file);
            return origin == null ? ResponseEntity.notFound().build()
                    : ResponseEntity.status(302).location(URI.create(origin)).build();
        }
        if ("PRIVATE".equals(storage.visibility(file)) && !canReadPrivate(file))
            return ResponseEntity.status(403).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getOriginalFileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(storage.open(file)));
    }

    private boolean canReadPrivate(FileUpload file) {
        return StpUtil.isLogin() && (StpUtil.hasRole("SUPER_ADMIN")
                || (Objects.equals(file.getTenantId(), TenantContext.getTenantIdOrNull())
                    && Objects.equals(file.getUploaderId(), StpUtil.getLoginIdAsLong()))
                || ((StpUtil.hasRole("ADMIN") || StpUtil.hasRole("SUPER_ADMIN"))
                    && (libraryAccess.permissionsForFolder(file.getTenantId(), file.getFolderId())
                        & (ResourceLibraryAccess.VIEW | ResourceLibraryAccess.SELECT)) != 0));
    }

    /** 私有链接的 expiresAt 仅描述本次授权，不能写入业务数据库。 */
    public record AccessAddress(String visibility, String url, Instant expiresAt) { }
}
