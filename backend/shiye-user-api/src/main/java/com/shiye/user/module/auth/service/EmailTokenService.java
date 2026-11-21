package com.shiye.user.module.auth.service;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.common.security.Hashes;
import com.shiye.common.security.SecureCodes;
import com.shiye.user.module.auth.domain.entity.EmailTokenEntity;
import com.shiye.user.module.auth.domain.entity.UserEntity;
import com.shiye.user.module.auth.mapper.EmailTokenMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 邮箱验证/密码重置 token：只存哈希，单次使用，过期即失效。
 */
@Service
public class EmailTokenService {

    public static final String TYPE_VERIFY = "VERIFY";
    public static final String TYPE_RESET = "RESET";

    private final EmailTokenMapper tokenMapper;
    private final MailService mailService;

    @Value("${shiye.email-token.verify-expire-minutes:1440}")
    private long verifyExpireMinutes;

    @Value("${shiye.email-token.reset-expire-minutes:30}")
    private long resetExpireMinutes;

    public EmailTokenService(EmailTokenMapper tokenMapper, MailService mailService) {
        this.tokenMapper = tokenMapper;
        this.mailService = mailService;
    }

    @Transactional
    public void sendVerification(UserEntity user) {
        String token = issue(user.getId(), TYPE_VERIFY, verifyExpireMinutes);
        mailService.sendVerification(user.getEmail(), token);
    }

    @Transactional
    public void sendPasswordReset(UserEntity user) {
        String token = issue(user.getId(), TYPE_RESET, resetExpireMinutes);
        mailService.sendPasswordReset(user.getEmail(), token);
    }

    public void sendTeamInvite(String email, String teamName, String code) {
        mailService.sendTeamInvite(email, teamName, code);
    }

    @Transactional
    public long consume(String rawToken, String type) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BizException(ErrorCode.INVALID_AUTH_CODE);
        }
        EmailTokenEntity token = tokenMapper.findActive(
                Hashes.sha256Hex(rawToken.trim()), type);
        if (token == null || tokenMapper.markUsed(token.getId()) == 0) {
            throw new BizException(ErrorCode.INVALID_AUTH_CODE);
        }
        return token.getUserId();
    }

    private String issue(long userId, String type, long expireMinutes) {
        tokenMapper.deleteExpired();
        String raw = SecureCodes.token(32);
        EmailTokenEntity token = new EmailTokenEntity();
        token.setUserId(userId);
        token.setType(type);
        token.setTokenHash(Hashes.sha256Hex(raw));
        token.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC)
                .plusMinutes(Math.max(5, expireMinutes)));
        tokenMapper.insert(token);
        return raw;
    }
}
