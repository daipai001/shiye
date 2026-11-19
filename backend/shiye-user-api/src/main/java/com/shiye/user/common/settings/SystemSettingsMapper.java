package com.shiye.user.common.settings;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SystemSettingsMapper {

    @Select("""
            SELECT setting_value
            FROM system_settings
            WHERE setting_key = 'system.settings'
            LIMIT 1
            """)
    String findSystemSettings();
}
