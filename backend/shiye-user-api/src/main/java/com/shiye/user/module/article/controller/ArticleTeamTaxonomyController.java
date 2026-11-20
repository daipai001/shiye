package com.shiye.user.module.article.controller;

import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.article.domain.vo.ArticleFolderVO;
import com.shiye.user.module.tag.domain.vo.TagVO;
import com.shiye.user.module.team.domain.dto.ArticleTeamFolderRequest;
import com.shiye.user.module.team.domain.dto.ArticleTeamTagRequest;
import com.shiye.user.module.team.service.TeamTaxonomyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/articles/{articleId}")
public class ArticleTeamTaxonomyController {

    private final TeamTaxonomyService taxonomyService;

    public ArticleTeamTaxonomyController(TeamTaxonomyService taxonomyService) {
        this.taxonomyService = taxonomyService;
    }

    @PostMapping("/team-tags")
    public Result<TagVO> addTag(@PathVariable long articleId,
                                @Valid @RequestBody ArticleTeamTagRequest request) {
        return Result.ok(taxonomyService.addArticleTag(
                UserContext.requireUserId(), articleId, request));
    }

    @DeleteMapping("/team-tags/{tagId}")
    public Result<Void> removeTag(@PathVariable long articleId, @PathVariable long tagId) {
        taxonomyService.removeArticleTag(UserContext.requireUserId(), articleId, tagId);
        return Result.ok();
    }

    @PostMapping("/team-folders")
    public Result<ArticleFolderVO> addFolder(
            @PathVariable long articleId,
            @Valid @RequestBody ArticleTeamFolderRequest request) {
        return Result.ok(taxonomyService.addArticleFolder(
                UserContext.requireUserId(), articleId, request));
    }

    @DeleteMapping("/team-folders/{folderId}")
    public Result<Void> removeFolder(@PathVariable long articleId,
                                     @PathVariable long folderId) {
        taxonomyService.removeArticleFolder(UserContext.requireUserId(), articleId, folderId);
        return Result.ok();
    }
}
