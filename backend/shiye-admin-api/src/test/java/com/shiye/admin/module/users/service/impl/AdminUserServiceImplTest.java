package com.shiye.admin.module.users.service.impl;

import com.shiye.admin.module.users.domain.dto.AdminUserPasswordResetRequest;
import com.shiye.admin.module.users.domain.dto.AdminUserStatusRequest;
import com.shiye.admin.module.users.domain.entity.AdminUserRowEntity;
import com.shiye.admin.module.teams.mapper.AdminTeamMapper;
import com.shiye.admin.module.users.mapper.AdminUserQueryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceImplTest {

    private final AdminUserQueryMapper userMapper = mock(AdminUserQueryMapper.class);
    private final AdminTeamMapper teamMapper = mock(AdminTeamMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminUserServiceImpl service = new AdminUserServiceImpl(
            userMapper, teamMapper, passwordEncoder);

    @Test
    void resetPasswordUpdatesHashAndRevokesTokens() {
        AdminUserRowEntity user = new AdminUserRowEntity();
        user.setId(5L);
        user.setEmail("user@example.com");
        user.setStatus("ACTIVE");
        when(userMapper.findById(anyLong(), anyString())).thenReturn(user);
        when(passwordEncoder.encode("NewPass456")).thenReturn("encoded");

        service.resetPassword(5L, new AdminUserPasswordResetRequest("NewPass456"));

        verify(userMapper).updatePassword(5L, "encoded");
        verify(userMapper).revokeUserTokens(5L);
    }

    @Test
    void disableRejectsUserWhoOwnsActiveTeam() {
        AdminUserRowEntity user = new AdminUserRowEntity();
        user.setId(5L);
        user.setEmail("owner@example.com");
        user.setStatus("ACTIVE");
        when(userMapper.findById(anyLong(), anyString())).thenReturn(user);
        when(teamMapper.countActiveOwnedBy(5L)).thenReturn(1L);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.shiye.common.exception.BizException.class,
                () -> service.updateStatus(5L, new AdminUserStatusRequest("DISABLED")));

        verify(userMapper, org.mockito.Mockito.never()).updateStatus(5L, "DISABLED");
    }
}
