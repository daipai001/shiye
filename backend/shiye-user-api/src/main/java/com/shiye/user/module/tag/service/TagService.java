package com.shiye.user.module.tag.service;

import com.shiye.user.module.tag.domain.vo.TagVO;

import java.util.List;
import java.util.Map;

public interface TagService {

    List<TagVO> list(long userId);

    void replaceArticleTags(long userId, long articleId, List<String> names);

    Map<Long, List<TagVO>> byArticleIds(long userId, List<Long> articleIds);
}
