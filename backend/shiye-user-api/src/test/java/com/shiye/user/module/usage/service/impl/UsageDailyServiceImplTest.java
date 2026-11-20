package com.shiye.user.module.usage.service.impl;

import com.shiye.user.module.usage.mapper.AiUsageDailyMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageDailyServiceImplTest {

    private final AiUsageDailyMapper mapper = mock(AiUsageDailyMapper.class);
    private final UsageDailyServiceImpl service = new UsageDailyServiceImpl(mapper);

    @Test
    void aggregatesSingleDayRange() {
        LocalDate date = LocalDate.of(2026, 11, 19);
        when(mapper.aggregate(date.atStartOfDay(), date.plusDays(1).atStartOfDay()))
                .thenReturn(3);

        assertEquals(3, service.aggregate(date));
        verify(mapper).aggregate(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
    }

    @Test
    void aggregatesRecentDays() {
        when(mapper.aggregate(any(), any())).thenReturn(2);

        assertEquals(6, service.aggregateRecent(3));
    }
}
