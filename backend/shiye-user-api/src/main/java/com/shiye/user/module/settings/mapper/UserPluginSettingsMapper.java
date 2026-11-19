package com.shiye.user.module.settings.mapper;

import com.shiye.user.module.settings.domain.entity.UserPluginSettingsEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserPluginSettingsMapper {

    @Select("""
            SELECT id, user_id, ball_enabled, default_mode, default_scope, default_scope_id,
                   shortcut, site_blacklist, created_at, updated_at
            FROM user_plugin_settings
            WHERE user_id = #{userId}
            LIMIT 1
            """)
    UserPluginSettingsEntity findByUserId(@Param("userId") long userId);

    @Insert("""
            INSERT INTO user_plugin_settings
                (user_id, ball_enabled, default_mode, default_scope, default_scope_id,
                 shortcut, site_blacklist)
            VALUES (#{userId}, #{ballEnabled}, #{defaultMode}, #{defaultScope}, #{defaultScopeId},
                    #{shortcut}, CAST(#{siteBlacklist} AS JSON))
            ON DUPLICATE KEY UPDATE
                ball_enabled = VALUES(ball_enabled),
                default_mode = VALUES(default_mode),
                default_scope = VALUES(default_scope),
                default_scope_id = VALUES(default_scope_id),
                shortcut = VALUES(shortcut),
                site_blacklist = VALUES(site_blacklist),
                updated_at = UTC_TIMESTAMP(3)
            """)
    int upsert(UserPluginSettingsEntity entity);
}
