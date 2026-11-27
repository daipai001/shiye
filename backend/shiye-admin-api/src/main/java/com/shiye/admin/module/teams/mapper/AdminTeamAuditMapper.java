package com.shiye.admin.module.teams.mapper;

import com.shiye.admin.module.teams.domain.entity.AdminTeamAuditRowEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminTeamAuditMapper {

    @Select("""
            <script>
            SELECT l.id, l.team_id, t.name AS team_name, l.operator_id,
                   u.email AS operator_email, l.action, l.target_type, l.target_id,
                   l.detail, l.ip, l.user_agent, l.created_at
            FROM team_audit_logs l
            LEFT JOIN teams t ON t.id = l.team_id
            LEFT JOIN users u ON u.id = l.operator_id
            WHERE 1 = 1
            <if test="teamId != null"> AND l.team_id = #{teamId}</if>
            <if test="operatorId != null"> AND l.operator_id = #{operatorId}</if>
            <if test="action != null"> AND l.action = #{action}</if>
            <if test="from != null"> AND l.created_at &gt;= #{from}</if>
            <if test="to != null"> AND l.created_at &lt; #{to}</if>
            ORDER BY l.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminTeamAuditRowEntity> findPage(@Param("teamId") Long teamId,
                                           @Param("operatorId") Long operatorId,
                                           @Param("action") String action,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM team_audit_logs l
            WHERE 1 = 1
            <if test="teamId != null"> AND l.team_id = #{teamId}</if>
            <if test="operatorId != null"> AND l.operator_id = #{operatorId}</if>
            <if test="action != null"> AND l.action = #{action}</if>
            <if test="from != null"> AND l.created_at &gt;= #{from}</if>
            <if test="to != null"> AND l.created_at &lt; #{to}</if>
            </script>
            """)
    long count(@Param("teamId") Long teamId,
               @Param("operatorId") Long operatorId,
               @Param("action") String action,
               @Param("from") LocalDateTime from,
               @Param("to") LocalDateTime to);
}
