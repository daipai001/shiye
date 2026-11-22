package com.shiye.user.module.article.mapper;

import com.shiye.user.module.article.domain.entity.ArticleEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ArticleMapper {

    @Select("""
            SELECT id, user_id, team_id, title, url, url_hash, domain, source_type, author,
                   published_at, fetched_at, summary, content_text, content_hash,
                   word_count, language, status, file_asset_id, created_at, updated_at, deleted_at
            FROM articles
            WHERE user_id = #{userId} AND url_hash = #{urlHash} AND deleted_at IS NULL
            LIMIT 1
            """)
    ArticleEntity findByUserAndUrlHash(@Param("userId") long userId,
                                       @Param("urlHash") String urlHash);

    @Select("""
            SELECT id, user_id, team_id, title, url, url_hash, domain, source_type, author,
                   published_at, fetched_at, summary, content_text, content_hash,
                   word_count, language, status, file_asset_id, created_at, updated_at, deleted_at
            FROM articles
            WHERE user_id = #{userId} AND url_hash = #{urlHash}
            LIMIT 1
            """)
    ArticleEntity findByUserAndUrlHashIncludingDeleted(@Param("userId") long userId,
                                                       @Param("urlHash") String urlHash);

    @Select("""
            SELECT id, user_id, team_id, title, url, url_hash, domain, source_type, author,
                   published_at, fetched_at, summary, content_text, content_hash,
                   word_count, language, status, file_asset_id, created_at, updated_at, deleted_at
            FROM articles
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    ArticleEntity findByIdAndUser(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT id, user_id, team_id, title, url, url_hash, domain, source_type, author,
                   published_at, fetched_at, summary, content_text, content_hash,
                   word_count, language, status, file_asset_id, created_at, updated_at, deleted_at
            FROM articles
            WHERE user_id = #{userId} AND file_asset_id = #{fileAssetId} AND deleted_at IS NULL
            LIMIT 1
            """)
    ArticleEntity findByFileAsset(@Param("userId") long userId,
                                  @Param("fileAssetId") long fileAssetId);

    @Insert("""
            INSERT INTO articles (user_id, title, url, url_hash, domain, source_type, status,
                                  file_asset_id)
            VALUES (#{userId}, #{title}, #{url}, #{urlHash}, #{domain}, #{sourceType}, #{status},
                    #{fileAssetId})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ArticleEntity article);

    @Update("""
            UPDATE articles
            SET title = #{title},
                author = #{author},
                summary = #{summary},
                content_text = #{contentText},
                content_hash = #{contentHash},
                word_count = #{wordCount},
                language = #{language},
                fetched_at = #{fetchedAt},
                status = #{status},
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateParsed(@Param("id") long id,
                     @Param("userId") long userId,
                     @Param("title") String title,
                     @Param("author") String author,
                     @Param("summary") String summary,
                     @Param("contentText") String contentText,
                     @Param("contentHash") String contentHash,
                     @Param("wordCount") int wordCount,
                     @Param("language") String language,
                     @Param("fetchedAt") LocalDateTime fetchedAt,
                     @Param("status") String status);

    @Update("""
            UPDATE articles
            SET status = #{status}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateStatus(@Param("id") long id,
                     @Param("userId") long userId,
                     @Param("status") String status);

    @Update("""
            UPDATE articles
            SET summary = #{summary}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int updateSummary(@Param("id") long id,
                      @Param("userId") long userId,
                      @Param("summary") String summary);

    @Select("""
            <script>
            SELECT a.id, a.user_id, a.team_id, a.title, a.url, a.url_hash, a.domain, a.source_type, a.author,
                   a.published_at, a.fetched_at, a.summary, a.content_hash, a.word_count,
                   a.language, a.status, a.file_asset_id, a.created_at, a.updated_at, a.deleted_at,
                   (SELECT COUNT(*) FROM article_chunks c
                    WHERE c.article_id = a.id AND c.user_id = a.user_id) AS chunk_count
            FROM articles a
            WHERE a.user_id = #{userId} AND a.deleted_at IS NULL
            <if test="status != null"> AND a.status = #{status}</if>
            <if test="sourceType != null"> AND a.source_type = #{sourceType}</if>
            <if test="q != null and q != ''">
              AND (a.title LIKE CONCAT('%', #{q}, '%') OR a.summary LIKE CONCAT('%', #{q}, '%'))
            </if>
            <if test="tagId != null">
              AND EXISTS (SELECT 1 FROM article_tags at
                          WHERE at.article_id = a.id AND at.user_id = a.user_id
                            AND at.tag_id = #{tagId})
            </if>
            <if test="folderId != null">
              AND EXISTS (SELECT 1 FROM article_folders af
                          WHERE af.article_id = a.id AND af.user_id = a.user_id
                            AND af.bookmark_folder_id = #{folderId})
            </if>
            ORDER BY a.updated_at DESC, a.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ArticleEntity> findPage(@Param("userId") long userId,
                                 @Param("q") String q,
                                 @Param("status") String status,
                                 @Param("sourceType") String sourceType,
                                 @Param("tagId") Long tagId,
                                 @Param("folderId") Long folderId,
                                 @Param("offset") int offset,
                                 @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM articles a
            WHERE a.user_id = #{userId} AND a.deleted_at IS NULL
            <if test="status != null"> AND a.status = #{status}</if>
            <if test="sourceType != null"> AND a.source_type = #{sourceType}</if>
            <if test="q != null and q != ''">
              AND (a.title LIKE CONCAT('%', #{q}, '%') OR a.summary LIKE CONCAT('%', #{q}, '%'))
            </if>
            <if test="tagId != null">
              AND EXISTS (SELECT 1 FROM article_tags at
                          WHERE at.article_id = a.id AND at.user_id = a.user_id
                            AND at.tag_id = #{tagId})
            </if>
            <if test="folderId != null">
              AND EXISTS (SELECT 1 FROM article_folders af
                          WHERE af.article_id = a.id AND af.user_id = a.user_id
                            AND af.bookmark_folder_id = #{folderId})
            </if>
            </script>
            """)
    long count(@Param("userId") long userId,
               @Param("q") String q,
               @Param("status") String status,
               @Param("sourceType") String sourceType,
               @Param("tagId") Long tagId,
               @Param("folderId") Long folderId);

    @Select("""
            SELECT COUNT(*)
            FROM articles
            WHERE user_id = #{userId} AND deleted_at IS NULL
              AND status IN ('PENDING', 'PARSING', 'CHUNKING', 'EMBEDDING')
            """)
    long countProcessing(@Param("userId") long userId);

    @Select("""
            <script>
            SELECT id, user_id, team_id, title, url, url_hash, domain, source_type, author,
                   published_at, fetched_at, summary, content_text, content_hash,
                   word_count, language, status, file_asset_id, created_at, updated_at, deleted_at
            FROM articles
            WHERE user_id = #{userId} AND deleted_at IS NULL AND status = 'READY'
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    List<ArticleEntity> findByIdsAndUser(@Param("userId") long userId,
                                         @Param("ids") List<Long> ids);

    @Update("""
            UPDATE articles
            SET deleted_at = NULL,
                title = #{title},
                source_type = #{sourceType},
                status = 'PENDING',
                updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId}
            """)
    int reactivate(@Param("id") long id,
                   @Param("userId") long userId,
                   @Param("title") String title,
                   @Param("sourceType") String sourceType);

    @Update("""
            UPDATE articles
            SET deleted_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int softDelete(@Param("id") long id, @Param("userId") long userId);

    @Select("""
            SELECT id, user_id, team_id, title, url, url_hash, domain, source_type, author,
                   published_at, fetched_at, summary, content_text, content_hash,
                   word_count, language, status, file_asset_id, created_at, updated_at, deleted_at
            FROM articles
            WHERE id = #{id} AND deleted_at IS NULL
            """)
    ArticleEntity findById(@Param("id") long id);

    @Select("""
            <script>
            SELECT id, user_id, team_id, title, url, url_hash, domain, source_type, author,
                   published_at, fetched_at, summary, content_text, content_hash,
                   word_count, language, status, file_asset_id, created_at, updated_at, deleted_at
            FROM articles
            WHERE team_id = #{teamId} AND deleted_at IS NULL AND status = 'READY'
              AND id IN
              <foreach collection="ids" item="id" open="(" separator="," close=")">
                #{id}
              </foreach>
            </script>
            """)
    List<ArticleEntity> findByIdsAndTeam(@Param("teamId") long teamId,
                                         @Param("ids") List<Long> ids);

    @Select("""
            <script>
            SELECT id, user_id, team_id, title, url, url_hash, domain, source_type, author,
                   published_at, fetched_at, summary, content_hash, word_count,
                   language, status, file_asset_id, created_at, updated_at, deleted_at
            FROM articles
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
              AND deleted_at IS NULL
            </script>
            """)
    List<ArticleEntity> findByIds(@Param("ids") List<Long> ids);

    @Select("""
            SELECT id
            FROM articles
            WHERE team_id = #{teamId} AND deleted_at IS NULL AND status = 'READY'
            ORDER BY id DESC
            """)
    List<Long> findReadyIdsByTeam(@Param("teamId") long teamId);

    @Select("""
            <script>
            SELECT a.id, a.user_id, a.team_id, a.title, a.url, a.url_hash, a.domain,
                   a.source_type, a.author, a.published_at, a.fetched_at, a.summary,
                   a.content_hash, a.word_count, a.language, a.status, a.file_asset_id,
                   a.created_at, a.updated_at, a.deleted_at,
                   (SELECT COUNT(*) FROM article_chunks c
                    WHERE c.article_id = a.id AND c.user_id = a.user_id) AS chunk_count
            FROM articles a
            WHERE a.team_id = #{teamId} AND a.deleted_at IS NULL
            <if test="status != null"> AND a.status = #{status}</if>
            <if test="sourceType != null"> AND a.source_type = #{sourceType}</if>
            <if test="q != null and q != ''">
              AND (a.title LIKE CONCAT('%', #{q}, '%') OR a.summary LIKE CONCAT('%', #{q}, '%'))
            </if>
            <if test="tagId != null">
              AND EXISTS (SELECT 1 FROM article_team_tags att
                          WHERE att.article_id = a.id AND att.team_id = a.team_id
                            AND att.tag_id = #{tagId})
            </if>
            <if test="folderId != null">
              AND EXISTS (SELECT 1 FROM article_team_folders atf
                          WHERE atf.article_id = a.id AND atf.team_id = a.team_id
                            AND atf.folder_id = #{folderId})
            </if>
            ORDER BY a.updated_at DESC, a.id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<ArticleEntity> findPageByTeam(@Param("teamId") long teamId,
                                       @Param("q") String q,
                                       @Param("status") String status,
                                       @Param("sourceType") String sourceType,
                                       @Param("tagId") Long tagId,
                                       @Param("folderId") Long folderId,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*)
            FROM articles a
            WHERE a.team_id = #{teamId} AND a.deleted_at IS NULL
            <if test="status != null"> AND a.status = #{status}</if>
            <if test="sourceType != null"> AND a.source_type = #{sourceType}</if>
            <if test="q != null and q != ''">
              AND (a.title LIKE CONCAT('%', #{q}, '%') OR a.summary LIKE CONCAT('%', #{q}, '%'))
            </if>
            <if test="tagId != null">
              AND EXISTS (SELECT 1 FROM article_team_tags att
                          WHERE att.article_id = a.id AND att.team_id = a.team_id
                            AND att.tag_id = #{tagId})
            </if>
            <if test="folderId != null">
              AND EXISTS (SELECT 1 FROM article_team_folders atf
                          WHERE atf.article_id = a.id AND atf.team_id = a.team_id
                            AND atf.folder_id = #{folderId})
            </if>
            </script>
            """)
    long countByTeam(@Param("teamId") long teamId,
                     @Param("q") String q,
                     @Param("status") String status,
                     @Param("sourceType") String sourceType,
                     @Param("tagId") Long tagId,
                     @Param("folderId") Long folderId);

    @Select("""
            SELECT COUNT(*)
            FROM articles
            WHERE team_id = #{teamId} AND deleted_at IS NULL
              AND status IN ('PENDING', 'PARSING', 'CHUNKING', 'EMBEDDING')
            """)
    long countProcessingByTeam(@Param("teamId") long teamId);

    @Update("""
            UPDATE articles
            SET team_id = #{teamId}, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND user_id = #{userId} AND deleted_at IS NULL
            """)
    int linkTeam(@Param("id") long id,
                 @Param("userId") long userId,
                 @Param("teamId") long teamId);

    @Update("""
            UPDATE articles
            SET team_id = NULL, updated_at = UTC_TIMESTAMP(3)
            WHERE id = #{id} AND team_id = #{teamId} AND deleted_at IS NULL
            """)
    int unlinkTeam(@Param("id") long id, @Param("teamId") long teamId);

    @Select("SELECT team_id FROM articles WHERE id = #{id}")
    Long findTeamId(@Param("id") long id);

    @Update("""
            UPDATE articles
            SET team_id = NULL, updated_at = UTC_TIMESTAMP(3)
            WHERE team_id = #{teamId} AND deleted_at IS NULL
            """)
    int unlinkAllFromTeam(@Param("teamId") long teamId);
}
