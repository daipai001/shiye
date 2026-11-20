package com.shiye.admin.module.teams.service.impl;

import com.shiye.admin.module.teams.domain.dto.AdminTeamQuotaRequest;
import com.shiye.admin.module.teams.domain.entity.AdminTeamRowEntity;
import com.shiye.admin.module.teams.mapper.AdminTeamMapper;
import com.shiye.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminTeamServiceImplTest {

    private final AdminTeamMapper teamMapper = mock(AdminTeamMapper.class);
    private final AdminTeamServiceImpl service = new AdminTeamServiceImpl(teamMapper);

    @Test
    void updateQuotaRejectsMissingTeam() {
        when(teamMapper.findById(anyLong(), anyString())).thenReturn(null);

        assertThrows(BizException.class, () -> service.updateQuota(
                1L, new AdminTeamQuotaRequest(100000L, BigDecimal.TEN)));

        verify(teamMapper, never()).updateQuota(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateQuotaWritesAndReturnsLatestValues() {
        AdminTeamRowEntity row = new AdminTeamRowEntity();
        row.setId(1L);
        row.setName("团队");
        row.setOwnerUserId(10L);
        row.setOwnerEmail("owner@example.com");
        row.setStatus("ACTIVE");
        row.setMemberCount(2L);
        row.setMonthlyTokenQuota(500000L);
        row.setMonthlyCostQuota(new BigDecimal("10.000000"));
        row.setTokensUsed(1000L);
        row.setCostUsed(new BigDecimal("0.1000"));
        row.setCalls(3L);
        when(teamMapper.findById(anyLong(), anyString())).thenReturn(row);

        var result = service.updateQuota(
                1L, new AdminTeamQuotaRequest(500000L, new BigDecimal("10")));

        verify(teamMapper).updateQuota(1L, 500000, new BigDecimal("10"));
        assertEquals(500000, result.monthlyTokenQuota());
        assertEquals(0, new BigDecimal("10").compareTo(result.monthlyCostQuota()));
    }
}
