package com.shiye.user.module.usage.mapper;

import com.shiye.user.module.usage.domain.entity.UserUsageMonthlyEntity;
import com.shiye.user.module.usage.domain.vo.UsageTotals;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;

@Mapper
public interface UserUsageMonthlyMapper {

    @Select("""
            SELECT id, user_id, month, tokens, cost, calls, updated_at
            FROM user_usage_monthly
            WHERE user_id = #{userId} AND month = #{month}
            """)
    UserUsageMonthlyEntity find(@Param("userId") long userId, @Param("month") String month);

    @Insert("""
            INSERT INTO user_usage_monthly (user_id, month, tokens, cost, calls)
            VALUES (#{userId}, #{month}, #{tokens}, #{cost}, 1)
            ON DUPLICATE KEY UPDATE
                tokens = tokens + VALUES(tokens),
                cost = cost + VALUES(cost),
                calls = calls + 1
            """)
    int increment(@Param("userId") long userId,
                  @Param("month") String month,
                  @Param("tokens") long tokens,
                  @Param("cost") BigDecimal cost);

    @Select("""
            SELECT COALESCE(SUM(tokens), 0) AS tokens,
                   COALESCE(SUM(cost), 0) AS cost,
                   COALESCE(SUM(calls), 0) AS calls
            FROM user_usage_monthly
            WHERE month = #{month}
            """)
    UsageTotals sumByMonth(@Param("month") String month);
}
