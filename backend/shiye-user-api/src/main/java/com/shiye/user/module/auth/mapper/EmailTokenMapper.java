package com.shiye.user.module.auth.mapper;

import com.shiye.user.module.auth.domain.entity.EmailTokenEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmailTokenMapper {

    @Insert("""
            INSERT INTO email_tokens (user_id, type, token_hash, expires_at)
            VALUES (#{userId}, #{type}, #{tokenHash}, #{expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(EmailTokenEntity token);

    @Select("""
            SELECT id, user_id, type, token_hash, expires_at, used_at, created_at
            FROM email_tokens
            WHERE token_hash = #{tokenHash}
              AND type = #{type}
              AND used_at IS NULL
              AND expires_at > UTC_TIMESTAMP(3)
            LIMIT 1
            """)
    EmailTokenEntity findActive(@Param("tokenHash") String tokenHash,
                                @Param("type") String type);

    @Update("""
            UPDATE email_tokens
            SET used_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND used_at IS NULL
            """)
    int markUsed(@Param("id") long id);

    @Delete("""
            DELETE FROM email_tokens
            WHERE expires_at < UTC_TIMESTAMP(3) OR used_at < DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 7 DAY)
            """)
    int deleteExpired();
}
