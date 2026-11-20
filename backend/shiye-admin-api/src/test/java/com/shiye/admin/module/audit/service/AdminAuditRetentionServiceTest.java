package com.shiye.admin.module.audit.service;

import com.shiye.admin.module.audit.mapper.AdminAuditMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuditRetentionServiceTest {

    private final AdminAuditMapper auditMapper = mock(AdminAuditMapper.class);
    private final AdminAuditRetentionService service = new AdminAuditRetentionService(auditMapper);

    @Test
    void cleanupDeletesRowsOlderThanRetentionDays() {
        ReflectionTestUtils.setField(service, "retentionDays", 30);
        when(auditMapper.deleteOlderThan(any())).thenReturn(7);

        int deleted = service.cleanup();

        assertEquals(7, deleted);
        verify(auditMapper).deleteOlderThan(org.mockito.ArgumentMatchers.argThat(
                cutoff -> cutoff.isBefore(LocalDateTime.now(ZoneOffset.UTC).minusDays(29))));
    }

    @Test
    void cleanupNeverUsesLessThanOneDay() {
        ReflectionTestUtils.setField(service, "retentionDays", 0);
        when(auditMapper.deleteOlderThan(any())).thenAnswer(invocation -> {
            LocalDateTime cutoff = invocation.getArgument(0);
            assertTrue(cutoff.isBefore(LocalDateTime.now(ZoneOffset.UTC).minusHours(23)));
            return 0;
        });

        assertEquals(0, service.cleanup());
    }
}
