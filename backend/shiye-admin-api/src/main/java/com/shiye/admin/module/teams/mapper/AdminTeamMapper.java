package com.shiye.admin.module.teams.mapper;

import com.shiye.admin.module.teams.domain.entity.AdminTeamRowEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface AdminTeamMapper {

    @Select("""
            <script>
            SELECT t.id, t.name, t.owner_user_id, u.email AS owner_email, t.status,
                   t.monthly_token_quota, t.monthly_cost_quota, t.created_at,
                   (SELECT COUNT(*) FROM team_members m
                    WHERE m.team_id = t.id AND m.status = 'ACTIVE') AS member_count,
                   COALESCE((SELECT um.tokens FROM team_usage_monthly um
                             WHERE um.team_id = t.id AND um.month = #{month}), 0) AS tokens_used,
                   COALESCE((SELECT um.cost FROM team_usage_monthly um
                             WHERE um.team_id = t.id AND um.month = #{month}), 0) AS cost_used,
                   COALESCE((SELECT um.calls FROM team_usage_monthly um
                             WHERE um.team_id = t.id AND um.month = #{month}), 0) AS calls
            FROM teams t
            LEFT JOIN users u ON u.id = t.owner_user_id
            WHERE t.deleted_at IS NULL
            <if test="q != null and q != ''">
              AND (t.name LIKE CONCAT('%', #{q}, '%')
                   OR u.email LIKE CONCAT('%', #{q}, '%'))
            </if>
            <if test="status != null"> AND t.status = #{status}</if>
            ORDER BY t.created_at DESC, t.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminTeamRowEntity> findPage(@Param("q") String q,
                                      @Param("status") String status,
                                      @Param("month") String month,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM teams t
            LEFT JOIN users u ON u.id = t.owner_user_id
            WHERE t.deleted_at IS NULL
            <if test="q != null and q != ''">
              AND (t.name LIKE CONCAT('%', #{q}, '%')
                   OR u.email LIKE CONCAT('%', #{q}, '%'))
            </if>
            <if test="status != null"> AND t.status = #{status}</if>
            </script>
            """)
    long count(@Param("q") String q, @Param("status") String status);

    @Select("""
            SELECT t.id, t.name, t.owner_user_id, u.email AS owner_email, t.status,
                   t.monthly_token_quota, t.monthly_cost_quota, t.created_at,
                   (SELECT COUNT(*) FROM team_members m
                    WHERE m.team_id = t.id AND m.status = 'ACTIVE') AS member_count,
                   COALESCE((SELECT um.tokens FROM team_usage_monthly um
                             WHERE um.team_id = t.id AND um.month = #{month}), 0) AS tokens_used,
                   COALESCE((SELECT um.cost FROM team_usage_monthly um
                             WHERE um.team_id = t.id AND um.month = #{month}), 0) AS cost_used,
                   COALESCE((SELECT um.calls FROM team_usage_monthly um
                             WHERE um.team_id = t.id AND um.month = #{month}), 0) AS calls
            FROM teams t
            LEFT JOIN users u ON u.id = t.owner_user_id
            WHERE t.id = #{id} AND t.deleted_at IS NULL
            """)
    AdminTeamRowEntity findById(@Param("id") long id, @Param("month") String month);

    @Update("""
            UPDATE teams
            SET monthly_token_quota = #{tokens},
                monthly_cost_quota = #{cost},
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int updateQuota(@Param("id") long id,
                    @Param("tokens") long tokens,
                    @Param("cost") BigDecimal cost);

    @Select("""
            SELECT COUNT(*)
            FROM teams
            WHERE owner_user_id = #{userId}
              AND deleted_at IS NULL AND status = 'ACTIVE'
            """)
    long countActiveOwnedBy(@Param("userId") long userId);
}
