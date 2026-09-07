package org.hongtu.config;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 文件存储初始化监听器
 * 在应用启动时创建上传目录
 */
@Slf4j
@Component
public class FileStorageInitializer implements CommandLineRunner {

    @Resource
    private FileUploadConfig fileUploadConfig;

    @Override
    public void run(String... args) throws Exception {
        // 如果启用了本地存储，确保上传目录存在
        if (!fileUploadConfig.isOssEnabled()) {
            String uploadPath = fileUploadConfig.getUploadPath();
            
            // 如果路径不是绝对路径，则转换为绝对路径
            if (!Paths.get(uploadPath).isAbsolute()) {
                uploadPath = System.getProperty("user.dir") + File.separator + uploadPath;
            }
            
            // 创建目录
            Path path = Paths.get(uploadPath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("创建文件上传目录: {}", path.toAbsolutePath());
            } else {
                log.info("文件上传目录已存在: {}", path.toAbsolutePath());
            }
            
            // 更新配置中的路径为绝对路径
            fileUploadConfig.setUploadPath(path.toAbsolutePath().toString());
        }
    }
} 