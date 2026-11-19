package com.shiye.user.module.settings.domain.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PluginSettingsUpdateRequest(
        Boolean ballEnabled,
        @Pattern(regexp = "LIBRARY|CURRENT|GENERAL", message = "defaultMode 非法")
        String defaultMode,
        @Pattern(regexp = "ALL|FOLDER|TAG", message = "defaultScope 非法")
        String defaultScope,
        @Size(max = 64) String shortcut,
        List<@Size(max = 255) String> siteBlacklist
) {
}
