package com.shiye.user.module.auth.mapper;

import com.shiye.user.module.auth.domain.entity.UserEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

    @Select("""
            SELECT id, email, password_hash, display_name, avatar_url, status,
                   monthly_token_quota, monthly_cost_quota, quota_source,
                   email_verified_at, last_login_at, created_at, updated_at, deleted_at
            FROM users
            WHERE email = #{email} AND deleted_at IS NULL
            LIMIT 1
            """)
    UserEntity findByEmail(@Param("email") String email);

    @Select("""
            SELECT id, email, password_hash, display_name, avatar_url, status,
                   monthly_token_quota, monthly_cost_quota, quota_source,
                   email_verified_at, last_login_at, created_at, updated_at, deleted_at
            FROM users
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    UserEntity findById(@Param("id") Long id);

    @Select("""
            SELECT status
            FROM users
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    String findStatusById(@Param("id") long id);

    @Insert("""
            INSERT INTO users (email, password_hash, display_name, status,
                               monthly_token_quota, monthly_cost_quota, quota_source)
            VALUES (#{email}, #{passwordHash}, #{displayName}, #{status},
                    #{monthlyTokenQuota}, #{monthlyCostQuota}, #{quotaSource})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserEntity user);

    @Update("UPDATE users SET last_login_at = UTC_TIMESTAMP(3) WHERE id = #{id}")
    int updateLastLogin(@Param("id") Long id);

    @Update("""
            UPDATE users
            SET display_name = #{displayName},
                avatar_url = #{avatarUrl},
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateProfile(@Param("id") long id,
                      @Param("displayName") String displayName,
                      @Param("avatarUrl") String avatarUrl);

    @Update("""
            UPDATE users
            SET password_hash = #{passwordHash}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updatePassword(@Param("id") long id,
                       @Param("passwordHash") String passwordHash);

    @Update("""
            UPDATE users
            SET email_verified_at = COALESCE(email_verified_at, UTC_TIMESTAMP(3)),
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int markEmailVerified(@Param("id") long id);
}
