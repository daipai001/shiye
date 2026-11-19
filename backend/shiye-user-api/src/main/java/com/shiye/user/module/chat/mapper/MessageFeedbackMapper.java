package com.shiye.user.module.chat.mapper;

import com.shiye.user.module.chat.domain.entity.MessageFeedbackEntity;
import com.shiye.user.module.chat.domain.entity.MessageFeedbackRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageFeedbackMapper {

    @Insert("""
            INSERT INTO message_feedback (message_id, user_id, rating, comment)
            VALUES (#{messageId}, #{userId}, #{rating}, #{comment})
            ON DUPLICATE KEY UPDATE
                rating = VALUES(rating),
                comment = VALUES(comment),
                updated_at = UTC_TIMESTAMP(3)
            """)
    int upsert(MessageFeedbackEntity entity);

    @Select("""
            <script>
            SELECT message_id, rating
            FROM message_feedback
            WHERE user_id = #{userId}
              AND message_id IN
              <foreach collection="messageIds" item="messageId" open="(" separator="," close=")">
                #{messageId}
              </foreach>
            </script>
            """)
    List<MessageFeedbackRow> findByMessageIds(@Param("userId") long userId,
                                              @Param("messageIds") List<Long> messageIds);
}
