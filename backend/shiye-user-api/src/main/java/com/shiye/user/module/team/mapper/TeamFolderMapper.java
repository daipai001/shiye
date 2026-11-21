package com.shiye.user.module.team.mapper;

import com.shiye.user.module.team.domain.entity.TeamFolderEntity;
import com.shiye.user.module.team.domain.entity.TeamFolderRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TeamFolderMapper {

    @Select("""
            SELECT f.id, f.team_id, f.name, f.created_by, f.created_at, f.updated_at,
                   COUNT(af.id) AS article_count
            FROM team_folders f
            LEFT JOIN article_team_folders af ON af.folder_id = f.id
            WHERE f.team_id = #{teamId}
            GROUP BY f.id, f.team_id, f.name, f.created_by, f.created_at, f.updated_at
            ORDER BY f.name ASC, f.id ASC
            """)
    List<TeamFolderEntity> listWithCounts(@Param("teamId") long teamId);

    @Select("""
            SELECT id, team_id, name, created_by, created_at, updated_at
            FROM team_folders
            WHERE id = #{folderId} AND team_id = #{teamId}
            """)
    TeamFolderEntity findByIdAndTeam(@Param("folderId") long folderId,
                                     @Param("teamId") long teamId);

    @Select("""
            SELECT id, team_id, name, created_by, created_at, updated_at
            FROM team_folders
            WHERE team_id = #{teamId} AND name = #{name}
            LIMIT 1
            """)
    TeamFolderEntity findByName(@Param("teamId") long teamId, @Param("name") String name);

    @Insert("""
            INSERT INTO team_folders (team_id, name, created_by)
            VALUES (#{teamId}, #{name}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TeamFolderEntity folder);

    @Update("""
            UPDATE team_folders
            SET name = #{name}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{folderId} AND team_id = #{teamId}
            """)
    int update(@Param("folderId") long folderId,
               @Param("teamId") long teamId,
               @Param("name") String name);

    @Delete("DELETE FROM team_folders WHERE id = #{folderId} AND team_id = #{teamId}")
    int delete(@Param("folderId") long folderId, @Param("teamId") long teamId);

    @Delete("DELETE FROM article_team_folders WHERE folder_id = #{folderId} AND team_id = #{teamId}")
    int deleteArticleFolders(@Param("folderId") long folderId,
                             @Param("teamId") long teamId);

    @Insert("""
            INSERT IGNORE INTO article_team_folders (article_id, team_id, folder_id, created_by)
            VALUES (#{articleId}, #{teamId}, #{folderId}, #{createdBy})
            """)
    int insertArticleFolder(@Param("articleId") long articleId,
                            @Param("teamId") long teamId,
                            @Param("folderId") long folderId,
                            @Param("createdBy") long createdBy);

    @Delete("""
            DELETE FROM article_team_folders
            WHERE article_id = #{articleId} AND folder_id = #{folderId} AND team_id = #{teamId}
            """)
    int deleteArticleFolder(@Param("articleId") long articleId,
                            @Param("folderId") long folderId,
                            @Param("teamId") long teamId);

    @Select("""
            <script>
            SELECT af.article_id, af.folder_id, f.name
            FROM article_team_folders af
            JOIN team_folders f ON f.id = af.folder_id
            WHERE af.team_id = #{teamId}
              AND af.article_id IN
              <foreach collection="articleIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY f.name ASC, f.id ASC
            </script>
            """)
    List<TeamFolderRow> findByArticleIds(@Param("teamId") long teamId,
                                         @Param("articleIds") List<Long> articleIds);

    @Select("""
            SELECT af.article_id
            FROM article_team_folders af
            JOIN articles a ON a.id = af.article_id
            WHERE af.folder_id = #{folderId} AND af.team_id = #{teamId}
              AND a.team_id = #{teamId} AND a.deleted_at IS NULL
            """)
    List<Long> findArticleIdsByFolder(@Param("teamId") long teamId,
                                      @Param("folderId") long folderId);

    @Delete("""
            DELETE FROM article_team_folders
            WHERE article_id = #{articleId} AND team_id = #{teamId}
            """)
    int deleteArticleRelations(@Param("articleId") long articleId,
                               @Param("teamId") long teamId);

    @Delete("DELETE FROM article_team_folders WHERE team_id = #{teamId}")
    int deleteAllArticleRelations(@Param("teamId") long teamId);

    @Delete("DELETE FROM team_folders WHERE team_id = #{teamId}")
    int deleteAll(@Param("teamId") long teamId);

    @Select("SELECT team_id FROM team_folders WHERE id = #{folderId}")
    Long findTeamId(@Param("folderId") long folderId);
}
