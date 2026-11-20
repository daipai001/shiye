package com.shiye.user.module.team.mapper;

import com.shiye.user.module.team.domain.entity.TeamTagEntity;
import com.shiye.user.module.team.domain.entity.TeamTagRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TeamTagMapper {

    @Select("""
            SELECT t.id AS tag_id, t.name, t.color,
                   COUNT(att.id) AS article_count
            FROM team_tags t
            LEFT JOIN article_team_tags att ON att.tag_id = t.id
            WHERE t.team_id = #{teamId}
            GROUP BY t.id, t.name, t.color
            ORDER BY t.name ASC, t.id ASC
            """)
    List<TeamTagRow> listWithCounts(@Param("teamId") long teamId);

    @Select("""
            SELECT id, team_id, name, normalized_name, color, created_by, created_at, updated_at
            FROM team_tags
            WHERE id = #{tagId} AND team_id = #{teamId}
            """)
    TeamTagEntity findByIdAndTeam(@Param("tagId") long tagId,
                                  @Param("teamId") long teamId);

    @Select("""
            SELECT id, team_id, name, normalized_name, color, created_by, created_at, updated_at
            FROM team_tags
            WHERE team_id = #{teamId} AND normalized_name = #{normalizedName}
            LIMIT 1
            """)
    TeamTagEntity findByNormalizedName(@Param("teamId") long teamId,
                                       @Param("normalizedName") String normalizedName);

    @Insert("""
            INSERT INTO team_tags (team_id, name, normalized_name, color, created_by)
            VALUES (#{teamId}, #{name}, #{normalizedName}, #{color}, #{createdBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TeamTagEntity tag);

    @Update("""
            UPDATE team_tags
            SET name = #{name}, normalized_name = #{normalizedName}, color = #{color},
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{tagId} AND team_id = #{teamId}
            """)
    int update(@Param("tagId") long tagId,
               @Param("teamId") long teamId,
               @Param("name") String name,
               @Param("normalizedName") String normalizedName,
               @Param("color") String color);

    @Delete("DELETE FROM team_tags WHERE id = #{tagId} AND team_id = #{teamId}")
    int delete(@Param("tagId") long tagId, @Param("teamId") long teamId);

    @Delete("DELETE FROM article_team_tags WHERE tag_id = #{tagId} AND team_id = #{teamId}")
    int deleteArticleTags(@Param("tagId") long tagId, @Param("teamId") long teamId);

    @Insert("""
            INSERT IGNORE INTO article_team_tags (article_id, team_id, tag_id, created_by)
            VALUES (#{articleId}, #{teamId}, #{tagId}, #{createdBy})
            """)
    int insertArticleTag(@Param("articleId") long articleId,
                         @Param("teamId") long teamId,
                         @Param("tagId") long tagId,
                         @Param("createdBy") long createdBy);

    @Delete("""
            DELETE FROM article_team_tags
            WHERE article_id = #{articleId} AND tag_id = #{tagId} AND team_id = #{teamId}
            """)
    int deleteArticleTag(@Param("articleId") long articleId,
                         @Param("tagId") long tagId,
                         @Param("teamId") long teamId);

    @Select("""
            <script>
            SELECT att.article_id, att.tag_id, t.name, t.color
            FROM article_team_tags att
            JOIN team_tags t ON t.id = att.tag_id
            WHERE att.team_id = #{teamId}
              AND att.article_id IN
              <foreach collection="articleIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY t.name ASC, t.id ASC
            </script>
            """)
    List<TeamTagRow> findByArticleIds(@Param("teamId") long teamId,
                                      @Param("articleIds") List<Long> articleIds);

    @Select("""
            SELECT att.article_id
            FROM article_team_tags att
            JOIN articles a ON a.id = att.article_id
            WHERE att.tag_id = #{tagId} AND att.team_id = #{teamId}
              AND a.team_id = #{teamId} AND a.deleted_at IS NULL
            """)
    List<Long> findArticleIdsByTag(@Param("teamId") long teamId,
                                   @Param("tagId") long tagId);

    @Delete("""
            DELETE FROM article_team_tags
            WHERE article_id = #{articleId} AND team_id = #{teamId}
            """)
    int deleteArticleRelations(@Param("articleId") long articleId,
                               @Param("teamId") long teamId);

    @Delete("DELETE FROM article_team_tags WHERE team_id = #{teamId}")
    int deleteAllArticleRelations(@Param("teamId") long teamId);

    @Delete("DELETE FROM team_tags WHERE team_id = #{teamId}")
    int deleteAll(@Param("teamId") long teamId);
}
