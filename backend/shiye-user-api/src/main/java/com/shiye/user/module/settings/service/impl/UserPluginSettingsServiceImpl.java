package com.shiye.user.module.settings.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.module.settings.domain.dto.PluginSettingsUpdateRequest;
import com.shiye.user.module.settings.domain.entity.UserPluginSettingsEntity;
import com.shiye.user.module.settings.domain.vo.PluginSettingsVO;
import com.shiye.user.module.settings.mapper.UserPluginSettingsMapper;
import com.shiye.user.module.settings.service.UserPluginSettingsService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserPluginSettingsServiceImpl implements UserPluginSettingsService {

    private static final Set<String> MODES = Set.of("LIBRARY", "CURRENT", "GENERAL");
    private static final Set<String> SCOPES = Set.of("ALL", "FOLDER", "TAG");
    private static final int MAX_BLACKLIST = 100;

    private final UserPluginSettingsMapper mapper;
    private final ObjectMapper objectMapper;

    public UserPluginSettingsServiceImpl(UserPluginSettingsMapper mapper,
                                         ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PluginSettingsVO get(long userId) {
        UserPluginSettingsEntity entity = mapper.findByUserId(userId);
        if (entity == null) {
            return new PluginSettingsVO(true, "LIBRARY", "ALL", null, List.of());
        }
        return toVO(entity);
    }

    @Override
    public PluginSettingsVO update(long userId, PluginSettingsUpdateRequest request) {
        UserPluginSettingsEntity current = mapper.findByUserId(userId);
        PluginSettingsVO existing = current == null ? get(userId) : toVO(current);
        boolean ballEnabled = request.ballEnabled() == null
                ? existing.ballEnabled() : request.ballEnabled();
        String defaultMode = request.defaultMode() == null
                ? existing.defaultMode() : request.defaultMode().trim().toUpperCase();
        String defaultScope = request.defaultScope() == null
                ? existing.defaultScope() : request.defaultScope().trim().toUpperCase();
        if (!MODES.contains(defaultMode) || !SCOPES.contains(defaultScope)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "插件设置枚举值非法");
        }
        String shortcut = request.shortcut() == null
                ? existing.shortcut() : trimToNull(request.shortcut());
        List<String> blacklist = request.siteBlacklist() == null
                ? existing.siteBlacklist() : normalizeBlacklist(request.siteBlacklist());

        UserPluginSettingsEntity entity = new UserPluginSettingsEntity();
        entity.setUserId(userId);
        entity.setBallEnabled(ballEnabled);
        entity.setDefaultMode(defaultMode);
        entity.setDefaultScope(defaultScope);
        entity.setShortcut(shortcut);
        try {
            entity.setSiteBlacklist(objectMapper.writeValueAsString(blacklist));
        } catch (Exception ex) {
            throw new IllegalStateException("保存插件设置失败", ex);
        }
        mapper.upsert(entity);
        return new PluginSettingsVO(ballEnabled, defaultMode, defaultScope, shortcut, blacklist);
    }

    private PluginSettingsVO toVO(UserPluginSettingsEntity entity) {
        return new PluginSettingsVO(
                !Boolean.FALSE.equals(entity.getBallEnabled()),
                entity.getDefaultMode() == null ? "LIBRARY" : entity.getDefaultMode(),
                entity.getDefaultScope() == null ? "ALL" : entity.getDefaultScope(),
                entity.getShortcut(),
                parseBlacklist(entity.getSiteBlacklist())
        );
    }

    private List<String> parseBlacklist(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> normalizeBlacklist(List<String> values) {
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String item = trimToNull(value);
            if (item == null) {
                continue;
            }
            item = item.toLowerCase();
            if (item.startsWith("http://")) {
                item = item.substring(7);
            } else if (item.startsWith("https://")) {
                item = item.substring(8);
            }
            int slash = item.indexOf('/');
            if (slash >= 0) {
                item = item.substring(0, slash);
            }
            if (!item.isBlank()) {
                unique.add(item);
            }
            if (unique.size() >= MAX_BLACKLIST) {
                break;
            }
        }
        return new ArrayList<>(unique);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 64 ? trimmed : trimmed.substring(0, 64);
    }
}
