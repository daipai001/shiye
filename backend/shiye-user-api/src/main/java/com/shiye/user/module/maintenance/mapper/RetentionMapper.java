package com.shiye.user.module.maintenance.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface RetentionMapper {

    @Delete("""
            DELETE FROM refresh_tokens
            WHERE (expires_at < #{cutoff} OR (revoked_at IS NOT NULL AND revoked_at < #{cutoff}))
            LIMIT #{limit}
            """)
    int deleteRefreshTokens(@Param("cutoff") LocalDateTime cutoff,
                            @Param("limit") int limit);

    @Delete("""
            DELETE FROM extension_auth_codes
            WHERE (expires_at < #{cutoff} OR (used_at IS NOT NULL AND used_at < #{cutoff}))
            LIMIT #{limit}
            """)
    int deleteExtensionCodes(@Param("cutoff") LocalDateTime cutoff,
                             @Param("limit") int limit);

    @Delete("""
            DELETE FROM idempotency_keys
            WHERE expires_at < #{cutoff}
            LIMIT #{limit}
            """)
    int deleteIdempotencyKeys(@Param("cutoff") LocalDateTime cutoff,
                              @Param("limit") int limit);

    @Delete("""
            DELETE FROM ingest_tasks
            WHERE finished_at IS NOT NULL AND finished_at < #{cutoff}
              AND status IN ('SUCCESS', 'FAILED', 'DEAD')
            LIMIT #{limit}
            """)
    int deleteIngestTasks(@Param("cutoff") LocalDateTime cutoff,
                          @Param("limit") int limit);
}
