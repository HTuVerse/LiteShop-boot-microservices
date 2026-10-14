package org.hongtu.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.hongtu.DTO.FileUpload;
import org.hongtu.Result.Result;
import org.hongtu.config.FileUploadConfig;
import org.hongtu.enums.StorageTypeEnum;
import org.hongtu.service.FileUploadService;
import org.hongtu.utils.FileUploadUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件上传控制器
 */
@Slf4j
@RestController
@RequestMapping("/file-upload")
public class FileController {

    @Resource
    private FileUploadUtil fileUploadUtil;

    @Resource
    private FileUploadConfig fileUploadConfig;
    @Resource
    FileUploadService fileUploadService;

    /**
     * 上传单个图片
     * @param file 图片文件
     * @return 上传结果
     */
    @SaCheckLogin
    @PostMapping("/upload/image")
    public Result<FileUpload> uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
        
            // 检查文件是否为空
            if (file.isEmpty()) {
                return Result.error("上传的文件为空");
            }
            // 检查文件类型
            String extension = FilenameUtils.getExtension(file.getOriginalFilename());
            if (!fileUploadConfig.isAllowedImageType(extension)
                    || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                return Result.error("不支持的文件类型: " + extension);
            }
            // 上传文件
            String fileUrl = fileUploadUtil.uploadFile(file);
            // 构建响应对象
            FileUpload uploadDTO = new FileUpload();
            uploadDTO.setOriginalFileName(file.getOriginalFilename());
            uploadDTO.setSize(file.getSize());
            uploadDTO.setContentType(file.getContentType());
            uploadDTO.setUrl(fileUrl);
            uploadDTO.setStorageType(resolveStorageType());
            fileUploadService.save(uploadDTO);
            return Result.success("上传成功", uploadDTO);
        
    }

    /** 上传公告附件，允许常见图片、文档和压缩包，单文件最大 20MB。 */
    @SaCheckLogin
    @PostMapping("/upload/file")
    public Result<FileUpload> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        
            if (file.isEmpty()) return Result.error("上传的文件为空");
            String extension = FilenameUtils.getExtension(file.getOriginalFilename());
            if (!fileUploadConfig.isAllowedAttachmentType(extension)) {
                return Result.error("不支持的附件类型: " + extension);
            }
            String fileUrl = fileUploadUtil.uploadAttachment(file);
            FileUpload uploadDTO = buildUploadRecord(file, fileUrl);
            fileUploadService.save(uploadDTO);
            return Result.success("上传成功", uploadDTO);
        
    }

    private FileUpload buildUploadRecord(MultipartFile file, String fileUrl) {
        FileUpload uploadDTO = new FileUpload();
        uploadDTO.setOriginalFileName(file.getOriginalFilename());
        uploadDTO.setSize(file.getSize());
        uploadDTO.setContentType(file.getContentType());
        uploadDTO.setUrl(fileUrl);
        uploadDTO.setStorageType(resolveStorageType());
        return uploadDTO;
    }

    /**
     * 上传多个图片
     * @param files 图片文件数组
     * @return 上传结果
     */
    @SaCheckLogin
    @PostMapping("/upload/images")
    public Result<FileUpload[]> uploadImages(@RequestParam("files") MultipartFile[] files) throws IOException {
        
            if (files == null || files.length == 0) {
                return Result.error("上传的文件为空");
            }
            FileUpload[] results = new FileUpload[files.length];
            for (int i = 0; i < files.length; i++) {
                MultipartFile file = files[i];
                // 检查文件类型
                String extension = FilenameUtils.getExtension(file.getOriginalFilename());
                if (!fileUploadConfig.isAllowedImageType(extension)
                        || file.getContentType() == null || !file.getContentType().startsWith("image/")) {
                    return Result.error("不支持的文件类型: " + extension);
                }
                // 上传文件
                String fileUrl = fileUploadUtil.uploadFile(file);
                // 构建响应对象
                FileUpload uploadDTO = new FileUpload();
                uploadDTO.setOriginalFileName(file.getOriginalFilename());
                uploadDTO.setSize(file.getSize());
                uploadDTO.setContentType(file.getContentType());
                uploadDTO.setUrl(fileUrl);
                uploadDTO.setStorageType(resolveStorageType());
                fileUploadService.save(uploadDTO);
                results[i] = uploadDTO;
            }
            return Result.success("上传成功", results);
        
    }

    private StorageTypeEnum resolveStorageType() {
        return fileUploadConfig.isOssEnabled() ? StorageTypeEnum.OSS : StorageTypeEnum.LOCAL;
    }
}
