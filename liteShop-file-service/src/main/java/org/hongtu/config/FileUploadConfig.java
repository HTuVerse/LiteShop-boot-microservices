package org.hongtu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 文件上传配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "file")
public class FileUploadConfig {
    private static final Set<String> IMAGE_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp");
    private static final Set<String> ATTACHMENT_TYPES = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx",
            "xls", "xlsx", "ppt", "pptx", "txt", "csv", "zip", "rar"
    );
    /**
     * 是否启用七牛云OSS
     */
    private boolean ossEnabled;
    
    /**
     * 上传文件保存的本地路径
     */
    private String uploadPath;
    
    /**
     * 上传文件访问的基础URL
     */
    private String baseUrl;
    
    /**
     * 允许上传的文件类型，逗号分隔
     */
    private String allowedTypes;
    
    /**
     * 最大文件大小(MB)
     */
    private int maxSize;
    
    /**
     * 获取允许的文件类型列表
     */
    public List<String> getAllowedTypesList() {
        return Arrays.asList(allowedTypes.split(","));
    }
    
    /**
     * 检查文件类型是否允许上传
     */
    public boolean isAllowedFileType(String fileExtension) {
        return getAllowedTypesList().contains(fileExtension.toLowerCase());
    }

    public boolean isAllowedImageType(String fileExtension) {
        return fileExtension != null && IMAGE_TYPES.contains(fileExtension.toLowerCase());
    }

    public boolean isAllowedAttachmentType(String fileExtension) {
        return fileExtension != null && ATTACHMENT_TYPES.contains(fileExtension.toLowerCase());
    }
}
