package com.shiye.user.module.usage.mapper;

import com.shiye.user.module.usage.domain.entity.TeamUsageMonthlyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface TeamUsageMonthlyMapper {

    @Select("""
            SELECT id, team_id, month, tokens, cost, calls, updated_at
            FROM team_usage_monthly
            WHERE team_id = #{teamId} AND month = #{month}
            """)
    TeamUsageMonthlyEntity find(@Param("teamId") long teamId,
                                @Param("month") String month);

    @Select("""
            SELECT id
            FROM teams
            WHERE id = #{teamId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    Long lockTeamRow(@Param("teamId") long teamId);

    @Select("""
            SELECT id, team_id, month, tokens, cost, calls, updated_at
            FROM team_usage_monthly
            WHERE team_id = #{teamId} AND month = #{month}
            FOR UPDATE
            """)
    TeamUsageMonthlyEntity findForUpdate(@Param("teamId") long teamId,
                                         @Param("month") String month);

    @Insert("""
            INSERT INTO team_usage_monthly (team_id, month, tokens, cost, calls)
            VALUES (#{teamId}, #{month}, #{tokens}, #{cost}, 1)
            ON DUPLICATE KEY UPDATE
                tokens = tokens + VALUES(tokens),
                cost = cost + VALUES(cost),
                calls = calls + 1
            """)
    int increment(@Param("teamId") long teamId,
                  @Param("month") String month,
                  @Param("tokens") long tokens,
                  @Param("cost") BigDecimal cost);

    @Insert("""
            INSERT IGNORE INTO team_usage_monthly (team_id, month, tokens, cost, calls)
            VALUES (#{teamId}, #{month}, 0, 0, 0)
            """)
    int ensureRow(@Param("teamId") long teamId, @Param("month") String month);

    @Update("""
            UPDATE team_usage_monthly
            SET tokens = tokens + #{tokens}, cost = cost + #{cost},
                updated_at = UTC_TIMESTAMP(3)
            WHERE team_id = #{teamId} AND month = #{month}
              AND tokens + #{tokens} <= #{tokenLimit}
              AND cost + #{cost} <= #{costLimit}
            """)
    int reserve(@Param("teamId") long teamId,
                @Param("month") String month,
                @Param("tokens") long tokens,
                @Param("cost") BigDecimal cost,
                @Param("tokenLimit") long tokenLimit,
                @Param("costLimit") BigDecimal costLimit);

    @Update("""
            UPDATE team_usage_monthly
            SET tokens = GREATEST(0, tokens - #{tokens}),
                cost = GREATEST(0, cost - #{cost}),
                updated_at = UTC_TIMESTAMP(3)
            WHERE team_id = #{teamId} AND month = #{month}
            """)
    int release(@Param("teamId") long teamId,
                @Param("month") String month,
                @Param("tokens") long tokens,
                @Param("cost") BigDecimal cost);
}
