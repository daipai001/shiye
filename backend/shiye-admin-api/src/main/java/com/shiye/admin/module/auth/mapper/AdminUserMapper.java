package com.shiye.admin.module.auth.mapper;

import com.shiye.admin.module.auth.domain.entity.AdminUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdminUserMapper {

    @Select("""
            SELECT id, email, password_hash, name, role, status,
                   last_login_at, created_at, updated_at
            FROM admin_users
            WHERE email = #{email}
            LIMIT 1
            """)
    AdminUserEntity findByEmail(@Param("email") String email);

    @Select("""
            SELECT id, email, password_hash, name, role, status,
                   last_login_at, created_at, updated_at
            FROM admin_users
            WHERE id = #{id}
            """)
    AdminUserEntity findById(@Param("id") Long id);

    @Update("UPDATE admin_users SET last_login_at = UTC_TIMESTAMP(3) WHERE id = #{id}")
    int updateLastLogin(@Param("id") Long id);
}
