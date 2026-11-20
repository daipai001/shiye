package com.shiye.user.module.maintenance.service.impl;

import com.shiye.user.module.maintenance.mapper.RetentionMapper;
import com.shiye.user.module.maintenance.service.RetentionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionServiceImplTest {

    private final RetentionMapper mapper = mock(RetentionMapper.class);
    private final RetentionServiceImpl service = new RetentionServiceImpl(
            mapper, 30, 7, 1, 30, 1000);

    @Test
    void cleansAllConfiguredTables() {
        when(mapper.deleteRefreshTokens(any(LocalDateTime.class), anyInt())).thenReturn(3);
        when(mapper.deleteExtensionCodes(any(LocalDateTime.class), anyInt())).thenReturn(2);
        when(mapper.deleteIdempotencyKeys(any(LocalDateTime.class), anyInt())).thenReturn(1);
        when(mapper.deleteIngestTasks(any(LocalDateTime.class), anyInt())).thenReturn(4);

        RetentionService.RetentionResult result = service.cleanup();

        assertEquals(3, result.refreshTokens());
        assertEquals(2, result.extensionCodes());
        assertEquals(1, result.idempotencyKeys());
        assertEquals(4, result.ingestTasks());
        verify(mapper).deleteRefreshTokens(any(LocalDateTime.class), anyInt());
        verify(mapper).deleteExtensionCodes(any(LocalDateTime.class), anyInt());
        verify(mapper).deleteIdempotencyKeys(any(LocalDateTime.class), anyInt());
        verify(mapper).deleteIngestTasks(any(LocalDateTime.class), anyInt());
    }
}
