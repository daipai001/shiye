package com.shiye.admin.module.models.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminModelConfigMapper {

    @Select("""
            SELECT setting_value
            FROM system_settings
            WHERE setting_key = 'ai.model.config'
            LIMIT 1
            """)
    String findModelConfig();

    @Insert("""
            INSERT INTO system_settings (setting_key, setting_value, is_secret, description, updated_by)
            VALUES ('ai.model.config', CAST(#{value} AS JSON), 1,
                    'AI 模型运行时配置（Chat / Embedding / Rerank）', #{adminId})
            ON DUPLICATE KEY UPDATE
                setting_value = VALUES(setting_value),
                updated_by = VALUES(updated_by),
                updated_at = UTC_TIMESTAMP(3)
            """)
    int upsertModelConfig(@Param("value") String value, @Param("adminId") Long adminId);
}
