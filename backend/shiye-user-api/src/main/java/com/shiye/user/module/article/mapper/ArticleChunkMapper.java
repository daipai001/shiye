package com.shiye.user.module.article.mapper;

import com.shiye.user.module.article.domain.entity.ArticleChunkEntity;
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
            SET vector_id = #{vectorId}
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int updateVectorId(@Param("id") long id,
                       @Param("userId") long userId,
                       @Param("vectorId") String vectorId);

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
}
