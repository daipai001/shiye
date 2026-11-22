package com.shiye.user.module.article.mapper;

import com.shiye.user.module.article.domain.entity.ArticleChunkEntity;
import com.shiye.user.module.article.domain.entity.ArticleChunkSearchRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ArticleChunkMapper {

    @Insert("""
            INSERT INTO article_chunks (article_id, user_id, chunk_index, content, token_count,
                                        char_start, char_end, embedding_model, embedding_dim,
                                        vector_id, content_hash)
            VALUES (#{articleId}, #{userId}, #{chunkIndex}, #{content}, #{tokenCount},
                    #{charStart}, #{charEnd}, #{embeddingModel}, #{embeddingDim},
                    #{vectorId}, #{contentHash})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ArticleChunkEntity chunk);

    @Update("""
            UPDATE article_chunks
            SET vector_id = CONCAT('kb:chunk:', id)
            WHERE article_id = #{articleId} AND user_id = #{userId}
            """)
    int assignVectorIds(@Param("articleId") long articleId,
                        @Param("userId") long userId);

    @Select("""
            SELECT id, article_id, user_id, chunk_index, content, token_count,
                   char_start, char_end, embedding_model, embedding_dim, vector_id,
                   content_hash, created_at
            FROM article_chunks
            WHERE article_id = #{articleId} AND user_id = #{userId}
            ORDER BY chunk_index ASC
            """)
    List<ArticleChunkEntity> findByArticle(@Param("articleId") long articleId,
                                           @Param("userId") long userId);

    @Select("""
            SELECT id, article_id, user_id, chunk_index, content, token_count,
                   char_start, char_end, embedding_model, embedding_dim, vector_id,
                   content_hash, created_at
            FROM article_chunks
            WHERE article_id = #{articleId} AND user_id = #{userId}
            ORDER BY chunk_index ASC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ArticleChunkEntity> findPage(@Param("articleId") long articleId,
                                      @Param("userId") long userId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM article_chunks
            WHERE article_id = #{articleId} AND user_id = #{userId}
            """)
    long countByArticle(@Param("articleId") long articleId, @Param("userId") long userId);

    @Delete("DELETE FROM article_chunks WHERE article_id = #{articleId} AND user_id = #{userId}")
    int deleteByArticle(@Param("articleId") long articleId, @Param("userId") long userId);

    @Select("""
            <script>
            SELECT c.id, c.article_id, c.user_id, c.content, c.char_start, c.char_end,
                   MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS score
            FROM article_chunks c
            JOIN articles a ON a.id = c.article_id
            WHERE c.user_id = #{userId}
              AND a.user_id = #{userId} AND a.deleted_at IS NULL AND a.status = 'READY'
              AND MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
            <if test="articleId != null"> AND c.article_id = #{articleId}</if>
            <if test="articleIds != null">
              AND c.article_id IN
              <foreach collection="articleIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </if>
            ORDER BY score DESC
            LIMIT #{limit}
            </script>
            """)
    List<ArticleChunkSearchRow> searchKeyword(@Param("userId") long userId,
                                              @Param("query") String query,
                                              @Param("articleId") Long articleId,
                                              @Param("articleIds") List<Long> articleIds,
                                              @Param("limit") int limit);

    @Select("""
            <script>
            SELECT c.id, c.article_id, c.user_id, c.content, c.char_start, c.char_end,
                   MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS score
            FROM article_chunks c
            JOIN articles a ON a.id = c.article_id
            WHERE MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
              AND a.deleted_at IS NULL AND a.status = 'READY'
              AND c.article_id IN
              <foreach collection="articleIds" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            ORDER BY score DESC
            LIMIT #{limit}
            </script>
            """)
    List<ArticleChunkSearchRow> searchKeywordByArticleIds(
            @Param("query") String query,
            @Param("articleIds") List<Long> articleIds,
            @Param("limit") int limit);

    @Select("""
            SELECT c.id, c.article_id, c.user_id, c.content, c.char_start, c.char_end,
                   MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE) AS score
            FROM article_chunks c
            JOIN articles a ON a.id = c.article_id
            WHERE a.team_id = #{teamId}
              AND a.deleted_at IS NULL AND a.status = 'READY'
              AND MATCH(c.content) AGAINST(#{query} IN NATURAL LANGUAGE MODE)
            ORDER BY score DESC
            LIMIT #{limit}
            """)
    List<ArticleChunkSearchRow> searchKeywordByTeam(
            @Param("teamId") long teamId,
            @Param("query") String query,
            @Param("limit") int limit);
}
