package com.shiye.user.module.settings.domain.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PluginSettingsUpdateRequest(
        Boolean ballEnabled,
        @Pattern(regexp = "LIBRARY|CURRENT", message = "defaultMode 仅支持 LIBRARY / CURRENT")
        String defaultMode,
        @Pattern(regexp = "ALL|FOLDER|TAG", message = "defaultScope 非法")
        String defaultScope,
        @Pattern(regexp = "\\d+", message = "defaultScopeId 必须是数字")
        String defaultScopeId,
        @Size(max = 64) String shortcut,
        List<@Size(max = 255) String> siteBlacklist
) {
}
