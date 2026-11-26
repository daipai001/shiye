package com.shiye.user.module.article.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleIngestLockServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final ArticleIngestLockService service = new ArticleIngestLockService(redisTemplate, 30);

    @Test
    void returnsTokenWhenLockAcquired() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        String token = service.tryAcquire(42L);

        assertNotNull(token);
        assertEquals(false, token.isEmpty());
    }

    @Test
    void returnsNullWhenLockIsHeld() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertNull(service.tryAcquire(42L));
    }

    @Test
    void failsOpenWhenRedisUnavailable() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        assertEquals("", service.tryAcquire(42L));
    }
}
