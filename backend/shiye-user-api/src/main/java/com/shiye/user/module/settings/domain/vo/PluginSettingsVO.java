package com.shiye.user.module.settings.domain.vo;

import java.util.List;

public record PluginSettingsVO(
        boolean ballEnabled,
        String defaultMode,
        String defaultScope,
        String shortcut,
        List<String> siteBlacklist
) {
}
