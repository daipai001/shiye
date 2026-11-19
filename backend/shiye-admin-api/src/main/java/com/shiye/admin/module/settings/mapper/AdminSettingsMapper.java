package com.shiye.admin.module.settings.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminSettingsMapper {

    @Select("""
            SELECT setting_value
            FROM system_settings
            WHERE setting_key = 'system.settings'
            LIMIT 1
            """)
    String findSettings();

    @Insert("""
            INSERT INTO system_settings (setting_key, setting_value, is_secret, description, updated_by)
            VALUES ('system.settings', CAST(#{value} AS JSON), 0,
                    '注册开关、默认配额、全局预算和限流', #{adminId})
            ON DUPLICATE KEY UPDATE
                setting_value = VALUES(setting_value),
                updated_by = VALUES(updated_by),
                updated_at = UTC_TIMESTAMP(3)
            """)
    int upsertSettings(@Param("value") String value, @Param("adminId") Long adminId);
}
