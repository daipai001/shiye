package com.shiye.admin.module.auth.service.impl;

import com.shiye.admin.module.auth.domain.dto.AdminLoginRequest;
import com.shiye.admin.module.auth.domain.entity.AdminRefreshTokenEntity;
import com.shiye.admin.module.auth.domain.entity.AdminUserEntity;
import com.shiye.admin.module.auth.domain.vo.AdminSummary;
import com.shiye.admin.module.auth.domain.vo.AdminTokenResponse;
import com.shiye.admin.module.auth.mapper.AdminRefreshTokenMapper;
import com.shiye.admin.module.auth.mapper.AdminUserMapper;
import com.shiye.admin.module.auth.service.AdminAuthService;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.common.security.Hashes;
import com.shiye.common.security.JwtTokenService;
import com.shiye.common.security.SecureCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String SUBJECT_ADMIN = "ADMIN";

    private final AdminUserMapper adminUserMapper;
    private final AdminRefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Value("${shiye.jwt.refresh-expire-seconds:2592000}")
    private long refreshExpireSeconds;

    public AdminAuthServiceImpl(AdminUserMapper adminUserMapper,
                                AdminRefreshTokenMapper refreshTokenMapper,
                                PasswordEncoder passwordEncoder,
                                JwtTokenService jwtTokenService) {
        this.adminUserMapper = adminUserMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    @Transactional
    public AdminTokenResponse login(AdminLoginRequest request, String userAgent, String ip) {
        AdminUserEntity admin = adminUserMapper.findByEmail(normalizeEmail(request.email()));
        if (admin == null || !passwordEncoder.matches(request.password(), admin.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "管理员邮箱或密码错误");
        }
        if (!"ACTIVE".equals(admin.getStatus())) {
            throw new BizException(ErrorCode.ADMIN_DISABLED);
        }
        adminUserMapper.updateLastLogin(admin.getId());
        return issueTokens(admin, userAgent, ip);
    }

    @Override
    @Transactional
    public AdminTokenResponse refresh(String refreshToken, String userAgent, String ip) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ErrorCode.REFRESH_REVOKED);
        }
        AdminRefreshTokenEntity existing = refreshTokenMapper.findActiveByHash(Hashes.sha256Hex(refreshToken));
        if (existing == null || !SUBJECT_ADMIN.equals(existing.getSubjectType())) {
            throw new BizException(ErrorCode.REFRESH_REVOKED);
        }
        AdminUserEntity admin = adminUserMapper.findById(existing.getSubjectId());
        if (admin == null || !"ACTIVE".equals(admin.getStatus())) {
            throw new BizException(ErrorCode.ADMIN_DISABLED);
        }
        if (refreshTokenMapper.revoke(existing.getId(), null) == 0) {
            throw new BizException(ErrorCode.REFRESH_REVOKED);
        }
        return issueTokens(admin, userAgent, ip);
    }

    @Override
    @Transactional
    public void logout(long adminId, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            AdminRefreshTokenEntity existing = refreshTokenMapper.findActiveByHash(Hashes.sha256Hex(refreshToken));
            if (existing != null && SUBJECT_ADMIN.equals(existing.getSubjectType())
                    && existing.getSubjectId() == adminId) {
                refreshTokenMapper.revoke(existing.getId(), null);
            }
            return;
        }
        refreshTokenMapper.revokeAllForSubject(SUBJECT_ADMIN, adminId);
    }

    @Override
    public AdminSummary me(long adminId) {
        return authenticate(adminId);
    }

    @Override
    public AdminSummary authenticate(long adminId) {
        AdminUserEntity admin = adminUserMapper.findById(adminId);
        if (admin == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!"ACTIVE".equals(admin.getStatus())) {
            throw new BizException(ErrorCode.ADMIN_DISABLED);
        }
        return AdminSummary.from(admin);
    }

    private AdminTokenResponse issueTokens(AdminUserEntity admin, String userAgent, String ip) {
        String accessToken = jwtTokenService.issue(admin.getId(), admin.getRole());
        String rawRefreshToken = SecureCodes.token(32);

        AdminRefreshTokenEntity refresh = new AdminRefreshTokenEntity();
        refresh.setSubjectType(SUBJECT_ADMIN);
        refresh.setSubjectId(admin.getId());
        refresh.setTokenHash(Hashes.sha256Hex(rawRefreshToken));
        refresh.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(refreshExpireSeconds));
        refresh.setUserAgent(truncate(userAgent, 512));
        refresh.setIp(truncate(ip, 45));
        refreshTokenMapper.insert(refresh);

        return new AdminTokenResponse(
                accessToken,
                rawRefreshToken,
                jwtTokenService.accessTtlSeconds(),
                "Bearer",
                AdminSummary.from(admin)
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
