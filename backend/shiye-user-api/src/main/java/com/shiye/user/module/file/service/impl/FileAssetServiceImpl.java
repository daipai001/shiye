package com.shiye.user.module.file.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.module.file.domain.entity.FileAssetEntity;
import com.shiye.user.module.file.mapper.FileAssetMapper;
import com.shiye.user.module.file.service.FileAssetService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class FileAssetServiceImpl implements FileAssetService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");

    private final FileAssetMapper fileAssetMapper;
    private final Path uploadRoot;

    public FileAssetServiceImpl(
            FileAssetMapper fileAssetMapper,
            @Value("${shiye.file.upload-dir:./data/uploads}") String uploadDir) {
        this.fileAssetMapper = fileAssetMapper;
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public FileAssetEntity findOrCreate(long userId, byte[] bytes, String originalName,
                                        String mimeType, String sha256, String extension) {
        FileAssetEntity existing = fileAssetMapper.findByUserAndSha(userId, sha256);
        if (existing != null) {
            return existing;
        }
        String safeExtension = sanitizeExtension(extension);
        String objectKey = userId + "/" + YearMonth.now(ZoneOffset.UTC).format(MONTH) + "/"
                + UUID.randomUUID().toString().replace("-", "") + safeExtension;
        Path target = uploadRoot.resolve(objectKey).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件路径不合法");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
            FileAssetEntity entity = new FileAssetEntity();
            entity.setUserId(userId);
            entity.setStorageType("LOCAL");
            entity.setObjectKey(objectKey);
            entity.setOriginalName(truncate(originalName, 255));
            entity.setMimeType(truncate(mimeType, 128));
            entity.setSizeBytes((long) bytes.length);
            entity.setSha256(sha256);
            entity.setStatus("READY");
            try {
                fileAssetMapper.insert(entity);
                return entity;
            } catch (DuplicateKeyException ex) {
                safeDelete(target);
                FileAssetEntity duplicated = fileAssetMapper.findByUserAndSha(userId, sha256);
                if (duplicated != null) {
                    return duplicated;
                }
                throw ex;
            }
        } catch (IOException ex) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败");
        } catch (RuntimeException ex) {
            safeDelete(target);
            throw ex;
        }
    }

    private String sanitizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return ".bin";
        }
        String value = extension.trim().toLowerCase();
        if (!value.startsWith(".")) {
            value = "." + value;
        }
        return value.matches("\\.[a-z0-9]{1,10}") ? value : ".bin";
    }

    private void safeDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 忽略清理失败，避免覆盖原始异常
        }
    }

    private String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return "upload";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
