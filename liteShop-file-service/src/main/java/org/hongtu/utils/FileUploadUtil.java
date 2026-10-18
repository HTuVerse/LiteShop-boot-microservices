package org.hongtu.utils;

import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.hongtu.config.FileUploadConfig;
import org.hongtu.config.QiniuConfig;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/**
 * 文件上传工具类
 */
@Slf4j
@Component
public class FileUploadUtil {

    @Resource
    private FileUploadConfig fileUploadConfig;

    @Resource
    private QiniuConfig qiniuConfig;

    /**
     * 上传文件
     * @param file 文件
     * @return 文件访问URL
     * @throws IOException 上传异常
     */
    public String uploadFile(MultipartFile file) throws IOException {
        return upload(file, false);
    }

    /** 上传公告等业务附件，使用固定安全扩展名白名单。 */
    public String uploadAttachment(MultipartFile file) throws IOException {
        return upload(file, true);
    }

    private String upload(MultipartFile file, boolean attachment) throws IOException {
        // 检查文件是否为空
        if (file.isEmpty()) {
            throw new IOException("上传的文件为空");
        }
        // 获取文件原始名称和扩展名
        String originalFilename = file.getOriginalFilename();
        String extension = FilenameUtils.getExtension(originalFilename);
        // 检查文件类型是否允许
        boolean allowed = attachment
                ? fileUploadConfig.isAllowedAttachmentType(extension)
                : fileUploadConfig.isAllowedFileType(extension);
        if (!allowed) {
            throw new IOException("不支持的文件类型: " + extension);
        }
        // 检查文件大小
        long fileSize = file.getSize();
        int maxSize = attachment ? 20 : fileUploadConfig.getMaxSize();
        if (fileSize > maxSize * 1024L * 1024L) {
            throw new IOException("文件大小超过限制: " + maxSize + "MB");
        }
        // 生成新的文件名
        String newFileName = generateFileName(extension);
        // 根据配置选择上传方式
        if (fileUploadConfig.isOssEnabled()) {
            // 使用七牛云OSS存储
            return uploadToQiniu(file, newFileName);
        } else {
            // 使用本地存储
            return uploadToLocal(file, newFileName);
        }
    }

    /**
     * 上传到本地存储
     * @param file 文件
     * @param fileName 文件名
     * @return 文件访问URL
     * @throws IOException 上传异常
     */
    private String uploadToLocal(MultipartFile file, String fileName) throws IOException {
        // 创建日期子目录
        String datePath = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String relativePath = datePath + "/" + fileName;
        // 确保目录存在
        // Servlet MultipartFile 对相对目标可能按临时目录解析，必须传入绝对路径。
        Path uploadDir = Paths.get(fileUploadConfig.getUploadPath(), datePath).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        // 保存文件
        Path filePath = uploadDir.resolve(fileName);
        file.transferTo(filePath.toFile());
        // 返回文件访问URL
        return fileUploadConfig.getBaseUrl() + relativePath;
    }

    /**
     * 上传到七牛云OSS
     * @param file 文件
     * @param fileName 文件名
     * @return 文件访问URL
     * @throws IOException 上传异常
     */
    private String uploadToQiniu(MultipartFile file, String fileName) throws IOException {
        // 创建日期子目录
        String datePath = new SimpleDateFormat("yyyyMMdd").format(new Date());
        String key = datePath + "/" + fileName;

        try {
            // 创建七牛云上传配置
            Configuration cfg = Configuration.create(Region.autoRegion());
            UploadManager uploadManager = new UploadManager(cfg);

            // 获取上传凭证
            Auth auth = Auth.create(qiniuConfig.getAccessKey(), qiniuConfig.getSecretKey());
            String upToken = auth.uploadToken(qiniuConfig.getBucket());

            // 上传文件
            Response response = uploadManager.put(file.getBytes(), key, upToken);
            if (!response.isOK()) {
                throw new IOException("七牛云上传失败: " + response.error);
            }

            // 返回文件访问URL
            return qiniuConfig.getDomain() + "/" + key;
        } catch (QiniuException ex) {
            throw new IOException("七牛云上传异常: " + ex.getMessage());
        }
    }

    /**
     * 生成唯一的文件名
     * @param extension 文件扩展名
     * @return 新的文件名
     */
    private String generateFileName(String extension) {
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        return uuid + "." + extension;
    }
} 
