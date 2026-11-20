package com.shiye.user.module.ingest.service.impl;

import com.shiye.user.module.ingest.domain.entity.IngestTaskEntity;
import com.shiye.user.module.ingest.mapper.IngestTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestTaskServiceImplTest {

    private final IngestTaskMapper mapper = mock(IngestTaskMapper.class);
    private final IngestTaskServiceImpl service = new IngestTaskServiceImpl(mapper);

    @Test
    void beginCreatesRunningTask() {
        when(mapper.insert(any(IngestTaskEntity.class))).thenAnswer(invocation -> {
            IngestTaskEntity task = invocation.getArgument(0);
            task.setId(8L);
            return 1;
        });

        long taskId = service.begin(10L, 20L, "UPLOAD", "CHUNK");

        assertEquals(8L, taskId);
        ArgumentCaptor<IngestTaskEntity> captor = ArgumentCaptor.forClass(IngestTaskEntity.class);
        verify(mapper).insert(captor.capture());
        assertEquals("UPLOAD", captor.getValue().getSourceType());
        assertEquals("CHUNK", captor.getValue().getStage());
        assertEquals("RUNNING", captor.getValue().getStatus());
        assertTrue(captor.getValue().getIdempotencyKey().startsWith("ingest:UPLOAD:20:"));
    }

    @Test
    void lifecycleUpdatesMapper() {
        service.advance(8L, "EMBED");
        service.complete(8L);
        service.fail(8L, "PARSE_EMPTY", "没有有效正文");

        verify(mapper).updateStage(8L, "EMBED");
        verify(mapper).markSuccess(8L);
        verify(mapper).markFailed(8L, "PARSE_EMPTY", "没有有效正文");
    }

    @Test
    void zeroTaskIdIsNoop() {
        service.advance(0L, "EMBED");
        service.complete(0L);
        service.fail(0L, "X", "Y");

        verify(mapper, org.mockito.Mockito.never()).updateStage(0L, "EMBED");
    }
}
