package org.hongtu.storage;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 按文件头识别受支持类型，避免仅靠扩展名或浏览器声明的 MIME 上传。 */
@Component
public class FileTypeSniffer {
    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("pdf", "application/pdf"),
            Map.entry("zip", "application/zip"), Map.entry("rar", "application/vnd.rar"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("doc", "application/msword"), Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"), Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"), Map.entry("mp4", "video/mp4"), Map.entry("webm", "video/webm"));

    public String detect(MultipartFile file, String extension) throws Exception {
        String ext = extension.toLowerCase();
        String mime = MIME.get(ext);
        if (mime == null) throw new IllegalArgumentException("不支持的文件类型");
        byte[] head;
        try (InputStream stream = file.getInputStream()) { head = stream.readNBytes(32); }
        int count = head.length;
        if (count < 4 || !matches(ext, head, count)) throw new IllegalArgumentException("文件内容与扩展名不匹配");
        return mime;
    }

    private boolean matches(String ext, byte[] b, int n) {
        String ascii = new String(b, 0, n, StandardCharsets.ISO_8859_1);
        return switch (ext) {
            case "jpg", "jpeg" -> (b[0] & 255) == 0xff && (b[1] & 255) == 0xd8 && (b[2] & 255) == 0xff;
            case "png" -> ascii.startsWith("\u0089PNG\r\n\u001a\n");
            case "gif" -> ascii.startsWith("GIF87a") || ascii.startsWith("GIF89a");
            case "webp" -> ascii.startsWith("RIFF") && ascii.substring(8).startsWith("WEBP");
            case "pdf" -> ascii.startsWith("%PDF-");
            case "zip", "docx", "xlsx", "pptx" -> ascii.startsWith("PK\u0003\u0004");
            case "doc", "xls", "ppt" -> (b[0] & 255) == 0xd0 && (b[1] & 255) == 0xcf
                    && (b[2] & 255) == 0x11 && (b[3] & 255) == 0xe0;
            case "rar" -> ascii.startsWith("Rar!\u001a\u0007");
            case "mp4" -> n >= 12 && ascii.substring(4).startsWith("ftyp");
            case "webm" -> (b[0] & 255) == 0x1a && (b[1] & 255) == 0x45
                    && (b[2] & 255) == 0xdf && (b[3] & 255) == 0xa3;
            case "txt", "csv" -> ascii.chars().noneMatch(value -> value == 0 || (value < 9));
            default -> false;
        };
    }
}
