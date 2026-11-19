package com.shiye.admin.module.overview.mapper;

import com.shiye.admin.module.overview.domain.entity.RecentFailureRow;
import com.shiye.admin.module.overview.domain.entity.SourceSplitRow;
import com.shiye.admin.module.overview.domain.entity.UsageSummaryRow;
import com.shiye.admin.module.overview.domain.entity.UsageTrendRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OverviewMapper {

    @Select("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL")
    long countUsers();

    @Select("SELECT COUNT(*) FROM articles WHERE deleted_at IS NULL")
    long countArticles();

    @Select("SELECT COUNT(*) FROM article_chunks")
    long countChunks();

    @Select("""
            SELECT COUNT(*) AS calls,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COALESCE(SUM(cost), 0) AS cost,
                   COALESCE(SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END), 0) AS errors
            FROM ai_usage
            WHERE created_at >= #{from}
            """)
    UsageSummaryRow usageSince(@Param("from") LocalDateTime from);

    @Select("""
            SELECT MAX(latency_ms)
            FROM (
                SELECT latency_ms, NTILE(20) OVER (ORDER BY latency_ms) AS bucket
                FROM ai_usage
                WHERE created_at >= #{from} AND latency_ms IS NOT NULL
            ) t
            WHERE bucket = 20
            """)
    Integer p95Latency(@Param("from") LocalDateTime from);

    @Select("""
            SELECT MAX(latency_ms)
            FROM ai_usage
            WHERE created_at >= #{from} AND latency_ms IS NOT NULL
            """)
    Integer maxLatencySince(@Param("from") LocalDateTime from);

    @Select("""
            SELECT DATE(created_at) AS day,
                   COUNT(*) AS calls,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM ai_usage
            WHERE created_at >= #{from}
            GROUP BY DATE(created_at)
            ORDER BY day
            """)
    List<UsageTrendRow> trendsSince(@Param("from") LocalDateTime from);

    @Select("""
            SELECT source,
                   COUNT(*) AS calls,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM ai_usage
            WHERE created_at >= #{from}
            GROUP BY source
            ORDER BY tokens DESC
            """)
    List<SourceSplitRow> sourceSplitSince(@Param("from") LocalDateTime from);

    @Select("""
            SELECT *
            FROM (
                SELECT 'IMPORT' AS type, CAST(i.id AS CHAR) AS resource_id,
                       u.email AS user_name, i.error_code, i.error_message,
                       i.updated_at AS created_at
                FROM bookmark_items i
                LEFT JOIN users u ON u.id = i.user_id
                WHERE i.status = 'FAILED'
                UNION ALL
                SELECT 'INGEST' AS type, CAST(a.id AS CHAR) AS resource_id,
                       u.email AS user_name, 'INGEST_FAILED' AS error_code,
                       NULL AS error_message, a.updated_at AS created_at
                FROM articles a
                LEFT JOIN users u ON u.id = a.user_id
                WHERE a.status = 'FAILED' AND a.deleted_at IS NULL
                UNION ALL
                SELECT 'AI' AS type, au.request_id AS resource_id,
                       u.email AS user_name, au.error_code,
                       NULL AS error_message, au.created_at AS created_at
                FROM ai_usage au
                LEFT JOIN users u ON u.id = au.user_id
                WHERE au.status = 'FAILED'
            ) f
            ORDER BY created_at DESC
            LIMIT 10
            """)
    List<RecentFailureRow> recentFailures();
}
