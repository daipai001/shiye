package com.shiye.user.module.chat.mapper;

import com.shiye.user.module.chat.domain.entity.MessageEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MessageMapper {

    @Insert("""
            INSERT INTO messages (conversation_id, user_id, role, content, model,
                                  prompt_tokens, completion_tokens, request_id, status,
                                  error_code, first_token_ms, latency_ms)
            VALUES (#{conversationId}, #{userId}, #{role}, #{content}, #{model},
                    #{promptTokens}, #{completionTokens}, #{requestId}, #{status},
                    #{errorCode}, #{firstTokenMs}, #{latencyMs})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MessageEntity entity);

    @Select("""
            SELECT id, conversation_id, user_id, role, content, model, prompt_tokens,
                   completion_tokens, request_id, status, error_code, first_token_ms,
                   latency_ms, created_at
            FROM messages
            WHERE id = #{id} AND user_id = #{userId}
            """)
    MessageEntity findByIdAndUser(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, conversation_id, user_id, role, content, model, prompt_tokens,
                   completion_tokens, request_id, status, error_code, first_token_ms,
                   latency_ms, created_at
            FROM messages
            WHERE conversation_id = #{conversationId} AND user_id = #{userId}
            <choose>
              <when test="asc"> ORDER BY id ASC</when>
              <otherwise> ORDER BY id DESC</otherwise>
            </choose>
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<MessageEntity> findPage(@Param("conversationId") long conversationId,
                                 @Param("userId") long userId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit,
                                 @Param("asc") boolean asc);

    @Select("""
            SELECT id, conversation_id, user_id, role, content, model, prompt_tokens,
                   completion_tokens, request_id, status, error_code, first_token_ms,
                   latency_ms, created_at
            FROM messages
            WHERE conversation_id = #{conversationId} AND user_id = #{userId}
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<MessageEntity> findRecent(@Param("conversationId") long conversationId,
                                   @Param("userId") long userId,
                                   @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM messages
            WHERE conversation_id = #{conversationId} AND user_id = #{userId}
            """)
    long countByConversation(@Param("conversationId") long conversationId,
                             @Param("userId") long userId);

    @Update("""
            UPDATE messages
            SET content = #{content},
                model = #{model},
                prompt_tokens = #{promptTokens},
                completion_tokens = #{completionTokens},
                status = #{status},
                error_code = #{errorCode},
                first_token_ms = #{firstTokenMs},
                latency_ms = #{latencyMs}
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int updateAssistant(@Param("id") long id,
                        @Param("userId") long userId,
                        @Param("content") String content,
                        @Param("model") String model,
                        @Param("promptTokens") int promptTokens,
                        @Param("completionTokens") int completionTokens,
                        @Param("status") String status,
                        @Param("errorCode") String errorCode,
                        @Param("firstTokenMs") Integer firstTokenMs,
                        @Param("latencyMs") Integer latencyMs);
}
