package com.shiye.user.module.auth.mapper;

import com.shiye.user.module.auth.domain.entity.ExtensionAuthCodeEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ExtensionAuthCodeMapper {

    @Insert("""
            INSERT INTO extension_auth_codes (user_id, code_hash, expires_at)
            VALUES (#{userId}, #{codeHash}, #{expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ExtensionAuthCodeEntity code);

    @Select("""
            SELECT id, user_id, code_hash, expires_at, used_at, created_at
            FROM extension_auth_codes
            WHERE code_hash = #{codeHash}
              AND used_at IS NULL
              AND expires_at > UTC_TIMESTAMP(3)
            LIMIT 1
            """)
    ExtensionAuthCodeEntity findActiveByHash(@Param("codeHash") String codeHash);

    @Update("UPDATE extension_auth_codes SET used_at = UTC_TIMESTAMP(3) WHERE id = #{id} AND used_at IS NULL")
    int markUsed(@Param("id") Long id);
}
