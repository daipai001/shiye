package com.shiye.admin.module.audit.mapper;

import com.shiye.admin.module.audit.domain.entity.AdminAuditLogRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AdminAuditMapper {

    @Insert("""
            INSERT INTO admin_audit_logs
                (admin_id, action, resource_type, resource_id, detail, ip, user_agent)
            VALUES (#{adminId}, #{action}, #{resourceType}, #{resourceId},
                    CAST(#{detail} AS JSON), #{ip}, #{userAgent})
            """)
    int insert(@Param("adminId") long adminId,
               @Param("action") String action,
               @Param("resourceType") String resourceType,
               @Param("resourceId") String resourceId,
               @Param("detail") String detail,
               @Param("ip") String ip,
               @Param("userAgent") String userAgent);

    @Select("""
            <script>
            SELECT l.id, l.admin_id, a.email AS admin_email, l.action, l.resource_type,
                   l.resource_id, l.detail, l.ip, l.user_agent, l.created_at
            FROM admin_audit_logs l
            LEFT JOIN admin_users a ON a.id = l.admin_id
            WHERE 1 = 1
            <if test="adminId != null"> AND l.admin_id = #{adminId}</if>
            <if test="action != null"> AND l.action = #{action}</if>
            <if test="from != null"> AND l.created_at &gt;= #{from}</if>
            <if test="to != null"> AND l.created_at &lt; #{to}</if>
            ORDER BY l.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminAuditLogRow> findPage(@Param("adminId") Long adminId,
                                    @Param("action") String action,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM admin_audit_logs l
            WHERE 1 = 1
            <if test="adminId != null"> AND l.admin_id = #{adminId}</if>
            <if test="action != null"> AND l.action = #{action}</if>
            <if test="from != null"> AND l.created_at &gt;= #{from}</if>
            <if test="to != null"> AND l.created_at &lt; #{to}</if>
            </script>
            """)
    long count(@Param("adminId") Long adminId,
               @Param("action") String action,
               @Param("from") LocalDateTime from,
               @Param("to") LocalDateTime to);
}
