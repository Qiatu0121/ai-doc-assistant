package com.example.aidoc.service;

import com.example.aidoc.common.BizException;
import com.example.aidoc.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

/**
 * 本地文件存储：校验文件类型和大小，使用随机文件名落盘，避免原始文件名带来的路径问题。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("pdf", "md", "markdown", "txt", "docx");

    private final StorageProperties properties;

    public StoredFile save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException("文件不能超过 10MB");
        }

        String originalName = normalizeFileName(file.getOriginalFilename());
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException("仅支持 PDF、Markdown、TXT、DOCX 文件");
        }

        try {
            Path uploadDir = Paths.get(properties.getUploadDir())
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(uploadDir);

            String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            Path target = uploadDir.resolve(storedName).normalize();
            if (!target.startsWith(uploadDir)) {
                throw new BizException("文件路径不合法");
            }

            file.transferTo(target.toFile());
            return new StoredFile(target, originalName, extension, file.getSize());
        } catch (IOException e) {
            log.error("保存上传文件失败", e);
            throw new BizException("保存上传文件失败", e);
        }
    }

    public void delete(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(filePath));
        } catch (IOException e) {
            log.warn("删除本地文件失败：{}", filePath, e);
        }
    }

    private String normalizeFileName(String originalName) {
        String name = originalName == null || originalName.isBlank()
                ? "document"
                : originalName.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BizException("文件缺少扩展名");
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    /**
     * 保存结果：绝对路径、原始文件名、扩展名、字节数。
     */
    public record StoredFile(Path path, String originalName, String fileType, long size) {
    }
}
