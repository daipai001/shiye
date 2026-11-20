package com.shiye.user.module.usage.mapper;

import com.shiye.user.module.usage.domain.entity.TeamUsageMonthlyEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
