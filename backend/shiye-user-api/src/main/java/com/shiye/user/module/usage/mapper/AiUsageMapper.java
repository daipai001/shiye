package com.shiye.user.module.usage.mapper;

import com.shiye.user.module.usage.domain.entity.AiUsageEntity;
import com.shiye.user.module.usage.domain.entity.UsageBreakdownRow;
import com.shiye.user.module.usage.domain.entity.UsageDailyRow;
import com.shiye.user.module.usage.domain.entity.UsageRangeTotalsRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AiUsageMapper {

    @Insert("""
            INSERT INTO ai_usage (request_id, user_id, source, scene, model, prompt_tokens,
                                  completion_tokens, total_tokens, cached_tokens, input_price,
                                  output_price, cost, currency, latency_ms, first_token_ms,
                                  status, error_code, estimated, conversation_id, article_id,
                                  client_version)
            VALUES (#{requestId}, #{userId}, #{source}, #{scene}, #{model}, #{promptTokens},
                    #{completionTokens}, #{totalTokens}, #{cachedTokens}, #{inputPrice},
                    #{outputPrice}, #{cost}, #{currency}, #{latencyMs}, #{firstTokenMs},
                    #{status}, #{errorCode}, #{estimated}, #{conversationId}, #{articleId},
                    #{clientVersion})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AiUsageEntity entity);

    @Select("""
            <script>
            SELECT id, request_id, user_id, source, scene, model, prompt_tokens, completion_tokens,
                   total_tokens, cached_tokens, input_price, output_price, cost, currency,
                   latency_ms, first_token_ms, status, error_code, estimated, conversation_id,
                   article_id, client_version, created_at
            FROM ai_usage
            WHERE user_id = #{userId}
            <if test="source != null"> AND source = #{source}</if>
            <if test="scene != null"> AND scene = #{scene}</if>
            <if test="model != null"> AND model = #{model}</if>
            <if test="status != null"> AND status = #{status}</if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AiUsageEntity> findPage(@Param("userId") long userId,
                                 @Param("source") String source,
                                 @Param("scene") String scene,
                                 @Param("model") String model,
                                 @Param("status") String status,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM ai_usage
            WHERE user_id = #{userId}
            <if test="source != null"> AND source = #{source}</if>
            <if test="scene != null"> AND scene = #{scene}</if>
            <if test="model != null"> AND model = #{model}</if>
            <if test="status != null"> AND status = #{status}</if>
            </script>
            """)
    long count(@Param("userId") long userId,
               @Param("source") String source,
               @Param("scene") String scene,
               @Param("model") String model,
               @Param("status") String status);

    @Select("""
            SELECT COUNT(*) AS calls,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COALESCE(SUM(cost), 0) AS cost,
                   COALESCE(SUM(status = 'FAILED'), 0) AS errors
            FROM ai_usage
            WHERE user_id = #{userId} AND created_at >= #{from}
            """)
    UsageRangeTotalsRow summary(@Param("userId") long userId,
                                @Param("from") LocalDateTime from);

    @Select("""
            SELECT scene AS label,
                   COUNT(*) AS calls,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM ai_usage
            WHERE user_id = #{userId} AND created_at >= #{from}
            GROUP BY scene
            ORDER BY tokens DESC, label ASC
            """)
    List<UsageBreakdownRow> summaryByScene(@Param("userId") long userId,
                                           @Param("from") LocalDateTime from);

    @Select("""
            SELECT source AS label,
                   COUNT(*) AS calls,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM ai_usage
            WHERE user_id = #{userId} AND created_at >= #{from}
            GROUP BY source
            ORDER BY tokens DESC, label ASC
            """)
    List<UsageBreakdownRow> summaryBySource(@Param("userId") long userId,
                                            @Param("from") LocalDateTime from);

    @Select("""
            SELECT DATE(created_at) AS date,
                   COUNT(*) AS calls,
                   COALESCE(SUM(total_tokens), 0) AS tokens,
                   COALESCE(SUM(cost), 0) AS cost,
                   COALESCE(SUM(status = 'FAILED'), 0) AS errors
            FROM ai_usage
            WHERE user_id = #{userId} AND created_at >= #{from}
            GROUP BY DATE(created_at)
            ORDER BY date
            """)
    List<UsageDailyRow> summaryDaily(@Param("userId") long userId,
                                     @Param("from") LocalDateTime from);
}
