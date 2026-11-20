package com.shiye.user.module.team.mapper;

import com.shiye.user.module.team.domain.entity.TeamMemberEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TeamMemberMapper {

    @Select("""
            SELECT m.id, m.team_id, m.user_id, m.role, m.status, m.joined_at,
                   m.created_at, m.updated_at, u.email, u.display_name
            FROM team_members m
            JOIN users u ON u.id = m.user_id
            WHERE m.team_id = #{teamId} AND m.status = 'ACTIVE'
            ORDER BY FIELD(m.role, 'OWNER', 'ADMIN', 'MEMBER'), m.id ASC
            """)
    List<TeamMemberEntity> findByTeam(@Param("teamId") long teamId);

    @Select("""
            SELECT m.id, m.team_id, m.user_id, m.role, m.status, m.joined_at,
                   m.created_at, m.updated_at, u.email, u.display_name
            FROM team_members m
            JOIN users u ON u.id = m.user_id
            WHERE m.team_id = #{teamId} AND m.user_id = #{userId} AND m.status = 'ACTIVE'
            LIMIT 1
            """)
    TeamMemberEntity findActive(@Param("teamId") long teamId,
                                @Param("userId") long userId);

    @Select("""
            SELECT COUNT(*)
            FROM team_members
            WHERE team_id = #{teamId} AND role = 'OWNER' AND status = 'ACTIVE'
            """)
    long countActiveOwners(@Param("teamId") long teamId);

    @Select("SELECT COUNT(*) FROM team_members WHERE team_id = #{teamId} AND status = 'ACTIVE'")
    long countActive(@Param("teamId") long teamId);

    @Insert("""
            INSERT INTO team_members (team_id, user_id, role, status)
            VALUES (#{teamId}, #{userId}, #{role}, #{status})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TeamMemberEntity member);

    @Update("""
            UPDATE team_members
            SET role = #{role}, updated_at = UTC_TIMESTAMP(3)
            WHERE team_id = #{teamId} AND user_id = #{userId} AND status = 'ACTIVE'
            """)
    int updateRole(@Param("teamId") long teamId,
                   @Param("userId") long userId,
                   @Param("role") String role);

    @Delete("""
            DELETE FROM team_members
            WHERE team_id = #{teamId} AND user_id = #{userId}
            """)
    int delete(@Param("teamId") long teamId, @Param("userId") long userId);
}
