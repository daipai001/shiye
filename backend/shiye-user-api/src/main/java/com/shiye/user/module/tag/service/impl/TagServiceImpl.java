package com.shiye.user.module.tag.service.impl;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;
import com.shiye.user.module.tag.domain.dto.TagUpdateRequest;
import com.shiye.user.module.tag.domain.entity.TagEntity;
import com.shiye.user.module.tag.domain.entity.TagRow;
import com.shiye.user.module.tag.domain.vo.TagVO;
import com.shiye.user.module.tag.mapper.TagMapper;
import com.shiye.user.module.tag.service.TagService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class TagServiceImpl implements TagService {

    private static final int MAX_TAG_NAME_CHARS = 64;
    private static final String[] COLORS = {
            "#3b82f6", "#10b981", "#f59e0b", "#8b5cf6", "#ef4444", "#06b6d4"
    };

    private final TagMapper tagMapper;

    public TagServiceImpl(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @Override
    public List<TagVO> list(long userId) {
        return tagMapper.listWithCounts(userId).stream()
                .map(TagVO::fromRow)
                .toList();
    }

    @Override
    @Transactional
    public void replaceArticleTags(long userId, long articleId, List<String> names) {
        tagMapper.deleteArticleTags(articleId, userId);
        if (names == null || names.isEmpty()) {
            return;
        }
        Set<String> seen = new HashSet<>();
        for (String raw : names) {
            String name = normalizeName(raw);
            if (name == null) {
                continue;
            }
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized)) {
                continue;
            }
            TagEntity tag = tagMapper.findByNormalizedName(userId, normalized);
            if (tag == null) {
                tag = createTag(userId, name, normalized, null);
            }
            if (tag != null && tag.getId() != null) {
                tagMapper.insertArticleTag(articleId, tag.getId(), userId);
            }
        }
    }

    @Override
    public Map<Long, List<TagVO>> byArticleIds(long userId, List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<TagVO>> grouped = new LinkedHashMap<>();
        for (TagRow row : tagMapper.findByArticleIds(userId, articleIds)) {
            grouped.computeIfAbsent(row.getArticleId(), key -> new ArrayList<>())
                    .add(TagVO.fromRow(row));
        }
        return grouped;
    }

    @Override
    @Transactional
    public TagVO createOrGet(long userId, String name, String color) {
        String normalizedName = normalizeName(name);
        if (normalizedName == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "标签名不能为空");
        }
        String normalized = normalizedName.toLowerCase(Locale.ROOT);
        TagEntity existing = tagMapper.findByNormalizedName(userId, normalized);
        if (existing != null) {
            return TagVO.fromEntity(existing);
        }
        TagEntity tag = createTag(userId, normalizedName, normalized, color);
        if (tag == null) {
            throw new BizException(ErrorCode.CONFLICT, "标签创建失败");
        }
        return TagVO.fromEntity(tag);
    }

    @Override
    @Transactional
    public TagVO update(long userId, long tagId, TagUpdateRequest request) {
        TagEntity tag = tagMapper.findByIdAndUser(tagId, userId);
        if (tag == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        String name = request.name() == null ? tag.getName() : normalizeName(request.name());
        if (name == null) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "标签名不能为空");
        }
        String normalized = name.toLowerCase(Locale.ROOT);
        TagEntity sameName = tagMapper.findByNormalizedName(userId, normalized);
        if (sameName != null && !sameName.getId().equals(tagId)) {
            throw new BizException(ErrorCode.CONFLICT, "标签名已存在");
        }
        String color = request.color() == null ? tag.getColor() : request.color();
        tagMapper.update(tagId, userId, name, normalized, color);
        tag.setName(name);
        tag.setNormalizedName(normalized);
        tag.setColor(color);
        return TagVO.fromEntity(tag);
    }

    @Override
    @Transactional
    public void delete(long userId, long tagId) {
        TagEntity tag = tagMapper.findByIdAndUser(tagId, userId);
        if (tag == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "标签不存在");
        }
        tagMapper.deleteTagRelations(tagId, userId);
        tagMapper.deleteTag(tagId, userId);
    }

    @Override
    @Transactional
    public TagVO addArticleTag(long userId, long articleId, Long tagId,
                               String name, String color) {
        TagEntity tag;
        if (tagId != null) {
            tag = tagMapper.findByIdAndUser(tagId, userId);
            if (tag == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "标签不存在");
            }
        } else {
            String normalizedName = normalizeName(name);
            if (normalizedName == null) {
                throw new BizException(ErrorCode.VALIDATION_ERROR, "tagId 或 name 至少提供一个");
            }
            String normalized = normalizedName.toLowerCase(Locale.ROOT);
            tag = tagMapper.findByNormalizedName(userId, normalized);
            if (tag == null) {
                tag = createTag(userId, normalizedName, normalized, color);
            }
        }
        tagMapper.insertArticleTag(articleId, tag.getId(), userId);
        return TagVO.fromEntity(tag);
    }

    @Override
    public void removeArticleTag(long userId, long articleId, long tagId) {
        tagMapper.deleteArticleTag(articleId, tagId, userId);
    }

    @Override
    public List<Long> findArticleIdsByTag(long userId, long tagId) {
        return tagMapper.findArticleIdsByTag(userId, tagId);
    }

    private TagEntity createTag(long userId, String name, String normalized, String color) {
        TagEntity tag = new TagEntity();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setNormalizedName(normalized);
        tag.setColor(color == null || !color.matches("^#[0-9a-fA-F]{6}$")
                ? COLORS[Math.floorMod(normalized.hashCode(), COLORS.length)]
                : color);
        try {
            tagMapper.insert(tag);
            return tag;
        } catch (DuplicateKeyException ex) {
            return tagMapper.findByNormalizedName(userId, normalized);
        }
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim();
        while (name.startsWith("#")) {
            name = name.substring(1).trim();
        }
        name = name.replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            return null;
        }
        return name.length() <= MAX_TAG_NAME_CHARS
                ? name
                : name.substring(0, MAX_TAG_NAME_CHARS);
    }
}
