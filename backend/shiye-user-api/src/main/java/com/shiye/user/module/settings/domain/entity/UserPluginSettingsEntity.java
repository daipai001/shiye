package com.shiye.user.module.settings.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserPluginSettingsEntity {
    private Long id;
    private Long userId;
    private Boolean ballEnabled;
    private String defaultMode;
    private String defaultScope;
    private String shortcut;
    private String siteBlacklist;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
