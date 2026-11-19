package com.shiye.user.module.file.service;

import com.shiye.user.module.file.domain.entity.FileAssetEntity;

public interface FileAssetService {

    FileAssetEntity findOrCreate(long userId, byte[] bytes, String originalName,
                                 String mimeType, String sha256, String extension);
}
