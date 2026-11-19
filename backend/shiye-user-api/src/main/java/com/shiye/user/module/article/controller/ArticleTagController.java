package com.shiye.user.module.article.controller;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.Result;
import com.shiye.common.exception.BizException;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.article.domain.dto.ArticleTagAddRequest;
import com.shiye.user.module.article.domain.entity.ArticleEntity;
import com.shiye.user.module.article.mapper.ArticleMapper;
import com.shiye.user.module.tag.domain.vo.TagVO;
import com.shiye.user.module.tag.service.TagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/articles/{articleId}/tags")
public class ArticleTagController {

    private final ArticleMapper articleMapper;
    private final TagService tagService;

    public ArticleTagController(ArticleMapper articleMapper, TagService tagService) {
        this.articleMapper = articleMapper;
        this.tagService = tagService;
    }

    @PostMapping
    public Result<TagVO> add(@PathVariable long articleId,
                             @Valid @RequestBody ArticleTagAddRequest request) {
        long userId = UserContext.requireUserId();
        requireOwnedArticle(userId, articleId);
        Long tagId = request.tagId() == null || request.tagId().isBlank()
                ? null : Long.parseLong(request.tagId());
        return Result.ok(tagService.addArticleTag(
                userId, articleId, tagId, request.name(), request.color()));
    }

    @DeleteMapping("/{tagId}")
    public Result<Void> remove(@PathVariable long articleId, @PathVariable long tagId) {
        long userId = UserContext.requireUserId();
        requireOwnedArticle(userId, articleId);
        tagService.removeArticleTag(userId, articleId, tagId);
        return Result.ok();
    }

    private void requireOwnedArticle(long userId, long articleId) {
        ArticleEntity article = articleMapper.findByIdAndUser(articleId, userId);
        if (article == null) {
            throw new BizException(ErrorCode.ARTICLE_NOT_FOUND);
        }
    }
}
