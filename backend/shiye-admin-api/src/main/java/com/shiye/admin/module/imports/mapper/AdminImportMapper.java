package com.shiye.admin.module.imports.mapper;

import com.shiye.admin.module.imports.domain.entity.AdminImportItemRowEntity;
import com.shiye.admin.module.imports.domain.entity.AdminImportRowEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminImportMapper {

    @Select("""
            <script>
            SELECT i.id, i.user_id, u.email AS user_email, i.folder_path, i.status,
                   i.total, i.success, i.failed, i.skipped, i.error_code,
                   i.started_at, i.finished_at, i.created_at
            FROM bookmark_imports i
            LEFT JOIN users u ON u.id = i.user_id
            WHERE 1 = 1
            <if test="userId != null"> AND i.user_id = #{userId}</if>
            <if test="status != null"> AND i.status = #{status}</if>
            ORDER BY i.created_at DESC, i.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminImportRowEntity> findPage(@Param("userId") Long userId,
                                        @Param("status") String status,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM bookmark_imports i
            WHERE 1 = 1
            <if test="userId != null"> AND i.user_id = #{userId}</if>
            <if test="status != null"> AND i.status = #{status}</if>
            </script>
            """)
    long count(@Param("userId") Long userId, @Param("status") String status);

    @Select("""
            SELECT i.id, i.user_id, u.email AS user_email, i.folder_path, i.status,
                   i.total, i.success, i.failed, i.skipped, i.error_code,
                   i.started_at, i.finished_at, i.created_at
            FROM bookmark_imports i
            LEFT JOIN users u ON u.id = i.user_id
            WHERE i.id = #{id}
            """)
    AdminImportRowEntity findById(@Param("id") long id);

    @Select("""
            <script>
            SELECT bi.id, bi.user_id, u.email AS user_email, bi.title, bi.url,
                   bi.folder_path, bi.status, bi.article_id, bi.error_code,
                   bi.error_message, bi.updated_at
            FROM bookmark_items bi
            LEFT JOIN users u ON u.id = bi.user_id
            WHERE bi.import_id = #{importId}
            <if test="status != null"> AND bi.status = #{status}</if>
            ORDER BY bi.id ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<AdminImportItemRowEntity> findItems(@Param("importId") long importId,
                                             @Param("status") String status,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM bookmark_items
            WHERE import_id = #{importId}
            <if test="status != null"> AND status = #{status}</if>
            </script>
            """)
    long countItems(@Param("importId") long importId, @Param("status") String status);

    @Update("""
            UPDATE bookmark_imports
            SET status = #{status}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int updateStatus(@Param("id") long id, @Param("status") String status);

    @Update("""
            UPDATE bookmark_imports
            SET status = 'RUNNING',
                started_at = COALESCE(started_at, UTC_TIMESTAMP(3)),
                finished_at = NULL,
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int markRunning(@Param("id") long id);

    @Update("""
            UPDATE bookmark_imports
            SET status = #{status}, finished_at = UTC_TIMESTAMP(3),
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int markFinished(@Param("id") long id, @Param("status") String status);

    @Update("""
            UPDATE bookmark_imports
            SET status = 'PENDING', finished_at = NULL, error_code = NULL,
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id}
            """)
    int markPending(@Param("id") long id);

    @Update("""
            UPDATE bookmark_imports i
            SET i.total = (SELECT COUNT(*) FROM bookmark_items WHERE import_id = i.id),
                i.success = (SELECT COUNT(*) FROM bookmark_items WHERE import_id = i.id AND status = 'READY'),
                i.failed = (SELECT COUNT(*) FROM bookmark_items WHERE import_id = i.id AND status = 'FAILED'),
                i.skipped = (SELECT COUNT(*) FROM bookmark_items WHERE import_id = i.id AND status = 'SKIPPED'),
                i.updated_at = UTC_TIMESTAMP(3)
            WHERE i.id = #{id}
            """)
    int refreshCounts(@Param("id") long id);

    @Update("""
            <script>
            UPDATE bookmark_items
            SET status = 'PENDING', error_code = NULL, error_message = NULL,
                updated_at = UTC_TIMESTAMP(3)
            WHERE import_id = #{importId} AND status = 'FAILED'
            <if test="itemIds != null and itemIds.size() > 0">
              AND id IN
              <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
                #{itemId}
              </foreach>
            </if>
            </script>
            """)
    int retryFailed(@Param("importId") long importId,
                    @Param("itemIds") List<Long> itemIds);

    @Update("""
            UPDATE bookmark_items
            SET status = 'SKIPPED', error_code = 'CANCELLED', error_message = '管理员取消导入',
                updated_at = UTC_TIMESTAMP(3)
            WHERE import_id = #{importId}
              AND status IN ('PENDING', 'FETCHING', 'PARSING', 'CHUNKING', 'EMBEDDING')
            """)
    int cancelPending(@Param("importId") long importId);
}
