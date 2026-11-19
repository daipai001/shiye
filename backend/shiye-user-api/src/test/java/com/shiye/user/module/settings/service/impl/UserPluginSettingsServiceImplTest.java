package com.shiye.user.module.settings.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.module.bookmark.service.BookmarkFolderService;
import com.shiye.user.module.settings.domain.dto.PluginSettingsUpdateRequest;
import com.shiye.user.module.settings.domain.entity.UserPluginSettingsEntity;
import com.shiye.user.module.settings.domain.vo.PluginSettingsVO;
import com.shiye.user.module.settings.mapper.UserPluginSettingsMapper;
import com.shiye.user.module.tag.service.TagService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPluginSettingsServiceImplTest {

    private final UserPluginSettingsMapper mapper = mock(UserPluginSettingsMapper.class);
    private final BookmarkFolderService folderService = mock(BookmarkFolderService.class);
    private final TagService tagService = mock(TagService.class);
    private final UserPluginSettingsServiceImpl service = new UserPluginSettingsServiceImpl(
            mapper, new ObjectMapper(), folderService, tagService);

    @Test
    void rejectsFolderScopeWithoutId() {
        BizException ex = assertThrows(BizException.class, () ->
                service.update(10L, new PluginSettingsUpdateRequest(
                        true, "LIBRARY", "FOLDER", null, null, List.of())));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void rejectsFolderNotOwnedByUser() {
        when(folderService.existsForUser(10L, 7L)).thenReturn(false);

        BizException ex = assertThrows(BizException.class, () ->
                service.update(10L, new PluginSettingsUpdateRequest(
                        true, "LIBRARY", "FOLDER", "7", null, List.of())));

        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    void savesFolderScope() {
        when(folderService.existsForUser(10L, 7L)).thenReturn(true);

        PluginSettingsVO result = service.update(10L, new PluginSettingsUpdateRequest(
                true, "LIBRARY", "FOLDER", "7", null, List.of()));

        assertEquals("FOLDER", result.defaultScope());
        assertEquals("7", result.defaultScopeId());
        verify(mapper).upsert(any(UserPluginSettingsEntity.class));
    }

    @Test
    void savesTagScope() {
        when(tagService.existsForUser(10L, 9L)).thenReturn(true);

        PluginSettingsVO result = service.update(10L, new PluginSettingsUpdateRequest(
                true, "LIBRARY", "TAG", "9", null, List.of()));

        assertEquals("TAG", result.defaultScope());
        assertEquals("9", result.defaultScopeId());
    }

    @Test
    void allScopeClearsId() {
        UserPluginSettingsEntity existing = new UserPluginSettingsEntity();
        existing.setUserId(10L);
        existing.setBallEnabled(true);
        existing.setDefaultMode("LIBRARY");
        existing.setDefaultScope("FOLDER");
        existing.setDefaultScopeId(7L);
        when(mapper.findByUserId(10L)).thenReturn(existing);

        PluginSettingsVO result = service.update(10L, new PluginSettingsUpdateRequest(
                true, "LIBRARY", "ALL", null, null, List.of()));

        assertEquals("ALL", result.defaultScope());
        assertNull(result.defaultScopeId());
    }

    @Test
    void generalModeNormalizesToLibrary() {
        UserPluginSettingsEntity existing = new UserPluginSettingsEntity();
        existing.setUserId(10L);
        existing.setBallEnabled(true);
        existing.setDefaultMode("GENERAL");
        existing.setDefaultScope("ALL");
        when(mapper.findByUserId(10L)).thenReturn(existing);

        PluginSettingsVO result = service.get(10L);

        assertEquals("LIBRARY", result.defaultMode());
    }
}
