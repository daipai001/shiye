package com.shiye.user.module.team.scheduler;

import com.shiye.user.module.team.mapper.TeamAuditLogMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamAuditRetentionSchedulerTest {

    private final TeamAuditLogMapper mapper = mock(TeamAuditLogMapper.class);

    @Test
    void purgesAuditLogsInBatches() {
        when(mapper.deleteBefore(any(LocalDateTime.class), eq(1000)))
                .thenReturn(1000, 30);
        TeamAuditRetentionScheduler scheduler =
                new TeamAuditRetentionScheduler(
                        mapper, true, 180, 1000, new SimpleMeterRegistry());

        scheduler.purge();

        verify(mapper, times(2)).deleteBefore(any(LocalDateTime.class), eq(1000));
    }

    @Test
    void doesNothingWhenDisabled() {
        TeamAuditRetentionScheduler scheduler =
                new TeamAuditRetentionScheduler(
                        mapper, false, 180, 1000, new SimpleMeterRegistry());

        scheduler.purge();

        verify(mapper, never()).deleteBefore(any(), eq(1000));
    }
}
