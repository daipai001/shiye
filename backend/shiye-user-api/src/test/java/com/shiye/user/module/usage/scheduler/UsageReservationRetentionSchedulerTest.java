package com.shiye.user.module.usage.scheduler;

import com.shiye.user.module.usage.mapper.UsageReservationMapper;
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

class UsageReservationRetentionSchedulerTest {

    private final UsageReservationMapper mapper = mock(UsageReservationMapper.class);

    @Test
    void purgesReleasedReservationsInBatches() {
        when(mapper.deleteReleasedBefore(any(LocalDateTime.class), eq(1000)))
                .thenReturn(1000, 30);
        UsageReservationRetentionScheduler scheduler =
                new UsageReservationRetentionScheduler(
                        mapper, true, 90, 1000, new SimpleMeterRegistry());

        scheduler.purge();

        verify(mapper, times(2)).deleteReleasedBefore(any(LocalDateTime.class), eq(1000));
    }

    @Test
    void doesNothingWhenDisabled() {
        UsageReservationRetentionScheduler scheduler =
                new UsageReservationRetentionScheduler(
                        mapper, false, 90, 1000, new SimpleMeterRegistry());

        scheduler.purge();

        verify(mapper, never()).deleteReleasedBefore(any(), eq(1000));
    }
}
