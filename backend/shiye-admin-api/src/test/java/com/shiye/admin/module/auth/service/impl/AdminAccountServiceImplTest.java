package com.shiye.admin.module.auth.service.impl;

import com.shiye.admin.module.auth.domain.dto.AdminAccountCreateRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountStatusRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountUpdateRequest;
import com.shiye.admin.module.auth.domain.entity.AdminUserEntity;
import com.shiye.admin.module.auth.mapper.AdminRefreshTokenMapper;
import com.shiye.admin.module.auth.mapper.AdminUserMapper;
import com.shiye.common.exception.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAccountServiceImplTest {

    private final AdminUserMapper adminUserMapper = mock(AdminUserMapper.class);
    private final AdminRefreshTokenMapper refreshTokenMapper = mock(AdminRefreshTokenMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final AdminAccountServiceImpl service = new AdminAccountServiceImpl(
            adminUserMapper, refreshTokenMapper, passwordEncoder);

    @Test
    void createRejectsDuplicateEmail() {
        when(adminUserMapper.countByEmail("ops@example.com")).thenReturn(1);

        assertThrows(BizException.class, () -> service.create(
                new AdminAccountCreateRequest("ops@example.com", "运营", "OPERATOR", "Passw0rd!")));

        verify(adminUserMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateRejectsDemotingLastActiveSuperAdmin() {
        AdminUserEntity superAdmin = admin(1L, "SUPER_ADMIN", "ACTIVE");
        when(adminUserMapper.findById(1L)).thenReturn(superAdmin);
        when(adminUserMapper.countActiveSuperAdminsExcluding(1L)).thenReturn(0);

        assertThrows(BizException.class, () -> service.update(
                9L, 1L, new AdminAccountUpdateRequest("超级管理员", "VIEWER")));

        verify(adminUserMapper, never()).updateProfile(anyLong(), anyString(), anyString());
    }

    @Test
    void statusRejectsDisablingSelf() {
        AdminUserEntity admin = admin(9L, "SUPER_ADMIN", "ACTIVE");
        when(adminUserMapper.findById(9L)).thenReturn(admin);

        assertThrows(BizException.class, () -> service.updateStatus(
                9L, 9L, new AdminAccountStatusRequest("DISABLED")));
    }

    @Test
    void disablingAnotherAdminRevokesTokens() {
        AdminUserEntity operator = admin(2L, "OPERATOR", "ACTIVE");
        when(adminUserMapper.findById(2L)).thenReturn(operator);

        service.updateStatus(9L, 2L, new AdminAccountStatusRequest("DISABLED"));

        verify(adminUserMapper).updateStatus(2L, "DISABLED");
        verify(refreshTokenMapper).revokeAllForSubject("ADMIN", 2L);
    }

    private AdminUserEntity admin(long id, String role, String status) {
        AdminUserEntity admin = new AdminUserEntity();
        admin.setId(id);
        admin.setEmail(id + "@example.com");
        admin.setName("管理员" + id);
        admin.setRole(role);
        admin.setStatus(status);
        return admin;
    }
}
