package com.shiye.user.module.chat.mapper;

import com.shiye.user.module.chat.domain.entity.CitationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CitationMapper {

    @Insert("""
            INSERT INTO citations (message_id, user_id, article_id, chunk_id, `rank`, score, quote)
            VALUES (#{messageId}, #{userId}, #{articleId}, #{chunkId}, #{rank}, #{score}, #{quote})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CitationEntity entity);

    @Select("""
            <script>
            SELECT c.id, c.message_id, c.user_id, c.article_id, c.chunk_id, c.`rank`,
                   c.score, c.quote, c.created_at,
                   a.title, ch.char_start, ch.char_end
            FROM citations c
            LEFT JOIN articles a ON a.id = c.article_id
            LEFT JOIN article_chunks ch ON ch.id = c.chunk_id
            WHERE c.user_id = #{userId}
              AND c.message_id IN
              <foreach collection="messageIds" item="messageId" open="(" separator="," close=")">
                #{messageId}
              </foreach>
            ORDER BY c.message_id ASC, c.`rank` ASC
            </script>
            """)
    List<CitationEntity> findByMessageIds(@Param("userId") long userId,
                                          @Param("messageIds") List<Long> messageIds);
}
