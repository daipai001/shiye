package com.shiye.user.module.article.mapper;

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
            SELECT article_id
            FROM article_folders
            WHERE user_id = #{userId} AND bookmark_folder_id = #{folderId}
            """)
    List<Long> findArticleIdsByFolder(@Param("userId") long userId,
                                      @Param("folderId") long folderId);
}
