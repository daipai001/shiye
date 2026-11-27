package com.shiye.user.module.team.service.impl;

import com.shiye.user.module.auth.service.AuthService;
import com.shiye.user.module.auth.service.EmailTokenService;
import com.shiye.user.module.team.domain.dto.TeamMemberAddRequest;
import com.shiye.user.module.team.domain.dto.TeamMemberUpdateRequest;
import com.shiye.user.module.team.domain.entity.TeamEntity;
import com.shiye.user.module.team.domain.entity.TeamMemberEntity;
import com.shiye.user.module.team.mapper.TeamInviteMapper;
import com.shiye.user.module.team.mapper.TeamMapper;
import com.shiye.user.module.team.mapper.TeamMemberMapper;
import com.shiye.user.module.team.service.TeamAuditService;
import com.shiye.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceImplTest {

    private final TeamMapper teamMapper = mock(TeamMapper.class);
    private final TeamMemberMapper memberMapper = mock(TeamMemberMapper.class);
    private final TeamInviteMapper inviteMapper = mock(TeamInviteMapper.class);
    private final AuthService authService = mock(AuthService.class);
    private final EmailTokenService emailTokenService = mock(EmailTokenService.class);
    private final TeamAuditService auditService = mock(TeamAuditService.class);
    private final TeamServiceImpl service = new TeamServiceImpl(
            teamMapper, memberMapper, inviteMapper, authService, emailTokenService, auditService);

    @Test
    void ownerCannotLeaveTeam() {
        when(memberMapper.findActive(1L, 10L)).thenReturn(member(10L, "OWNER"));

        assertThrows(BizException.class, () -> service.leave(10L, 1L));

        verify(memberMapper, never()).delete(1L, 10L);
    }

    @Test
    void adminCannotAddAnotherAdmin() {
        when(teamMapper.findById(1L)).thenReturn(team());
        when(memberMapper.findActive(1L, 10L)).thenReturn(member(10L, "ADMIN"));

        assertThrows(BizException.class, () -> service.addMember(
                10L, 1L, new TeamMemberAddRequest("other@example.com", "ADMIN")));

        verify(memberMapper, never()).insert(any());
    }

    @Test
    void adminCannotRemoveAnotherAdmin() {
        when(teamMapper.findById(1L)).thenReturn(team());
        when(memberMapper.findActive(1L, 10L)).thenReturn(member(10L, "ADMIN"));
        when(memberMapper.findActive(1L, 11L)).thenReturn(member(11L, "ADMIN"));

        assertThrows(BizException.class, () -> service.removeMember(10L, 1L, 11L));

        verify(memberMapper, never()).delete(1L, 11L);
    }

    @Test
    void removingMemberRevokesTheirUnusedInvites() {
        when(teamMapper.findById(1L)).thenReturn(team());
        when(memberMapper.findActive(1L, 10L)).thenReturn(member(10L, "OWNER"));
        when(memberMapper.findActive(1L, 11L)).thenReturn(member(11L, "MEMBER"));

        service.removeMember(10L, 1L, 11L);

        verify(memberMapper).delete(1L, 11L);
        verify(inviteMapper).deleteUnusedByInviter(1L, 11L);
        verify(auditService).record(eq(1L), eq(10L), eq("MEMBER_REMOVE"),
                eq("USER"), eq(11L), any());
    }

    @Test
    void memberLeaveIsAudited() {
        when(memberMapper.findActive(1L, 11L)).thenReturn(member(11L, "MEMBER"));

        service.leave(11L, 1L);

        verify(memberMapper).delete(1L, 11L);
        verify(auditService).record(eq(1L), eq(11L), eq("MEMBER_LEAVE"),
                eq("USER"), eq(11L), any());
    }

    @Test
    void ownerCanPromoteMemberToAdmin() {
        when(teamMapper.findById(1L)).thenReturn(team());
        when(memberMapper.findActive(1L, 10L)).thenReturn(member(10L, "OWNER"));
        when(memberMapper.findActive(1L, 11L)).thenReturn(member(11L, "MEMBER"));

        var updated = service.updateMember(
                10L, 1L, 11L, new TeamMemberUpdateRequest("ADMIN"));

        assertEquals("ADMIN", updated.role());
        verify(memberMapper).updateRole(1L, 11L, "ADMIN");
    }

    @Test
    void ownerCanTransferOwnership() {
        when(teamMapper.findById(1L)).thenReturn(team());
        when(memberMapper.findActive(1L, 10L)).thenReturn(member(10L, "OWNER"));
        when(memberMapper.findActive(1L, 11L)).thenReturn(member(11L, "MEMBER"));

        service.transferOwner(10L, 1L, 11L);

        verify(teamMapper).updateOwner(1L, 11L);
        verify(memberMapper).updateRole(1L, 11L, "OWNER");
        verify(memberMapper).updateRole(1L, 10L, "ADMIN");
        verify(auditService).record(eq(1L), eq(10L), eq("OWNER_TRANSFER"),
                eq("USER"), eq(11L), any());
    }

    @Test
    void memberCanReadTeam() {
        when(teamMapper.findById(1L)).thenReturn(team());
        when(memberMapper.findActive(1L, 10L)).thenReturn(member(10L, "MEMBER"));

        assertEquals("MEMBER", service.requireMember(10L, 1L).role());
    }

    private TeamEntity team() {
        TeamEntity team = new TeamEntity();
        team.setId(1L);
        team.setName("团队");
        team.setOwnerUserId(10L);
        team.setStatus("ACTIVE");
        return team;
    }

    private TeamMemberEntity member(long userId, String role) {
        TeamMemberEntity member = new TeamMemberEntity();
        member.setTeamId(1L);
        member.setUserId(userId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        return member;
    }
}
