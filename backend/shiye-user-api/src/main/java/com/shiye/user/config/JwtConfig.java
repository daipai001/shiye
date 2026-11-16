package com.shiye.user.config;

import com.shiye.common.security.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

    @Bean
    public JwtTokenService userJwtTokenService(
            @Value("${shiye.jwt.user-secret}") String secret,
            @Value("${shiye.jwt.user-expire-seconds:900}") long accessTtlSeconds) {
        return new JwtTokenService(secret, "shiye-user-api", "user", accessTtlSeconds);
    }
}
