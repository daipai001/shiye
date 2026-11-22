package com.shiye.user.module.chat.mapper;

import com.shiye.user.module.chat.domain.entity.ConversationEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ConversationMapper {

    @Insert("""
            INSERT INTO conversations (user_id, title, scope_type, scope_id, model)
            VALUES (#{userId}, #{title}, #{scopeType}, #{scopeId}, #{model})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ConversationEntity entity);

    @Select("""
            SELECT c.id, c.user_id, c.title, c.scope_type, c.scope_id, c.model,
                   c.created_at, c.updated_at, c.deleted_at
            FROM conversations c
            WHERE c.id = #{id} AND c.user_id = #{userId} AND c.deleted_at IS NULL
              AND (
                COALESCE(c.scope_type, 'ALL') NOT IN ('TEAM', 'TEAM_TAG', 'TEAM_FOLDER')
                OR (c.scope_type = 'TEAM' AND EXISTS (
                    SELECT 1 FROM team_members tm
                    WHERE tm.team_id = c.scope_id AND tm.user_id = c.user_id))
                OR (c.scope_type = 'TEAM_TAG' AND EXISTS (
                    SELECT 1 FROM team_tags tt
                    JOIN team_members tm ON tm.team_id = tt.team_id
                    WHERE tt.id = c.scope_id AND tm.user_id = c.user_id))
                OR (c.scope_type = 'TEAM_FOLDER' AND EXISTS (
                    SELECT 1 FROM team_folders tf
                    JOIN team_members tm ON tm.team_id = tf.team_id
                    WHERE tf.id = c.scope_id AND tm.user_id = c.user_id))
              )
            """)
    ConversationEntity findByIdAndUser(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT c.id, c.user_id, c.title, c.scope_type, c.scope_id, c.model,
                   c.created_at, c.updated_at, c.deleted_at,
                   (SELECT m.content FROM messages m
                    WHERE m.conversation_id = c.id
                    ORDER BY m.id DESC LIMIT 1) AS last_message
            FROM conversations c
            WHERE c.user_id = #{userId} AND c.deleted_at IS NULL
              AND (
                COALESCE(c.scope_type, 'ALL') NOT IN ('TEAM', 'TEAM_TAG', 'TEAM_FOLDER')
                OR (c.scope_type = 'TEAM' AND EXISTS (
                    SELECT 1 FROM team_members tm
                    WHERE tm.team_id = c.scope_id AND tm.user_id = c.user_id))
                OR (c.scope_type = 'TEAM_TAG' AND EXISTS (
                    SELECT 1 FROM team_tags tt
                    JOIN team_members tm ON tm.team_id = tt.team_id
                    WHERE tt.id = c.scope_id AND tm.user_id = c.user_id))
                OR (c.scope_type = 'TEAM_FOLDER' AND EXISTS (
                    SELECT 1 FROM team_folders tf
                    JOIN team_members tm ON tm.team_id = tf.team_id
                    WHERE tf.id = c.scope_id AND tm.user_id = c.user_id))
              )
            ORDER BY c.updated_at DESC, c.id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<ConversationEntity> findPage(@Param("userId") long userId,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM conversations c
            WHERE c.user_id = #{userId} AND c.deleted_at IS NULL
              AND (
                COALESCE(c.scope_type, 'ALL') NOT IN ('TEAM', 'TEAM_TAG', 'TEAM_FOLDER')
                OR (c.scope_type = 'TEAM' AND EXISTS (
                    SELECT 1 FROM team_members tm
                    WHERE tm.team_id = c.scope_id AND tm.user_id = c.user_id))
                OR (c.scope_type = 'TEAM_TAG' AND EXISTS (
                    SELECT 1 FROM team_tags tt
                    JOIN team_members tm ON tm.team_id = tt.team_id
                    WHERE tt.id = c.scope_id AND tm.user_id = c.user_id))
                OR (c.scope_type = 'TEAM_FOLDER' AND EXISTS (
                    SELECT 1 FROM team_folders tf
                    JOIN team_members tm ON tm.team_id = tf.team_id
                    WHERE tf.id = c.scope_id AND tm.user_id = c.user_id))
              )
            """)
    long count(@Param("userId") long userId);

    @Update("""
            UPDATE conversations
            SET updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int touch(@Param("id") long id, @Param("userId") long userId);

    @Update("""
            UPDATE conversations
            SET title = #{title}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateTitle(@Param("id") long id,
                    @Param("userId") long userId,
                    @Param("title") String title);

    @Update("""
            UPDATE conversations
            SET deleted_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("id") long id, @Param("userId") long userId);
}
