package com.shiye.user.module.tag.mapper;

import com.shiye.user.module.tag.domain.entity.TagEntity;
import com.shiye.user.module.tag.domain.entity.TagRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TagMapper {

    @Select("""
            SELECT id, user_id, name, normalized_name, color, created_at, updated_at
            FROM tags
            WHERE user_id = #{userId} AND normalized_name = #{normalizedName}
            LIMIT 1
            """)
    TagEntity findByNormalizedName(@Param("userId") long userId,
                                   @Param("normalizedName") String normalizedName);

    @Insert("""
            INSERT INTO tags (user_id, name, normalized_name, color)
            VALUES (#{userId}, #{name}, #{normalizedName}, #{color})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TagEntity tag);

    @Delete("DELETE FROM article_tags WHERE article_id = #{articleId} AND user_id = #{userId}")
    int deleteArticleTags(@Param("articleId") long articleId, @Param("userId") long userId);

    @Insert("""
            INSERT IGNORE INTO article_tags (article_id, tag_id, user_id)
            VALUES (#{articleId}, #{tagId}, #{userId})
            """)
    int insertArticleTag(@Param("articleId") long articleId,
                         @Param("tagId") long tagId,
                         @Param("userId") long userId);

    @Select("""
            SELECT t.id AS tag_id, NULL AS article_id, t.name, t.color,
                   COUNT(at.article_id) AS article_count
            FROM tags t
            LEFT JOIN article_tags at ON at.tag_id = t.id AND at.user_id = t.user_id
            WHERE t.user_id = #{userId}
            GROUP BY t.id, t.name, t.color
            HAVING article_count > 0
            ORDER BY article_count DESC, t.id DESC
            """)
    List<TagRow> listWithCounts(@Param("userId") long userId);

    @Select("""
            <script>
            SELECT at.article_id AS article_id, t.id AS tag_id, t.name, t.color,
                   NULL AS article_count
            FROM article_tags at
            JOIN tags t ON t.id = at.tag_id AND t.user_id = at.user_id
            WHERE at.user_id = #{userId}
              AND at.article_id IN
              <foreach collection="articleIds" item="articleId" open="(" separator="," close=")">
                #{articleId}
              </foreach>
            ORDER BY at.article_id, t.id
            </script>
            """)
    List<TagRow> findByArticleIds(@Param("userId") long userId,
                                  @Param("articleIds") List<Long> articleIds);
}
