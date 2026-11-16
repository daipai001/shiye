package com.shiye.user.module.bookmark.mapper;

import com.shiye.user.module.bookmark.domain.entity.BookmarkItemEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BookmarkItemMapper {

    @Insert("""
            <script>
            INSERT INTO bookmark_items (import_id, user_id, title, url, url_hash,
                                        browser_folder_id, folder_path, status)
            VALUES
            <foreach collection="items" item="item" separator=",">
                (#{item.importId}, #{item.userId}, #{item.title}, #{item.url}, #{item.urlHash},
                 #{item.browserFolderId}, #{item.folderPath}, #{item.status})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("items") List<BookmarkItemEntity> items);

    @Select("""
            <script>
            SELECT id, import_id, user_id, title, url, url_hash, browser_folder_id, folder_path,
                   status, article_id, error_code, error_message, created_at, updated_at
            FROM bookmark_items
            WHERE import_id = #{importId} AND user_id = #{userId}
            <if test="status != null"> AND status = #{status}</if>
            ORDER BY id ASC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<BookmarkItemEntity> findPage(@Param("importId") long importId,
                                      @Param("userId") long userId,
                                      @Param("status") String status,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM bookmark_items
            WHERE import_id = #{importId} AND user_id = #{userId}
            <if test="status != null"> AND status = #{status}</if>
            </script>
            """)
    long count(@Param("importId") long importId,
               @Param("userId") long userId,
               @Param("status") String status);

    @Select("""
            SELECT url_hash
            FROM bookmark_items
            WHERE import_id = #{importId} AND user_id = #{userId}
            """)
    List<String> findUrlHashes(@Param("importId") long importId, @Param("userId") long userId);

    @Update("""
            <script>
            UPDATE bookmark_items
            SET status = 'PENDING', error_code = NULL, error_message = NULL,
                updated_at = UTC_TIMESTAMP(3)
            WHERE import_id = #{importId} AND user_id = #{userId} AND status = 'FAILED'
            <if test="itemIds != null and itemIds.size() > 0">
              AND id IN
              <foreach collection="itemIds" item="itemId" open="(" separator="," close=")">
                #{itemId}
              </foreach>
            </if>
            </script>
            """)
    int retryFailed(@Param("importId") long importId,
                    @Param("userId") long userId,
                    @Param("itemIds") List<Long> itemIds);

    @Update("""
            UPDATE bookmark_items
            SET status = 'SKIPPED', error_code = 'CANCELLED', error_message = '导入已取消',
                updated_at = UTC_TIMESTAMP(3)
            WHERE import_id = #{importId} AND user_id = #{userId}
              AND status IN ('PENDING', 'FETCHING', 'PARSING', 'CHUNKING', 'EMBEDDING')
            """)
    int cancelPending(@Param("importId") long importId, @Param("userId") long userId);

    @Select("""
            SELECT id, import_id, user_id, title, url, url_hash, browser_folder_id, folder_path,
                   status, article_id, error_code, error_message, created_at, updated_at
            FROM bookmark_items
            WHERE import_id = #{importId} AND user_id = #{userId} AND status = 'PENDING'
            ORDER BY id ASC
            LIMIT 1
            """)
    BookmarkItemEntity findNextPending(@Param("importId") long importId,
                                      @Param("userId") long userId);

    @Select("""
            SELECT COUNT(*)
            FROM bookmark_items
            WHERE import_id = #{importId} AND user_id = #{userId} AND status = 'PENDING'
            """)
    long countPending(@Param("importId") long importId, @Param("userId") long userId);

    @Update("""
            UPDATE bookmark_items
            SET status = #{status}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int updateStatus(@Param("id") long id,
                     @Param("userId") long userId,
                     @Param("status") String status);

    @Update("""
            UPDATE bookmark_items
            SET status = 'READY', article_id = #{articleId}, error_code = NULL,
                error_message = NULL, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int markReady(@Param("id") long id,
                  @Param("userId") long userId,
                  @Param("articleId") long articleId);

    @Update("""
            UPDATE bookmark_items
            SET status = 'FAILED', error_code = #{errorCode}, error_message = #{errorMessage},
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int markFailed(@Param("id") long id,
                   @Param("userId") long userId,
                   @Param("errorCode") String errorCode,
                   @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE bookmark_items
            SET status = 'SKIPPED', article_id = #{articleId}, error_code = #{errorCode},
                error_message = #{errorMessage}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int markSkipped(@Param("id") long id,
                    @Param("userId") long userId,
                    @Param("articleId") Long articleId,
                    @Param("errorCode") String errorCode,
                    @Param("errorMessage") String errorMessage);

    @Update("""
            UPDATE bookmark_items
            SET status = 'PENDING', updated_at = UTC_TIMESTAMP(3)
            WHERE status IN ('FETCHING', 'PARSING', 'CHUNKING', 'EMBEDDING')
              AND updated_at < DATE_SUB(UTC_TIMESTAMP(3), INTERVAL 10 MINUTE)
            """)
    int resetStaleProcessing();
}
