package com.shiye.user.module.article.service;

import com.shiye.user.module.article.domain.entity.ArticleEntity;

/**
 * 团队资料的访问校验：资料必须已共享到团队，且当前用户是团队成员。
 */
public interface ArticleTeamAccessService {

    ArticleEntity requireTeamArticle(long userId, long articleId);
}
