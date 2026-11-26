package com.shiye.user.module.usage.mapper;

import com.shiye.user.module.usage.domain.vo.UsageTotals;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface GlobalUsageMonthlyMapper {

    @Select("""
            SELECT COALESCE(tokens, 0) AS tokens,
                   COALESCE(cost, 0) AS cost,
                   COALESCE(calls, 0) AS calls
            FROM global_usage_monthly
            WHERE month = #{month}
            """)
    UsageTotals find(@Param("month") String month);

    @Insert("""
            INSERT IGNORE INTO global_usage_monthly (month, tokens, cost, calls)
            VALUES (#{month}, 0, 0, 0)
            """)
    int ensureRow(@Param("month") String month);

    @Insert("""
            INSERT INTO global_usage_monthly (month, tokens, cost, calls)
            VALUES (#{month}, #{tokens}, #{cost}, #{calls})
            ON DUPLICATE KEY UPDATE
                tokens = tokens + VALUES(tokens),
                cost = cost + VALUES(cost),
                calls = calls + VALUES(calls)
            """)
    int add(@Param("month") String month,
            @Param("tokens") long tokens,
            @Param("cost") BigDecimal cost,
            @Param("calls") long calls);

    @Update("""
            UPDATE global_usage_monthly
            SET tokens = tokens + #{tokens},
                cost = cost + #{cost},
                updated_at = UTC_TIMESTAMP(3)
            WHERE month = #{month}
              AND tokens + #{tokens} <= #{tokenLimit}
              AND cost + #{cost} <= #{costLimit}
            """)
    int reserve(@Param("month") String month,
                @Param("tokens") long tokens,
                @Param("cost") BigDecimal cost,
                @Param("tokenLimit") long tokenLimit,
                @Param("costLimit") BigDecimal costLimit);

    @Update("""
            UPDATE global_usage_monthly
            SET tokens = GREATEST(0, tokens - #{tokens}),
                cost = GREATEST(0, cost - #{cost}),
                updated_at = UTC_TIMESTAMP(3)
            WHERE month = #{month}
            """)
    int release(@Param("month") String month,
                @Param("tokens") long tokens,
                @Param("cost") BigDecimal cost);

    @Select("SELECT GET_LOCK(#{name}, 3)")
    Integer acquireLock(@Param("name") String name);

    @Select("SELECT RELEASE_LOCK(#{name})")
    Integer releaseLock(@Param("name") String name);
}
