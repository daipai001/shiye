package com.shiye.user.module.bookmark.mapper;

import com.shiye.user.module.bookmark.domain.entity.BookmarkFolderEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BookmarkFolderMapper {

    @Select("""
            SELECT id, user_id, browser, folder_id, folder_path, is_primary, auto_sync,
                   last_synced_at, created_at, updated_at
            FROM bookmark_folders
            WHERE user_id = #{userId}
            ORDER BY is_primary DESC, folder_path ASC, id ASC
            """)
    List<BookmarkFolderEntity> findByUserId(@Param("userId") long userId);

    @Select("""
            SELECT id, user_id, browser, folder_id, folder_path, is_primary, auto_sync,
                   last_synced_at, created_at, updated_at
            FROM bookmark_folders
            WHERE id = #{id} AND user_id = #{userId}
            """)
    BookmarkFolderEntity findByIdAndUser(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT id, user_id, browser, folder_id, folder_path, is_primary, auto_sync,
                   last_synced_at, created_at, updated_at
            FROM bookmark_folders
            WHERE user_id = #{userId} AND browser = #{browser} AND folder_id = #{folderId}
            """)
    BookmarkFolderEntity findByBrowserFolder(@Param("userId") long userId,
                                             @Param("browser") String browser,
                                             @Param("folderId") String folderId);

    @Insert("""
            INSERT INTO bookmark_folders (user_id, browser, folder_id, folder_path, is_primary, auto_sync)
            VALUES (#{userId}, #{browser}, #{folderId}, #{folderPath}, #{isPrimary}, #{autoSync})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BookmarkFolderEntity folder);

    @Update("""
            UPDATE bookmark_folders
            SET folder_path = #{folderPath}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int updatePath(@Param("id") long id,
                   @Param("userId") long userId,
                   @Param("folderPath") String folderPath);

    @Update("""
            UPDATE bookmark_folders
            SET is_primary = 0, updated_at = UTC_TIMESTAMP(3)
            WHERE user_id = #{userId} AND is_primary = 1
            """)
    int clearPrimary(@Param("userId") long userId);

    @Update("""
            UPDATE bookmark_folders
            SET is_primary = #{isPrimary}, auto_sync = #{autoSync}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int updateSettings(@Param("id") long id,
                       @Param("userId") long userId,
                       @Param("isPrimary") boolean isPrimary,
                       @Param("autoSync") boolean autoSync);

    @Update("""
            UPDATE bookmark_folders
            SET last_synced_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int markSynced(@Param("id") long id, @Param("userId") long userId);
}
