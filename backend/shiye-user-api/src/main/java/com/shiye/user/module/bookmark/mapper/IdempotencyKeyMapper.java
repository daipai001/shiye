package com.shiye.user.module.bookmark.mapper;

import com.shiye.user.module.bookmark.domain.entity.IdempotencyKeyEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IdempotencyKeyMapper {

    @Select("""
            SELECT id, user_id, endpoint, idempotency_key, request_hash,
                   response_code, CAST(response_body AS CHAR) AS response_body,
                   expires_at, created_at
            FROM idempotency_keys
            WHERE user_id = #{userId}
              AND endpoint = #{endpoint}
              AND idempotency_key = #{idempotencyKey}
            """)
    IdempotencyKeyEntity find(@Param("userId") long userId,
                              @Param("endpoint") String endpoint,
                              @Param("idempotencyKey") String idempotencyKey);

    @Insert("""
            INSERT INTO idempotency_keys (user_id, endpoint, idempotency_key, request_hash,
                                         response_code, response_body, expires_at)
            VALUES (#{userId}, #{endpoint}, #{idempotencyKey}, #{requestHash},
                    #{responseCode}, #{responseBody}, #{expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(IdempotencyKeyEntity entity);

    @Delete("DELETE FROM idempotency_keys WHERE id = #{id}")
    int deleteById(@Param("id") long id);
}
