package org.hongtu.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.hongtu.config.FileUploadConfig;
import org.hongtu.DAO.FileUploadMapper;
import org.hongtu.DTO.FileUpload;
import org.hongtu.storage.StorageManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** 根据当前平台配置的目录提供本地上传文件。 */
@RestController
@RequiredArgsConstructor
public class LocalFileController {
    private final FileUploadConfig fileUploadConfig;
    @Autowired(required = false) private FileUploadMapper files;
    @Autowired(required = false) private StorageManager storage;

    @GetMapping("/images/**")
    public ResponseEntity<Resource> image(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length() + "/images/".length());
        if (!path.matches("(?i)[0-9]{8}/[a-f0-9]{32}\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|ppt|pptx|txt|csv|zip|rar|mp4|webm)"))
            return ResponseEntity.notFound().build();
        FileUpload record = files == null ? null : files.findPublicLocal(path);
        if (files != null && record == null) return ResponseEntity.notFound().build();
        String uploadPath = record == null ? fileUploadConfig.currentUploadPath() : storage.settings(record).getUploadPath();
        Path root = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root) || !Files.isRegularFile(target)) return ResponseEntity.notFound().build();
        if (!target.toRealPath().startsWith(root.toRealPath())) return ResponseEntity.notFound().build();
        Resource resource = new UrlResource(target.toUri());
        return ResponseEntity.ok().header("Cache-Control", "public, max-age=31536000, immutable")
                .contentType(MediaTypeFactory.getMediaType(target.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM)).body(resource);
    }
}
