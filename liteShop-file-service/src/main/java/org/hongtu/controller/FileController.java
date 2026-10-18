package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.hongtu.DTO.FileUpload;
import org.hongtu.Result.Result;
import org.hongtu.service.FileUploadService;
import org.hongtu.storage.StorageManager;
import org.hongtu.tenant.TenantContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/** 业务上传入口，沿用原 URL 与响应格式，具体存储由统一驱动选择。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/file-upload")
public class FileController {
    private final StorageManager storage;
    private final FileUploadService files;

    /** 上传公开图片，供菜品、头像和网站展示使用。 */
    @SaCheckLogin
    @PostMapping("/upload/image")
    public Result<FileUpload> uploadImage(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.success("上传成功", save(file, true));
    }

    /** 上传业务附件，保持原有公开链接行为。 */
    @SaCheckLogin
    @PostMapping("/upload/file")
    public Result<FileUpload> uploadFile(@RequestParam("file") MultipartFile file) throws Exception {
        return Result.success("上传成功", save(file, false));
    }

    /** 批量上传公开图片。 */
    @SaCheckLogin
    @PostMapping("/upload/images")
    public Result<FileUpload[]> uploadImages(@RequestParam("files") MultipartFile[] uploads) throws Exception {
        if (uploads == null || uploads.length == 0 || uploads.length > 20)
            return Result.error("每次可上传 1 至 20 张图片");
        List<FileUpload> result = new ArrayList<>();
        for (MultipartFile file : uploads) result.add(save(file, true));
        return Result.success("上传成功", result.toArray(FileUpload[]::new));
    }

    private FileUpload save(MultipartFile file, boolean imageOnly) throws Exception {
        StorageManager.UploadResult uploaded = storage.upload(file, imageOnly, "PUBLIC");
        FileUpload record = new FileUpload();
        record.setOriginalFileName(file.getOriginalFilename());
        record.setSize(file.getSize());
        record.setTenantId(TenantContext.getTenantIdOrNull() == null ? 0L : TenantContext.getTenantId());
        record.setUploaderId(StpUtil.getLoginIdAsLong());
        storage.apply(record, uploaded);
        try { files.save(record); }
        catch (RuntimeException error) {
            storage.delete(record);
            throw error;
        }
        return storage.forResponse(record);
    }
}
