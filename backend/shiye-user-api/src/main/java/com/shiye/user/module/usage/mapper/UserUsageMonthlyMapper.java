package com.shiye.user.module.usage.mapper;

import com.shiye.user.module.usage.domain.entity.UserUsageMonthlyEntity;
import com.shiye.user.module.usage.domain.vo.UsageTotals;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Select("""
            SELECT id
            FROM users
            WHERE id = #{userId} AND deleted_at IS NULL
            FOR UPDATE
            """)
    Long lockUserRow(@Param("userId") long userId);

    @Select("""
            SELECT id, user_id, month, tokens, cost, calls, updated_at
            FROM user_usage_monthly
            WHERE user_id = #{userId} AND month = #{month}
            FOR UPDATE
            """)
    UserUsageMonthlyEntity findForUpdate(@Param("userId") long userId,
                                         @Param("month") String month);

    @Insert("""
            INSERT IGNORE INTO user_usage_monthly (user_id, month, tokens, cost, calls)
            VALUES (#{userId}, #{month}, 0, 0, 0)
            """)
    int insertEmpty(@Param("userId") long userId, @Param("month") String month);

    @Update("""
            UPDATE user_usage_monthly
            SET tokens = tokens + #{tokens},
                cost = cost + #{cost},
                updated_at = UTC_TIMESTAMP(3)
            WHERE user_id = #{userId} AND month = #{month}
            """)
    int add(@Param("userId") long userId,
            @Param("month") String month,
            @Param("tokens") long tokens,
            @Param("cost") BigDecimal cost);

    @Update("""
            UPDATE user_usage_monthly
            SET tokens = GREATEST(0, tokens - #{tokens}),
                cost = GREATEST(0, cost - #{cost}),
                updated_at = UTC_TIMESTAMP(3)
            WHERE user_id = #{userId} AND month = #{month}
            """)
    int release(@Param("userId") long userId,
                @Param("month") String month,
                @Param("tokens") long tokens,
                @Param("cost") BigDecimal cost);
}
