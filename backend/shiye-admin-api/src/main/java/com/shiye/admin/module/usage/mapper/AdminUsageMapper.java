package com.shiye.admin.module.usage.mapper;

import com.shiye.admin.module.usage.domain.entity.AdminUsageGroupRow;
import com.shiye.admin.module.usage.domain.entity.AdminUsageRowEntity;
import com.shiye.admin.module.usage.domain.entity.AdminUsageSummaryRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminUsageMapper {

    String FILTERS = """
            WHERE 1 = 1
            <if test="userId != null"> AND au.user_id = #{userId}</if>
            <if test="source != null"> AND au.source = #{source}</if>
            <if test="scene != null"> AND au.scene = #{scene}</if>
            <if test="model != null"> AND au.model = #{model}</if>
            <if test="status != null"> AND au.status = #{status}</if>
            <if test="from != null"> AND au.created_at &gt;= #{from}</if>
            <if test="to != null"> AND au.created_at &lt; #{to}</if>
            """;

    @Select("""
            <script>
            SELECT au.id, au.request_id, au.user_id, u.email AS user_email,
                   au.source, au.scene, au.model, au.prompt_tokens, au.completion_tokens,
                   au.total_tokens, au.cost, au.currency, au.latency_ms, au.first_token_ms,
                   au.status, au.error_code, au.created_at
            FROM ai_usage au
            LEFT JOIN users u ON u.id = au.user_id
            """ + FILTERS + """
            ORDER BY au.created_at DESC, au.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminUsageRowEntity> findPage(@Param("userId") Long userId,
                                       @Param("source") String source,
                                       @Param("scene") String scene,
                                       @Param("model") String model,
                                       @Param("status") String status,
                                       @Param("from") LocalDateTime from,
                                       @Param("to") LocalDateTime to,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ai_usage au
            """ + FILTERS + """
            </script>
            """)
    long count(@Param("userId") Long userId,
               @Param("source") String source,
               @Param("scene") String scene,
               @Param("model") String model,
               @Param("status") String status,
               @Param("from") LocalDateTime from,
               @Param("to") LocalDateTime to);

    @Select("""
            <script>
            SELECT COUNT(*) AS calls,
                   COALESCE(SUM(au.total_tokens), 0) AS tokens,
                   COALESCE(SUM(au.cost), 0) AS cost,
                   COALESCE(SUM(CASE WHEN au.status = 'FAILED' THEN 1 ELSE 0 END), 0) AS errors
            FROM ai_usage au
            """ + FILTERS + """
            </script>
            """)
    AdminUsageSummaryRow summary(@Param("userId") Long userId,
                                 @Param("source") String source,
                                 @Param("scene") String scene,
                                 @Param("model") String model,
                                 @Param("status") String status,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to);

    @Select("""
            <script>
            SELECT au.source AS `key`,
                   COUNT(*) AS calls,
                   COALESCE(SUM(au.total_tokens), 0) AS tokens,
                   COALESCE(SUM(au.cost), 0) AS cost
            FROM ai_usage au
            """ + FILTERS + """
            GROUP BY au.source
            ORDER BY tokens DESC
            </script>
            """)
    List<AdminUsageGroupRow> sourceSplit(@Param("userId") Long userId,
                                         @Param("source") String source,
                                         @Param("scene") String scene,
                                         @Param("model") String model,
                                         @Param("status") String status,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    @Select("""
            <script>
            SELECT au.model AS `key`,
                   COUNT(*) AS calls,
                   COALESCE(SUM(au.total_tokens), 0) AS tokens,
                   COALESCE(SUM(au.cost), 0) AS cost
            FROM ai_usage au
            """ + FILTERS + """
            GROUP BY au.model
            ORDER BY tokens DESC
            </script>
            """)
    List<AdminUsageGroupRow> modelSplit(@Param("userId") Long userId,
                                        @Param("source") String source,
                                        @Param("scene") String scene,
                                        @Param("model") String model,
                                        @Param("status") String status,
                                        @Param("from") LocalDateTime from,
                                        @Param("to") LocalDateTime to);
}
