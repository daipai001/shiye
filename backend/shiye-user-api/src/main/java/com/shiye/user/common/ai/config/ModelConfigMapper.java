package com.shiye.user.common.ai.config;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ModelConfigMapper {

    @Select("""
            SELECT setting_value
            FROM system_settings
            WHERE setting_key = 'ai.model.config'
            LIMIT 1
            """)
    String findModelConfig();
}
