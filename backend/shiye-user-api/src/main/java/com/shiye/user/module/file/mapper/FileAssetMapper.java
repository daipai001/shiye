package com.shiye.user.module.file.mapper;

import com.shiye.user.module.file.domain.entity.FileAssetEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileAssetMapper {

    @Select("""
            SELECT id, user_id, storage_type, bucket, object_key, original_name, mime_type,
                   size_bytes, sha256, status, created_at, updated_at, deleted_at
            FROM file_assets
            WHERE user_id = #{userId} AND sha256 = #{sha256}
              AND status = 'READY' AND deleted_at IS NULL
            LIMIT 1
            """)
    FileAssetEntity findByUserAndSha(@Param("userId") long userId,
                                     @Param("sha256") String sha256);

    @Insert("""
            INSERT INTO file_assets (user_id, storage_type, bucket, object_key, original_name,
                                     mime_type, size_bytes, sha256, status)
            VALUES (#{userId}, #{storageType}, #{bucket}, #{objectKey}, #{originalName},
                    #{mimeType}, #{sizeBytes}, #{sha256}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FileAssetEntity entity);
}
