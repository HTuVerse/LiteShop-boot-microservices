package org.hongtu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

/**
 * 文件上传配置类
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "file")
public class FileUploadConfig {
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
} 