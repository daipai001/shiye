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
                   last_login_at, created_at, updated_at, deleted_at
            FROM users
            WHERE email = #{email} AND deleted_at IS NULL
            LIMIT 1
            """)
    UserEntity findByEmail(@Param("email") String email);

    @Select("""
            SELECT id, email, password_hash, display_name, avatar_url, status,
                   monthly_token_quota, monthly_cost_quota, quota_source,
                   last_login_at, created_at, updated_at, deleted_at
            FROM users
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    UserEntity findById(@Param("id") Long id);

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
}
