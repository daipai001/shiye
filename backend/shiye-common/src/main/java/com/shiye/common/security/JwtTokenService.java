package com.shiye.common.security;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * HMAC JWT 签发/校验。用户端和管理端各用一个实例，secret/audience 必须不同。
 */
public class JwtTokenService {

    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final Duration accessTtl;

    public JwtTokenService(String secret, String issuer, String audience, long accessTtlSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.accessTtl = Duration.ofSeconds(Math.max(60, accessTtlSeconds));
    }

    public String issue(long subjectId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(subjectId))
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(key)
                .compact();
    }

    public JwtPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new JwtPrincipal(Long.parseLong(claims.getSubject()), claims.get("role", String.class));
        } catch (ExpiredJwtException ex) {
            throw new BizException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
    }

    public long accessTtlSeconds() {
        return accessTtl.toSeconds();
    }

    public record JwtPrincipal(long subjectId, String role) {
    }
}
