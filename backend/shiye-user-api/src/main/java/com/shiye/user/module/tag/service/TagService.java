package com.shiye.user.module.tag.service;

import com.shiye.user.module.tag.domain.dto.TagUpdateRequest;
import com.shiye.user.module.tag.domain.vo.TagVO;

import java.util.List;
import java.util.Map;

public interface TagService {

    List<TagVO> list(long userId);

    void replaceArticleTags(long userId, long articleId, List<String> names);

    Map<Long, List<TagVO>> byArticleIds(long userId, List<Long> articleIds);

    TagVO createOrGet(long userId, String name, String color);

    TagVO update(long userId, long tagId, TagUpdateRequest request);

    void delete(long userId, long tagId);

    TagVO addArticleTag(long userId, long articleId, Long tagId, String name, String color);

    void removeArticleTag(long userId, long articleId, long tagId);

    List<Long> findArticleIdsByTag(long userId, long tagId);
}
