package com.shiye.admin.module.auth.service.impl;

import com.shiye.admin.common.security.AdminPermissions;
import com.shiye.admin.module.auth.domain.dto.AdminAccountCreateRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountPasswordResetRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountStatusRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountUpdateRequest;
import com.shiye.admin.module.auth.domain.entity.AdminUserEntity;
import com.shiye.admin.module.auth.domain.vo.AdminAccountVO;
import com.shiye.admin.module.auth.mapper.AdminRefreshTokenMapper;
import com.shiye.admin.module.auth.mapper.AdminUserMapper;
import com.shiye.admin.module.auth.service.AdminAccountService;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AdminAccountServiceImpl implements AdminAccountService {

    private static final String SUBJECT_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_DISABLED = "DISABLED";

    private final AdminUserMapper adminUserMapper;
    private final AdminRefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountServiceImpl(AdminUserMapper adminUserMapper,
                                   AdminRefreshTokenMapper refreshTokenMapper,
                                   PasswordEncoder passwordEncoder) {
        this.adminUserMapper = adminUserMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<AdminAccountVO> list() {
        return adminUserMapper.findAll().stream()
                .map(AdminAccountVO::from)
                .toList();
    }

    @Override
    @Transactional
    public AdminAccountVO create(AdminAccountCreateRequest request) {
        String email = normalizeEmail(request.email());
        if (adminUserMapper.countByEmail(email) > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该管理员邮箱已存在");
        }
        AdminUserEntity admin = new AdminUserEntity();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.setName(request.name().trim());
        admin.setRole(normalizeRole(request.role()));
        admin.setStatus(STATUS_ACTIVE);
        try {
            adminUserMapper.insert(admin);
        } catch (DuplicateKeyException ex) {
            throw new BizException(ErrorCode.CONFLICT, "该管理员邮箱已存在");
        }
        return AdminAccountVO.from(findAdmin(admin.getId()));
    }

    @Override
    @Transactional
    public AdminAccountVO update(long actorId, long id, AdminAccountUpdateRequest request) {
        AdminUserEntity existing = findAdmin(id);
        String role = normalizeRole(request.role());
        if (id == actorId && !role.equals(existing.getRole())) {
            throw new BizException(ErrorCode.CONFLICT, "不能修改自己的角色");
        }
        if (isLastActiveSuperAdmin(existing) && !AdminPermissions.ROLE_SUPER_ADMIN.equals(role)) {
            throw new BizException(ErrorCode.CONFLICT, "至少保留一名启用中的超级管理员");
        }
        adminUserMapper.updateProfile(id, request.name().trim(), role);
        return AdminAccountVO.from(findAdmin(id));
    }

    @Override
    @Transactional
    public AdminAccountVO updateStatus(long actorId, long id, AdminAccountStatusRequest request) {
        AdminUserEntity existing = findAdmin(id);
        String status = normalizeStatus(request.status());
        if (id == actorId && STATUS_DISABLED.equals(status)) {
            throw new BizException(ErrorCode.CONFLICT, "不能禁用自己的账号");
        }
        if (isLastActiveSuperAdmin(existing) && STATUS_DISABLED.equals(status)) {
            throw new BizException(ErrorCode.CONFLICT, "至少保留一名启用中的超级管理员");
        }
        adminUserMapper.updateStatus(id, status);
        if (STATUS_DISABLED.equals(status)) {
            refreshTokenMapper.revokeAllForSubject(SUBJECT_ADMIN, id);
        }
        return AdminAccountVO.from(findAdmin(id));
    }

    @Override
    @Transactional
    public void resetPassword(long id, AdminAccountPasswordResetRequest request) {
        findAdmin(id);
        adminUserMapper.updatePassword(id, passwordEncoder.encode(request.newPassword()));
        refreshTokenMapper.revokeAllForSubject(SUBJECT_ADMIN, id);
    }

    private boolean isLastActiveSuperAdmin(AdminUserEntity admin) {
        return AdminPermissions.ROLE_SUPER_ADMIN.equals(admin.getRole())
                && STATUS_ACTIVE.equals(admin.getStatus())
                && adminUserMapper.countActiveSuperAdminsExcluding(admin.getId()) == 0;
    }

    private AdminUserEntity findAdmin(long id) {
        AdminUserEntity admin = adminUserMapper.findById(id);
        if (admin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "管理员不存在");
        }
        return admin;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRole(String role) {
        String value = AdminPermissions.normalize(role);
        if (!AdminPermissions.isKnownRole(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    "role 仅支持 SUPER_ADMIN / OPERATOR / VIEWER");
        }
        return value;
    }

    private String normalizeStatus(String status) {
        String value = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(value) && !STATUS_DISABLED.equals(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR,
                    "status 仅支持 ACTIVE / DISABLED");
        }
        return value;
    }
}
