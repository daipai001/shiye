package com.shiye.user.module.usage.mapper;

import com.shiye.user.module.usage.domain.entity.UsageReservationEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UsageReservationMapper {

    @Insert("""
            INSERT INTO usage_reservations
                (user_id, team_id, month, user_tokens, user_cost,
                 team_tokens, team_cost, global_tokens, global_cost,
                 status, expires_at)
            VALUES
                (#{userId}, #{teamId}, #{month}, #{userTokens}, #{userCost},
                 #{teamTokens}, #{teamCost}, #{globalTokens}, #{globalCost},
                 'ACTIVE', #{expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UsageReservationEntity reservation);

    @Update("""
            UPDATE usage_reservations
            SET status = 'RELEASED', released_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND status = 'ACTIVE'
            """)
    int markReleased(@Param("id") long id);

    @Select("""
            SELECT id, user_id, team_id, month, user_tokens, user_cost,
                   team_tokens, team_cost, global_tokens, global_cost,
                   status, created_at, expires_at, released_at
            FROM usage_reservations
            WHERE status = 'ACTIVE' AND expires_at < UTC_TIMESTAMP(3)
            ORDER BY id
            LIMIT #{limit}
            """)
    List<UsageReservationEntity> findExpired(@Param("limit") int limit);

    @Delete("""
            DELETE FROM usage_reservations
            WHERE status = 'RELEASED' AND released_at < #{before}
            ORDER BY id
            LIMIT #{limit}
            """)
    int deleteReleasedBefore(@Param("before") LocalDateTime before,
                             @Param("limit") int limit);
}
