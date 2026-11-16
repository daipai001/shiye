package com.shiye.user.module.usage.mapper;

import com.shiye.user.module.usage.domain.entity.AiUsageEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
