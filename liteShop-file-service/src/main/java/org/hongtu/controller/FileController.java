package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.hongtu.DTO.FileUpload;
import org.hongtu.DTO.ResourceFolder;
import org.hongtu.DAO.ResourceFolderMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.hongtu.Result.Result;
import org.hongtu.service.FileUploadService;
import org.hongtu.storage.StorageManager;
import org.hongtu.storage.ResourceCapacityGuard;
import org.hongtu.tenant.TenantContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** 业务上传入口，沿用原 URL 与响应格式，具体存储由统一驱动选择。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/file-upload")
public class FileController {
    private final StorageManager storage;
    private final FileUploadService files;
    private final ResourceCapacityGuard capacity;
    private final ResourceFolderMapper folders;

    /** 上传公开图片，供菜品、头像和网站展示使用。 */
    @SaCheckLogin
    @PostMapping("/upload/image")
    @Transactional(rollbackFor = Exception.class)
    public Result<FileUpload> uploadImage(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.success("上传成功", save(file, true));
    }

    /** 上传业务附件，保持原有公开链接行为。 */
    @SaCheckLogin
    @PostMapping("/upload/file")
    @Transactional(rollbackFor = Exception.class)
    public Result<FileUpload> uploadFile(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.success("上传成功", save(file, false));
    }

    /** 批量上传公开图片。 */
    @SaCheckLogin
    @PostMapping("/upload/images")
    @Transactional(rollbackFor = Exception.class)
    public Result<FileUpload[]> uploadImages(@RequestParam("files") MultipartFile[] uploads) throws Exception {
        if (uploads == null || uploads.length == 0 || uploads.length > 20)
            return Result.error("每次可上传 1 至 20 张图片");
        List<FileUpload> result = new ArrayList<>();
        try {
            for (MultipartFile file : uploads) result.add(save(file, true));
        } catch (Exception error) {
            for (FileUpload saved : result) {
                try { storage.delete(saved); } catch (Exception cleanup) { error.addSuppressed(cleanup); }
            }
            throw error;
        }
        return Result.success("上传成功", result.toArray(FileUpload[]::new));
    }

    private FileUpload save(MultipartFile file, boolean imageOnly) throws Exception {
        Long tenantId = TenantContext.getTenantIdOrNull();
        Long uploaderId = StpUtil.getLoginIdAsLong();
        Long folderId = tenantId == null ? null : userFolder(tenantId, uploaderId);
        StorageManager.UploadResult uploaded = storage.upload(file, imageOnly, "PUBLIC", capacity::requireAvailable);
        FileUpload record = new FileUpload();
        record.setOriginalFileName(file.getOriginalFilename());
        record.setSize(uploaded.size());
        record.setTenantId(tenantId == null ? 0L : tenantId);
        record.setUploaderId(uploaderId);
        record.setFolderId(folderId);
        storage.apply(record, uploaded);
        try { files.save(record); }
        catch (RuntimeException error) {
            storage.delete(record);
            throw error;
        }
        return storage.forResponse(record);
    }

    /** 业务端上传统一落在当前租户的用户资源目录。 */
    private Long userFolder(Long tenantId, Long uploaderId) {
        ResourceFolder root = findOrCreate(tenantId, null, "用户资源");
        return findOrCreate(tenantId, root.getId(), "用户_" + uploaderId).getId();
    }

    private ResourceFolder findOrCreate(Long tenantId, Long parentId, String name) {
        LambdaQueryWrapper<ResourceFolder> query = new LambdaQueryWrapper<ResourceFolder>()
                .eq(ResourceFolder::getTenantId, tenantId)
                .eq(parentId != null, ResourceFolder::getParentId, parentId)
                .isNull(parentId == null, ResourceFolder::getParentId)
                .eq(ResourceFolder::getName, name);
        ResourceFolder existing = folders.selectOne(query);
        if (existing != null) return existing;
        ResourceFolder folder = new ResourceFolder();
        folder.setTenantId(tenantId);
        folder.setParentId(parentId);
        folder.setName(name);
        folders.insert(folder);
        return folder;
    }
}
