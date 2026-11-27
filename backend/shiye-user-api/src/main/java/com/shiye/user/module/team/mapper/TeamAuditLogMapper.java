package com.shiye.user.module.team.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface TeamAuditLogMapper {

    @Insert("""
            INSERT INTO team_audit_logs
                (team_id, operator_id, action, target_type, target_id,
                 detail, ip, user_agent)
            VALUES
                (#{teamId}, #{operatorId}, #{action}, #{targetType}, #{targetId},
                 CAST(#{detail} AS JSON), #{ip}, #{userAgent})
            """)
    int insert(@Param("teamId") long teamId,
               @Param("operatorId") long operatorId,
               @Param("action") String action,
               @Param("targetType") String targetType,
               @Param("targetId") String targetId,
               @Param("detail") String detail,
               @Param("ip") String ip,
               @Param("userAgent") String userAgent);

    @Delete("""
            DELETE FROM team_audit_logs
            WHERE created_at < #{before}
            ORDER BY id
            LIMIT #{limit}
            """)
    int deleteBefore(@Param("before") LocalDateTime before,
                     @Param("limit") int limit);
}
