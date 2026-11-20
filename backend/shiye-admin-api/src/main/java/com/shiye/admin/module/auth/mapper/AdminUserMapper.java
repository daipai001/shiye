package com.shiye.admin.module.auth.mapper;

import com.shiye.admin.module.auth.domain.entity.AdminUserEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

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

    @Select("""
            SELECT id, email, password_hash, name, role, status,
                   last_login_at, created_at, updated_at
            FROM admin_users
            ORDER BY id ASC
            """)
    List<AdminUserEntity> findAll();

    @Select("SELECT COUNT(*) FROM admin_users WHERE email = #{email}")
    int countByEmail(@Param("email") String email);

    @Select("""
            SELECT COUNT(*)
            FROM admin_users
            WHERE role = 'SUPER_ADMIN'
              AND status = 'ACTIVE'
              AND id <> #{excludeId}
            """)
    int countActiveSuperAdminsExcluding(@Param("excludeId") Long excludeId);

    @Insert("""
            INSERT INTO admin_users (email, password_hash, name, role, status)
            VALUES (#{email}, #{passwordHash}, #{name}, #{role}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(AdminUserEntity admin);

    @Update("UPDATE admin_users SET name = #{name}, role = #{role} WHERE id = #{id}")
    int updateProfile(@Param("id") Long id,
                      @Param("name") String name,
                      @Param("role") String role);

    @Update("UPDATE admin_users SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE admin_users SET password_hash = #{passwordHash} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);
}
