package com.shiye.admin.module.users.mapper;

import com.shiye.admin.module.users.domain.entity.AdminUserRowEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AdminUserQueryMapper {

    @Select("""
            <script>
            SELECT u.id, u.email, u.display_name, u.status, u.monthly_token_quota,
                   u.monthly_cost_quota, u.last_login_at, u.created_at,
                   COALESCE(um.tokens, 0) AS tokens_used,
                   COALESCE(um.cost, 0) AS cost_used,
                   (SELECT COUNT(*) FROM articles a
                    WHERE a.user_id = u.id AND a.deleted_at IS NULL) AS article_count
            FROM users u
            LEFT JOIN user_usage_monthly um
              ON um.user_id = u.id AND um.month = #{month}
            WHERE u.deleted_at IS NULL
            <if test="q != null and q != ''">
              AND (u.email LIKE CONCAT('%', #{q}, '%')
                   OR u.display_name LIKE CONCAT('%', #{q}, '%'))
            </if>
            <if test="status != null"> AND u.status = #{status}</if>
            <choose>
              <when test="sort == 'usage'"> ORDER BY tokens_used DESC, u.id DESC</when>
              <otherwise> ORDER BY u.created_at DESC, u.id DESC</otherwise>
            </choose>
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminUserRowEntity> findPage(@Param("q") String q,
                                      @Param("status") String status,
                                      @Param("sort") String sort,
                                      @Param("month") String month,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM users u
            WHERE u.deleted_at IS NULL
            <if test="q != null and q != ''">
              AND (u.email LIKE CONCAT('%', #{q}, '%')
                   OR u.display_name LIKE CONCAT('%', #{q}, '%'))
            </if>
            <if test="status != null"> AND u.status = #{status}</if>
            </script>
            """)
    long count(@Param("q") String q, @Param("status") String status);

    @Select("""
            SELECT u.id, u.email, u.display_name, u.status, u.monthly_token_quota,
                   u.monthly_cost_quota, u.last_login_at, u.created_at,
                   COALESCE(um.tokens, 0) AS tokens_used,
                   COALESCE(um.cost, 0) AS cost_used,
                   (SELECT COUNT(*) FROM articles a
                    WHERE a.user_id = u.id AND a.deleted_at IS NULL) AS article_count
            FROM users u
            LEFT JOIN user_usage_monthly um
              ON um.user_id = u.id AND um.month = #{month}
            WHERE u.id = #{id} AND u.deleted_at IS NULL
            """)
    AdminUserRowEntity findById(@Param("id") long id, @Param("month") String month);

    @Update("""
            UPDATE users
            SET status = #{status}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateStatus(@Param("id") long id, @Param("status") String status);

    @Update("""
            UPDATE users
            SET monthly_token_quota = #{tokens},
                monthly_cost_quota = #{cost},
                quota_source = 'ADMIN',
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateQuota(@Param("id") long id,
                    @Param("tokens") long tokens,
                    @Param("cost") BigDecimal cost);

    @Update("""
            UPDATE users
            SET password_hash = #{passwordHash}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updatePassword(@Param("id") long id, @Param("passwordHash") String passwordHash);

    @Update("""
            UPDATE refresh_tokens
            SET revoked_at = UTC_TIMESTAMP(3)
            WHERE subject_type = 'USER' AND subject_id = #{userId} AND revoked_at IS NULL
            """)
    int revokeUserTokens(@Param("userId") long userId);
}
