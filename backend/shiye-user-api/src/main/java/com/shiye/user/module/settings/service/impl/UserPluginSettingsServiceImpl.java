package com.shiye.user.module.settings.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.module.bookmark.service.BookmarkFolderService;
import com.shiye.user.module.settings.domain.dto.PluginSettingsUpdateRequest;
import com.shiye.user.module.settings.domain.entity.UserPluginSettingsEntity;
import com.shiye.user.module.settings.domain.vo.PluginSettingsVO;
import com.shiye.user.module.settings.mapper.UserPluginSettingsMapper;
import com.shiye.user.module.settings.service.UserPluginSettingsService;
import com.shiye.user.module.tag.service.TagService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserPluginSettingsServiceImpl implements UserPluginSettingsService {

    private static final Set<String> MODES = Set.of("LIBRARY", "CURRENT");
    private static final Set<String> SCOPES = Set.of("ALL", "FOLDER", "TAG");
    private static final int MAX_BLACKLIST = 100;

    private final UserPluginSettingsMapper mapper;
    private final ObjectMapper objectMapper;
    private final BookmarkFolderService folderService;
    private final TagService tagService;

    public UserPluginSettingsServiceImpl(UserPluginSettingsMapper mapper,
                                         ObjectMapper objectMapper,
                                         BookmarkFolderService folderService,
                                         TagService tagService) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.folderService = folderService;
        this.tagService = tagService;
    }

    @Override
    public PluginSettingsVO get(long userId) {
        UserPluginSettingsEntity entity = mapper.findByUserId(userId);
        if (entity == null) {
            return defaults();
        }
        return toVO(entity);
    }

    @Override
    @Transactional
    public PluginSettingsVO update(long userId, PluginSettingsUpdateRequest request) {
        UserPluginSettingsEntity current = mapper.findByUserId(userId);
        PluginSettingsVO existing = current == null ? defaults() : toVO(current);
        boolean ballEnabled = request.ballEnabled() == null
                ? existing.ballEnabled() : request.ballEnabled();
        String defaultMode = request.defaultMode() == null
                ? existing.defaultMode()
                : request.defaultMode().trim().toUpperCase(Locale.ROOT);
        String defaultScope = request.defaultScope() == null
                ? existing.defaultScope()
                : request.defaultScope().trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(defaultMode) || !SCOPES.contains(defaultScope)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "插件设置枚举值非法");
        }
        String scopeIdText = request.defaultScopeId() == null
                ? existing.defaultScopeId() : trimToNull(request.defaultScopeId());
        Long defaultScopeId = "ALL".equals(defaultScope)
                ? null : requireScopeId(userId, defaultScope, scopeIdText);
        String shortcut = request.shortcut() == null
                ? existing.shortcut() : trimToNull(request.shortcut());
        List<String> blacklist = request.siteBlacklist() == null
                ? existing.siteBlacklist() : normalizeBlacklist(request.siteBlacklist());

        UserPluginSettingsEntity entity = new UserPluginSettingsEntity();
        entity.setUserId(userId);
        entity.setBallEnabled(ballEnabled);
        entity.setDefaultMode(defaultMode);
        entity.setDefaultScope(defaultScope);
        entity.setDefaultScopeId(defaultScopeId);
        entity.setShortcut(shortcut);
        try {
            entity.setSiteBlacklist(objectMapper.writeValueAsString(blacklist));
        } catch (Exception ex) {
            throw new IllegalStateException("保存插件设置失败", ex);
        }
        mapper.upsert(entity);
        return new PluginSettingsVO(
                ballEnabled,
                defaultMode,
                defaultScope,
                defaultScopeId == null ? null : String.valueOf(defaultScopeId),
                shortcut,
                blacklist
        );
    }

    private PluginSettingsVO defaults() {
        return new PluginSettingsVO(true, "LIBRARY", "ALL", null, null, List.of());
    }

    private Long requireScopeId(long userId, String scope, String value) {
        if (value == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    "选择文件夹或标签范围时必须提供 defaultScopeId");
        }
        long scopeId;
        try {
            scopeId = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "defaultScopeId 必须是数字");
        }
        if (scopeId <= 0) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "defaultScopeId 必须大于 0");
        }
        boolean exists = "FOLDER".equals(scope)
                ? folderService.existsForUser(userId, scopeId)
                : tagService.existsForUser(userId, scopeId);
        if (!exists) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "默认范围对应的文件夹或标签不存在");
        }
        return scopeId;
    }

    private PluginSettingsVO toVO(UserPluginSettingsEntity entity) {
        String mode = "CURRENT".equals(entity.getDefaultMode()) ? "CURRENT" : "LIBRARY";
        String scope = "FOLDER".equals(entity.getDefaultScope())
                || "TAG".equals(entity.getDefaultScope())
                ? entity.getDefaultScope() : "ALL";
        if (!"ALL".equals(scope) && entity.getDefaultScopeId() == null) {
            scope = "ALL";
        }
        return new PluginSettingsVO(
                !Boolean.FALSE.equals(entity.getBallEnabled()),
                mode,
                scope,
                entity.getDefaultScopeId() == null
                        ? null : String.valueOf(entity.getDefaultScopeId()),
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
            item = item.toLowerCase(Locale.ROOT);
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
