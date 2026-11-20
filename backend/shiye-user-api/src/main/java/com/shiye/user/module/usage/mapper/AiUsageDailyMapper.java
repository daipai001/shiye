package com.shiye.user.module.usage.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface AiUsageDailyMapper {

    @Insert("""
            INSERT INTO ai_usage_daily
                (date, user_id, source, scene, model, calls, tokens, cost, currency,
                 avg_latency_ms, error_count)
            SELECT DATE(created_at), user_id, source, scene, model, COUNT(*),
                   COALESCE(SUM(total_tokens), 0),
                   COALESCE(SUM(cost), 0),
                   MAX(currency),
                   COALESCE(ROUND(AVG(latency_ms)), 0),
                   COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0)
            FROM ai_usage
            WHERE created_at >= #{from} AND created_at < #{to}
            GROUP BY DATE(created_at), user_id, source, scene, model
            ON DUPLICATE KEY UPDATE
                calls = VALUES(calls),
                tokens = VALUES(tokens),
                cost = VALUES(cost),
                currency = VALUES(currency),
                avg_latency_ms = VALUES(avg_latency_ms),
                error_count = VALUES(error_count),
                updated_at = UTC_TIMESTAMP(3)
            """)
    int aggregate(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
