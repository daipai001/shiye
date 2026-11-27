package com.shiye.user.module.usage.service.impl;

import com.shiye.common.exception.BizException;
import com.shiye.common.settings.SystemSettings;
import com.shiye.user.module.auth.domain.vo.UserQuotaVO;
import com.shiye.user.module.auth.service.AuthService;
import com.shiye.user.module.usage.domain.entity.TeamUsageMonthlyEntity;
import com.shiye.user.module.usage.domain.entity.UsageReservationEntity;
import com.shiye.user.module.usage.domain.entity.UserUsageMonthlyEntity;
import com.shiye.user.module.usage.domain.vo.ChatReservation;
import com.shiye.user.module.usage.domain.vo.TeamReservation;
import com.shiye.user.module.usage.domain.vo.UsageTotals;
import com.shiye.user.module.usage.mapper.AiUsageMapper;
import com.shiye.user.module.usage.mapper.GlobalUsageMonthlyMapper;
import com.shiye.user.module.usage.mapper.TeamUsageMonthlyMapper;
import com.shiye.user.module.usage.mapper.UsageReservationMapper;
import com.shiye.user.module.usage.mapper.UserUsageMonthlyMapper;
import com.shiye.user.module.team.domain.vo.TeamQuota;
import com.shiye.user.module.team.service.TeamService;
import com.shiye.user.common.settings.RuntimeSystemSettingsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UsageServiceImplTest {

    private final AuthService authService = mock(AuthService.class);
    private final AiUsageMapper aiUsageMapper = mock(AiUsageMapper.class);
    private final UserUsageMonthlyMapper monthlyMapper = mock(UserUsageMonthlyMapper.class);
    private final TeamUsageMonthlyMapper teamUsageMapper = mock(TeamUsageMonthlyMapper.class);
    private final GlobalUsageMonthlyMapper globalUsageMapper = mock(GlobalUsageMonthlyMapper.class);
    private final UsageReservationMapper reservationMapper = mock(UsageReservationMapper.class);
    private final TransactionTemplate transactionTemplate = new TransactionTemplate(
            mock(PlatformTransactionManager.class));
    private final TeamService teamService = mock(TeamService.class);
    private final RuntimeSystemSettingsService settingsService = mock(RuntimeSystemSettingsService.class);
    private final UsageServiceImpl service = new UsageServiceImpl(
            authService, aiUsageMapper, monthlyMapper, teamUsageMapper, globalUsageMapper,
            reservationMapper, transactionTemplate, teamService, settingsService,
            new SimpleMeterRegistry(), 8000, new BigDecimal("0.06"),
            12000, new BigDecimal("0.12"), 15);

    @Test
    void reserveChatAtomicallyAddsPersonalReservation() {
        when(authService.quota(1L)).thenReturn(quota(10000, new BigDecimal("5")));
        when(monthlyMapper.lockUserRow(1L)).thenReturn(1L);
        UserUsageMonthlyEntity usage = new UserUsageMonthlyEntity();
        usage.setTokens(1000L);
        usage.setCost(new BigDecimal("0.10"));
        when(monthlyMapper.findForUpdate(1L, month())).thenReturn(usage);
        when(monthlyMapper.add(1L, month(), 8000, new BigDecimal("0.06"))).thenReturn(1);
        when(settingsService.current()).thenReturn(settings());
        when(globalUsageMapper.find(month())).thenReturn(new UsageTotals(0, BigDecimal.ZERO, 0));
        when(globalUsageMapper.reserve(month(), 8000, new BigDecimal("0.06"),
                1_000_000, new BigDecimal("20"))).thenReturn(1);

        ChatReservation reservation = service.reserveChat(1L, null);

        assertNotNull(reservation.user());
        assertNotNull(reservation.global());
        assertEquals(8000, reservation.user().tokens());
        assertEquals(0, reservation.user().cost().compareTo(new BigDecimal("0.06")));
        verify(monthlyMapper).add(1L, month(), 8000, new BigDecimal("0.06"));
    }

    @Test
    void reserveChatWritesLedgerAndReleaseIsIdempotent() {
        stubPersonalReservation();
        doAnswer(invocation -> {
            UsageReservationEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            return 1;
        }).when(reservationMapper).insert(any(UsageReservationEntity.class));
        when(reservationMapper.markReleased(9L)).thenReturn(1, 0);

        ChatReservation reservation = service.reserveChat(1L, null);

        assertEquals(9L, reservation.id());
        verify(reservationMapper).insert(any(UsageReservationEntity.class));

        service.releaseChat(reservation);
        service.releaseChat(reservation);

        verify(monthlyMapper, times(1))
                .release(1L, month(), 8000, new BigDecimal("0.06"));
        verify(globalUsageMapper, times(1))
                .release(month(), 8000, new BigDecimal("0.06"));
        verify(reservationMapper, times(2)).markReleased(9L);
    }

    @Test
    void reaperReleasesExpiredReservationOnce() {
        UsageReservationEntity expired = new UsageReservationEntity();
        expired.setId(12L);
        expired.setUserId(1L);
        expired.setMonth(month());
        expired.setUserTokens(8000L);
        expired.setUserCost(new BigDecimal("0.06"));
        expired.setGlobalTokens(8000L);
        expired.setGlobalCost(new BigDecimal("0.06"));
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(reservationMapper.findExpired(200)).thenReturn(List.of(expired));
        when(reservationMapper.markReleased(12L)).thenReturn(1, 0);

        service.reapExpiredReservations();

        verify(monthlyMapper).release(1L, month(), 8000, new BigDecimal("0.06"));
        verify(globalUsageMapper).release(month(), 8000, new BigDecimal("0.06"));
        verify(reservationMapper).markReleased(12L);
    }

    @Test
    void reserveChatClampsPersonalReservationToRemainingQuota() {
        when(authService.quota(1L)).thenReturn(quota(10000, new BigDecimal("5")));
        when(monthlyMapper.lockUserRow(1L)).thenReturn(1L);
        UserUsageMonthlyEntity usage = new UserUsageMonthlyEntity();
        usage.setTokens(9500L);
        usage.setCost(new BigDecimal("0.10"));
        when(monthlyMapper.findForUpdate(1L, month())).thenReturn(usage);
        when(monthlyMapper.add(1L, month(), 500, new BigDecimal("0.06"))).thenReturn(1);
        when(settingsService.current()).thenReturn(settings());
        when(globalUsageMapper.find(month())).thenReturn(new UsageTotals(0, BigDecimal.ZERO, 0));
        when(globalUsageMapper.findForUpdate(month()))
                .thenReturn(new UsageTotals(0, BigDecimal.ZERO, 0));
        when(globalUsageMapper.reserve(month(), 500, new BigDecimal("0.06"),
                1_000_000, new BigDecimal("20"))).thenReturn(1);

        ChatReservation reservation = service.reserveChat(1L, null);

        assertEquals(500, reservation.user().tokens());
        verify(monthlyMapper).add(1L, month(), 500, new BigDecimal("0.06"));
    }

    @Test
    void reserveTeamChatClampsToRemainingQuota() {
        when(teamService.quota(2L)).thenReturn(new TeamQuota(10000, new BigDecimal("5")));
        when(teamUsageMapper.lockTeamRow(2L)).thenReturn(2L);
        TeamUsageMonthlyEntity usage = new TeamUsageMonthlyEntity();
        usage.setTokens(9000L);
        usage.setCost(BigDecimal.ZERO);
        when(teamUsageMapper.findForUpdate(2L, month())).thenReturn(usage);
        when(teamUsageMapper.reserve(2L, month(), 1000, new BigDecimal("0.12"),
                10000, new BigDecimal("5"))).thenReturn(1);

        TeamReservation reservation = service.reserveTeamChat(
                1L, 2L, 12000, new BigDecimal("0.12"));

        assertEquals(1000, reservation.tokens());
        assertEquals(0, reservation.cost().compareTo(new BigDecimal("0.12")));
    }

    @Test
    void reserveChatRejectsWhenPersonalQuotaExhausted() {
        when(authService.quota(1L)).thenReturn(quota(10000, new BigDecimal("5")));
        when(monthlyMapper.lockUserRow(1L)).thenReturn(1L);
        UserUsageMonthlyEntity usage = new UserUsageMonthlyEntity();
        usage.setTokens(10000L);
        usage.setCost(BigDecimal.ZERO);
        when(monthlyMapper.findForUpdate(1L, month())).thenReturn(usage);

        assertThrows(BizException.class, () -> service.reserveChat(1L, null));
        verify(monthlyMapper, never()).add(anyLong(), any(), anyLong(), any());
    }

    private void stubPersonalReservation() {
        when(authService.quota(1L)).thenReturn(quota(10000, new BigDecimal("5")));
        when(monthlyMapper.lockUserRow(1L)).thenReturn(1L);
        UserUsageMonthlyEntity usage = new UserUsageMonthlyEntity();
        usage.setTokens(1000L);
        usage.setCost(new BigDecimal("0.10"));
        when(monthlyMapper.findForUpdate(1L, month())).thenReturn(usage);
        when(monthlyMapper.add(1L, month(), 8000, new BigDecimal("0.06"))).thenReturn(1);
        when(settingsService.current()).thenReturn(settings());
        when(globalUsageMapper.find(month())).thenReturn(new UsageTotals(0, BigDecimal.ZERO, 0));
        when(globalUsageMapper.reserve(month(), 8000, new BigDecimal("0.06"),
                1_000_000, new BigDecimal("20"))).thenReturn(1);
    }

    private UserQuotaVO quota(long tokens, BigDecimal cost) {
        return new UserQuotaVO(tokens, cost);
    }

    private SystemSettings settings() {
        return new SystemSettings(
                true, 200000, new BigDecimal("5"), 1_000_000, new BigDecimal("20"),
                0.8, 10, 0, 700, 100, 8, true, true, true,
                false, null, false, null);
    }

    private String month() {
        return java.time.YearMonth.now(java.time.ZoneOffset.UTC).toString();
    }
}
