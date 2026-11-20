package com.shiye.user.module.article.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.module.article.domain.entity.ArticleEntity;
import com.shiye.user.module.article.mapper.ArticleMapper;
import com.shiye.user.module.article.service.ArticleTeamAccessService;
import com.shiye.user.module.team.service.TeamService;
import org.springframework.stereotype.Service;

@Service
public class ArticleTeamAccessServiceImpl implements ArticleTeamAccessService {

    private final ArticleMapper articleMapper;
    private final TeamService teamService;

    public ArticleTeamAccessServiceImpl(ArticleMapper articleMapper, TeamService teamService) {
        this.articleMapper = articleMapper;
        this.teamService = teamService;
    }

    @Override
    public ArticleEntity requireTeamArticle(long userId, long articleId) {
        ArticleEntity article = articleMapper.findById(articleId);
        if (article == null || article.getTeamId() == null) {
            throw new BizException(ErrorCode.ARTICLE_NOT_FOUND);
        }
        teamService.requireMember(userId, article.getTeamId());
        return article;
    }
}
