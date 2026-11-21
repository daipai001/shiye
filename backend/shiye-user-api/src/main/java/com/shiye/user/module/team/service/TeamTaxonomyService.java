package com.shiye.user.module.team.service;

import com.shiye.user.module.article.domain.vo.ArticleFolderVO;
import com.shiye.user.module.tag.domain.vo.TagVO;
import com.shiye.user.module.team.domain.dto.ArticleTeamFolderRequest;
import com.shiye.user.module.team.domain.dto.ArticleTeamTagRequest;
import com.shiye.user.module.team.domain.dto.TeamFolderRequest;
import com.shiye.user.module.team.domain.dto.TeamTagRequest;
import com.shiye.user.module.team.domain.vo.TeamTaxonomyOptionsVO;

import java.util.List;
import java.util.Map;

public interface TeamTaxonomyService {

    List<TagVO> tags(long userId, long teamId);

    TagVO createTag(long userId, long teamId, TeamTagRequest request);

    TagVO updateTag(long userId, long teamId, long tagId, TeamTagRequest request);

    void deleteTag(long userId, long teamId, long tagId);

    TagVO addArticleTag(long userId, long articleId, ArticleTeamTagRequest request);

    void removeArticleTag(long userId, long articleId, long tagId);

    List<ArticleFolderVO> folders(long userId, long teamId);

    ArticleFolderVO createFolder(long userId, long teamId, TeamFolderRequest request);

    ArticleFolderVO updateFolder(long userId, long teamId, long folderId,
                                 TeamFolderRequest request);

    void deleteFolder(long userId, long teamId, long folderId);

    ArticleFolderVO addArticleFolder(long userId, long articleId,
                                     ArticleTeamFolderRequest request);

    void removeArticleFolder(long userId, long articleId, long folderId);

    Map<Long, List<TagVO>> tagsByArticleIds(long userId, long teamId, List<Long> articleIds);

    Map<Long, List<ArticleFolderVO>> foldersByArticleIds(long userId, long teamId,
                                                         List<Long> articleIds);

    List<Long> findArticleIdsByTag(long userId, long teamId, long tagId);

    List<Long> findArticleIdsByFolder(long userId, long teamId, long folderId);

    void clearArticleTaxonomy(long teamId, long articleId);

    void deleteTeamTaxonomy(long teamId);

    TeamTaxonomyOptionsVO options(long userId);

    long teamIdForTag(long tagId);

    long teamIdForFolder(long folderId);
}
