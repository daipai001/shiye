package com.shiye.user.module.team.mapper;

import com.shiye.user.module.team.domain.entity.TeamInviteEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TeamInviteMapper {

    @Insert("""
            INSERT INTO team_invites (team_id, inviter_user_id, token_hash, role, expires_at)
            VALUES (#{teamId}, #{inviterUserId}, #{tokenHash}, #{role}, #{expiresAt})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TeamInviteEntity invite);

    @Select("""
            SELECT id, team_id, inviter_user_id, token_hash, role, expires_at, used_at, created_at
            FROM team_invites
            WHERE token_hash = #{tokenHash}
              AND used_at IS NULL
              AND expires_at > UTC_TIMESTAMP(3)
            LIMIT 1
            """)
    TeamInviteEntity findActiveByHash(@Param("tokenHash") String tokenHash);

    @Update("""
            UPDATE team_invites
            SET used_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND used_at IS NULL
            """)
    int markUsed(@Param("id") long id);

    @Delete("DELETE FROM team_invites WHERE team_id = #{teamId}")
    int deleteByTeam(@Param("teamId") long teamId);
}
