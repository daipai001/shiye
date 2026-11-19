package com.shiye.admin.module.tasks.mapper;

import com.shiye.admin.module.tasks.domain.entity.AdminTaskRowEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminTaskMapper {

    @Select("""
            <script>
            SELECT bi.id, bi.import_id, bi.user_id, u.email AS user_email, bi.title, bi.url,
                   bi.status, bi.article_id, bi.error_code, bi.error_message,
                   bi.created_at, bi.updated_at
            FROM bookmark_items bi
            LEFT JOIN users u ON u.id = bi.user_id
            WHERE 1 = 1
            <if test="userId != null"> AND bi.user_id = #{userId}</if>
            <if test="status != null"> AND bi.status = #{status}</if>
            ORDER BY bi.updated_at DESC, bi.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminTaskRowEntity> findPage(@Param("userId") Long userId,
                                      @Param("status") String status,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM bookmark_items bi
            WHERE 1 = 1
            <if test="userId != null"> AND bi.user_id = #{userId}</if>
            <if test="status != null"> AND bi.status = #{status}</if>
            </script>
            """)
    long count(@Param("userId") Long userId, @Param("status") String status);

    @Select("""
            SELECT bi.id, bi.import_id, bi.user_id, u.email AS user_email, bi.title, bi.url,
                   bi.status, bi.article_id, bi.error_code, bi.error_message,
                   bi.created_at, bi.updated_at
            FROM bookmark_items bi
            LEFT JOIN users u ON u.id = bi.user_id
            WHERE bi.id = #{id}
            """)
    AdminTaskRowEntity findById(@Param("id") long id);

    @Update("""
            UPDATE bookmark_items
            SET status = 'PENDING', error_code = NULL, error_message = NULL,
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND status = 'FAILED'
            """)
    int retryFailed(@Param("id") long id);

    @Update("""
            UPDATE bookmark_imports
            SET status = 'PENDING', finished_at = NULL, error_code = NULL,
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{importId} AND status IN ('DONE', 'FAILED', 'CANCELLED')
            """)
    int reopenImport(@Param("importId") long importId);
}
