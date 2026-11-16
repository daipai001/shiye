package com.shiye.admin.module.auth.mapper;

import com.shiye.admin.module.auth.domain.entity.AdminRefreshTokenEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminRefreshTokenMapper {

    @Insert("""
            INSERT INTO refresh_tokens (subject_type, subject_id, token_hash, expires_at,
                                        user_agent, ip)
            VALUES (#{subjectType}, #{subjectId}, #{tokenHash}, #{expiresAt},
                    #{userAgent}, #{ip})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminRefreshTokenEntity token);

    @Select("""
            SELECT id, subject_type, subject_id, token_hash, expires_at, revoked_at,
                   replaced_by_id, user_agent, ip, created_at
            FROM refresh_tokens
            WHERE token_hash = #{tokenHash}
              AND revoked_at IS NULL
              AND expires_at > UTC_TIMESTAMP(3)
            LIMIT 1
            """)
    AdminRefreshTokenEntity findActiveByHash(@Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE refresh_tokens
            SET revoked_at = UTC_TIMESTAMP(3), replaced_by_id = #{replacedById}
            WHERE id = #{id} AND revoked_at IS NULL
            """)
    int revoke(@Param("id") Long id, @Param("replacedById") Long replacedById);

    @Update("""
            UPDATE refresh_tokens
            SET revoked_at = UTC_TIMESTAMP(3)
            WHERE subject_type = #{subjectType} AND subject_id = #{subjectId}
              AND revoked_at IS NULL
            """)
    int revokeAllForSubject(@Param("subjectType") String subjectType,
                            @Param("subjectId") Long subjectId);
}
