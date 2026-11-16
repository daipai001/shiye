package com.shiye.admin.config;

import com.shiye.common.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtTokenService adminJwtTokenService(
            @Value("${shiye.jwt.admin-secret}") String secret,
            @Value("${shiye.jwt.admin-expire-seconds:900}") long accessTtlSeconds) {
        return new JwtTokenService(secret, "shiye-admin-api", "admin", accessTtlSeconds);
    }
}
