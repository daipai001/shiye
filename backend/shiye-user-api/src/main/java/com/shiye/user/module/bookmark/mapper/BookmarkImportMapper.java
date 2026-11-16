package com.shiye.user.module.bookmark.mapper;

import com.shiye.user.module.bookmark.domain.entity.BookmarkImportEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BookmarkImportMapper {

    @Insert("""
            INSERT INTO bookmark_imports (user_id, bookmark_folder_id, browser, folder_id,
                                          folder_path, `recursive`, status, total)
            VALUES (#{userId}, #{bookmarkFolderId}, #{browser}, #{folderId},
                    #{folderPath}, #{recursive}, #{status}, #{total})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BookmarkImportEntity entity);

    @Select("""
            SELECT id, user_id, bookmark_folder_id, browser, folder_id, folder_path, `recursive`,
                   status, total, success, failed, skipped, error_code,
                   started_at, finished_at, created_at, updated_at
            FROM bookmark_imports
            WHERE id = #{id} AND user_id = #{userId}
            """)
    BookmarkImportEntity findByIdAndUser(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT id, user_id, bookmark_folder_id, browser, folder_id, folder_path, `recursive`,
                   status, total, success, failed, skipped, error_code,
                   started_at, finished_at, created_at, updated_at
            FROM bookmark_imports
            WHERE user_id = #{userId}
              AND bookmark_folder_id = #{bookmarkFolderId}
              AND status IN ('PENDING', 'RUNNING', 'PAUSED')
            ORDER BY id DESC
            LIMIT 1
            """)
    BookmarkImportEntity findActiveByFolder(@Param("userId") long userId,
                                            @Param("bookmarkFolderId") long bookmarkFolderId);

    @Select("""
            <script>
            SELECT id, user_id, bookmark_folder_id, browser, folder_id, folder_path, `recursive`,
                   status, total, success, failed, skipped, error_code,
                   started_at, finished_at, created_at, updated_at
            FROM bookmark_imports
            WHERE user_id = #{userId}
            <if test="status != null"> AND status = #{status}</if>
            ORDER BY created_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<BookmarkImportEntity> findPage(@Param("userId") long userId,
                                        @Param("status") String status,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM bookmark_imports
            WHERE user_id = #{userId}
            <if test="status != null"> AND status = #{status}</if>
            </script>
            """)
    long count(@Param("userId") long userId, @Param("status") String status);

    @Select("""
            SELECT id, user_id, bookmark_folder_id, browser, folder_id, folder_path, `recursive`,
                   status, total, success, failed, skipped, error_code,
                   started_at, finished_at, created_at, updated_at
            FROM bookmark_imports
            WHERE status IN ('PENDING', 'RUNNING')
            ORDER BY id ASC
            LIMIT 1
            """)
    BookmarkImportEntity findRunnableImport();

    @Update("""
            UPDATE bookmark_imports
            SET status = #{status}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int updateStatus(@Param("id") long id,
                     @Param("userId") long userId,
                     @Param("status") String status);

    @Update("""
            UPDATE bookmark_imports
            SET status = 'RUNNING',
                started_at = COALESCE(started_at, UTC_TIMESTAMP(3)),
                finished_at = NULL,
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int markRunning(@Param("id") long id, @Param("userId") long userId);

    @Update("""
            UPDATE bookmark_imports
            SET status = #{status},
                finished_at = UTC_TIMESTAMP(3),
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int markFinished(@Param("id") long id,
                     @Param("userId") long userId,
                     @Param("status") String status);

    @Update("""
            UPDATE bookmark_imports
            SET status = 'PENDING',
                finished_at = NULL,
                error_code = NULL,
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int markPending(@Param("id") long id, @Param("userId") long userId);

    @Update("""
            UPDATE bookmark_imports i
            SET i.total = (SELECT COUNT(*) FROM bookmark_items WHERE import_id = i.id),
                i.success = (SELECT COUNT(*) FROM bookmark_items WHERE import_id = i.id AND status = 'READY'),
                i.failed = (SELECT COUNT(*) FROM bookmark_items WHERE import_id = i.id AND status = 'FAILED'),
                i.skipped = (SELECT COUNT(*) FROM bookmark_items WHERE import_id = i.id AND status = 'SKIPPED'),
                i.updated_at = UTC_TIMESTAMP(3)
            WHERE i.id = #{id} AND i.user_id = #{userId}
            """)
    int refreshCounts(@Param("id") long id, @Param("userId") long userId);
}
