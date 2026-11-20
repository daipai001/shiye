package com.shiye.user.module.team.mapper;

import com.shiye.user.module.team.domain.entity.TeamEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface TeamMapper {

    @Select("""
            SELECT t.id, t.name, t.owner_user_id, t.status, t.monthly_token_quota,
                   t.monthly_cost_quota, t.created_at, t.updated_at, t.deleted_at,
                   m.role AS my_role,
                   (SELECT COUNT(*) FROM team_members x
                    WHERE x.team_id = t.id AND x.status = 'ACTIVE') AS member_count
            FROM teams t
            JOIN team_members m ON m.team_id = t.id AND m.user_id = #{userId}
            WHERE t.deleted_at IS NULL AND t.status = 'ACTIVE'
              AND m.status = 'ACTIVE'
            ORDER BY t.updated_at DESC, t.id DESC
            """)
    List<TeamEntity> findByUser(@Param("userId") long userId);

    @Select("""
            SELECT t.id, t.name, t.owner_user_id, t.status, t.monthly_token_quota,
                   t.monthly_cost_quota, t.created_at, t.updated_at, t.deleted_at,
                   (SELECT COUNT(*) FROM team_members x
                    WHERE x.team_id = t.id AND x.status = 'ACTIVE') AS member_count
            FROM teams t
            WHERE t.id = #{id} AND t.deleted_at IS NULL
            """)
    TeamEntity findById(@Param("id") long id);

    @Insert("""
            INSERT INTO teams (name, owner_user_id, status, monthly_token_quota, monthly_cost_quota)
            VALUES (#{name}, #{ownerUserId}, #{status}, #{monthlyTokenQuota}, #{monthlyCostQuota})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TeamEntity team);

    @Update("""
            UPDATE teams
            SET name = #{name},
                monthly_token_quota = #{monthlyTokenQuota},
                monthly_cost_quota = #{monthlyCostQuota},
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    int update(@Param("id") long id,
               @Param("name") String name,
               @Param("monthlyTokenQuota") long monthlyTokenQuota,
               @Param("monthlyCostQuota") BigDecimal monthlyCostQuota);
}
