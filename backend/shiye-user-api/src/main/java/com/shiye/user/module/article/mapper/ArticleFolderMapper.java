package com.shiye.user.module.article.mapper;

import com.shiye.user.module.article.domain.entity.ArticleFolderRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ArticleFolderMapper {

    @Insert("""
            INSERT IGNORE INTO article_folders (article_id, user_id, bookmark_folder_id, folder_path)
            VALUES (#{articleId}, #{userId}, #{bookmarkFolderId}, #{folderPath})
            """)
    int insertIgnore(@Param("articleId") long articleId,
                     @Param("userId") long userId,
                     @Param("bookmarkFolderId") long bookmarkFolderId,
                     @Param("folderPath") String folderPath);

    @Select("""
            SELECT af.article_id
            FROM article_folders af
            JOIN articles a ON a.id = af.article_id
            WHERE af.user_id = #{userId} AND af.bookmark_folder_id = #{folderId}
              AND a.user_id = #{userId} AND a.deleted_at IS NULL AND a.status = 'READY'
            """)
    List<Long> findArticleIdsByFolder(@Param("userId") long userId,
                                      @Param("folderId") long folderId);

    @Select("""
            <script>
            SELECT article_id, bookmark_folder_id AS folder_id, folder_path
            FROM article_folders
            WHERE user_id = #{userId}
              AND article_id IN
              <foreach collection="articleIds" item="articleId" open="(" separator="," close=")">
                #{articleId}
              </foreach>
            ORDER BY article_id, bookmark_folder_id
            </script>
            """)
    List<ArticleFolderRow> findByArticleIds(@Param("userId") long userId,
                                            @Param("articleIds") List<Long> articleIds);

    @Delete("DELETE FROM article_folders WHERE article_id = #{articleId} AND user_id = #{userId}")
    int deleteByArticle(@Param("articleId") long articleId, @Param("userId") long userId);
}
