package com.shiye.admin.module.users.service.impl;

import com.shiye.admin.module.users.domain.dto.AdminUserPasswordResetRequest;
import com.shiye.admin.module.users.domain.entity.AdminUserRowEntity;
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
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminUserServiceImpl service = new AdminUserServiceImpl(
            userMapper, passwordEncoder);

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
}
