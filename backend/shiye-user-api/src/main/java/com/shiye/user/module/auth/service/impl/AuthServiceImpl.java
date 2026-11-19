package com.shiye.user.module.auth.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.common.security.Hashes;
import com.shiye.common.security.JwtTokenService;
import com.shiye.common.security.SecureCodes;
import com.shiye.common.settings.SystemSettings;
import com.shiye.user.common.settings.RuntimeSystemSettingsService;
import com.shiye.user.module.auth.domain.dto.LoginRequest;
import com.shiye.user.module.auth.domain.dto.RegisterRequest;
import com.shiye.user.module.auth.domain.entity.ExtensionAuthCodeEntity;
import com.shiye.user.module.auth.domain.entity.RefreshTokenEntity;
import com.shiye.user.module.auth.domain.entity.UserEntity;
import com.shiye.user.module.auth.domain.vo.ExtensionCodeResponse;
import com.shiye.user.module.auth.domain.vo.TokenResponse;
import com.shiye.user.module.auth.domain.vo.UserQuotaVO;
import com.shiye.user.module.auth.domain.vo.UserSummary;
import com.shiye.user.module.auth.mapper.ExtensionAuthCodeMapper;
import com.shiye.user.module.auth.mapper.RefreshTokenMapper;
import com.shiye.user.module.auth.mapper.UserMapper;
import com.shiye.user.module.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class AuthServiceImpl implements AuthService {

    private static final String SUBJECT_USER = "USER";
    private static final int EXTENSION_CODE_MINUTES = 5;

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final ExtensionAuthCodeMapper extensionAuthCodeMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final RuntimeSystemSettingsService systemSettingsService;

    @Value("${shiye.jwt.refresh-expire-seconds:2592000}")
    private long refreshExpireSeconds;

    public AuthServiceImpl(UserMapper userMapper,
                           RefreshTokenMapper refreshTokenMapper,
                           ExtensionAuthCodeMapper extensionAuthCodeMapper,
                           PasswordEncoder passwordEncoder,
                           JwtTokenService jwtTokenService,
                           RuntimeSystemSettingsService systemSettingsService) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.extensionAuthCodeMapper = extensionAuthCodeMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.systemSettingsService = systemSettingsService;
    }

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request, String userAgent, String ip) {
        SystemSettings settings = systemSettingsService.current();
        if (!settings.registrationEnabled()) {
            throw new BizException(ErrorCode.FORBIDDEN, "注册已关闭");
        }
        String email = normalizeEmail(request.email());
        if (userMapper.findByEmail(email) != null) {
            throw new BizException(ErrorCode.CONFLICT, "邮箱已注册");
        }

        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(resolveDisplayName(request.displayName(), email));
        user.setStatus("ACTIVE");
        user.setMonthlyTokenQuota(settings.defaultMonthlyTokens());
        user.setMonthlyCostQuota(settings.defaultMonthlyCost());
        user.setQuotaSource("NEW_USER");
        userMapper.insert(user);

        UserEntity saved = userMapper.findById(user.getId());
        if (saved == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR);
        }
        return issueTokens(saved, userAgent, ip);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request, String userAgent, String ip) {
        UserEntity user = userMapper.findByEmail(normalizeEmail(request.email()));
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "邮箱或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        userMapper.updateLastLogin(user.getId());
        return issueTokens(user, userAgent, ip);
    }

    @Override
    @Transactional
    public TokenResponse refresh(String refreshToken, String userAgent, String ip) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ErrorCode.REFRESH_REVOKED);
        }
        RefreshTokenEntity existing = refreshTokenMapper.findActiveByHash(Hashes.sha256Hex(refreshToken));
        if (existing == null || !SUBJECT_USER.equals(existing.getSubjectType())) {
            throw new BizException(ErrorCode.REFRESH_REVOKED);
        }
        UserEntity user = userMapper.findById(existing.getSubjectId());
        if (user == null) {
            throw new BizException(ErrorCode.REFRESH_REVOKED);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        refreshTokenMapper.revoke(existing.getId(), null);
        return issueTokens(user, userAgent, ip);
    }

    @Override
    @Transactional
    public void logout(long userId, String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            RefreshTokenEntity existing = refreshTokenMapper.findActiveByHash(Hashes.sha256Hex(refreshToken));
            if (existing != null && SUBJECT_USER.equals(existing.getSubjectType())
                    && existing.getSubjectId() == userId) {
                refreshTokenMapper.revoke(existing.getId(), null);
            }
            return;
        }
        refreshTokenMapper.revokeAllForSubject(SUBJECT_USER, userId);
    }

    @Override
    @Transactional
    public ExtensionCodeResponse issueExtensionCode(long userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }

        String code = "SHIYE-" + SecureCodes.code(8);
        ExtensionAuthCodeEntity entity = new ExtensionAuthCodeEntity();
        entity.setUserId(userId);
        entity.setCodeHash(Hashes.sha256Hex(code));
        entity.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(EXTENSION_CODE_MINUTES));
        extensionAuthCodeMapper.insert(entity);
        return new ExtensionCodeResponse(code, EXTENSION_CODE_MINUTES * 60L);
    }

    @Override
    @Transactional
    public TokenResponse exchangeExtensionCode(String code, String clientInfo, String userAgent, String ip) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        ExtensionAuthCodeEntity authCode = extensionAuthCodeMapper.findActiveByHash(Hashes.sha256Hex(normalized));
        if (authCode == null) {
            throw new BizException(ErrorCode.INVALID_AUTH_CODE);
        }
        if (extensionAuthCodeMapper.markUsed(authCode.getId()) == 0) {
            throw new BizException(ErrorCode.INVALID_AUTH_CODE);
        }
        UserEntity user = userMapper.findById(authCode.getUserId());
        if (user == null) {
            throw new BizException(ErrorCode.INVALID_AUTH_CODE);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        String agent = clientInfo == null || clientInfo.isBlank() ? userAgent : clientInfo + " / " + userAgent;
        return issueTokens(user, agent, ip);
    }

    @Override
    public UserSummary me(long userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        return UserSummary.from(user);
    }

    @Override
    public UserQuotaVO quota(long userId) {
        UserEntity user = userMapper.findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException(ErrorCode.USER_DISABLED);
        }
        return new UserQuotaVO(
                user.getMonthlyTokenQuota() == null ? 0 : user.getMonthlyTokenQuota(),
                user.getMonthlyCostQuota() == null ? BigDecimal.ZERO : user.getMonthlyCostQuota()
        );
    }

    private TokenResponse issueTokens(UserEntity user, String userAgent, String ip) {
        String accessToken = jwtTokenService.issue(user.getId(), "USER");
        String rawRefreshToken = SecureCodes.token(32);

        RefreshTokenEntity refresh = new RefreshTokenEntity();
        refresh.setSubjectType(SUBJECT_USER);
        refresh.setSubjectId(user.getId());
        refresh.setTokenHash(Hashes.sha256Hex(rawRefreshToken));
        refresh.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(refreshExpireSeconds));
        refresh.setUserAgent(truncate(userAgent, 512));
        refresh.setIp(truncate(ip, 45));
        refreshTokenMapper.insert(refresh);

        return new TokenResponse(
                accessToken,
                rawRefreshToken,
                jwtTokenService.accessTtlSeconds(),
                "Bearer",
                UserSummary.from(user)
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveDisplayName(String displayName, String email) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
