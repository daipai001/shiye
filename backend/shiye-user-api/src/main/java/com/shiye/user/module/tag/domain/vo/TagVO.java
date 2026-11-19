package com.shiye.user.module.tag.domain.vo;

import com.shiye.user.module.tag.domain.entity.TagRow;

public record TagVO(
        String id,
        String name,
        String color,
        long articleCount
) {

    public static TagVO fromRow(TagRow row) {
        return new TagVO(
                String.valueOf(row.getTagId()),
                row.getName(),
                row.getColor(),
                row.getArticleCount() == null ? 0 : row.getArticleCount()
        );
    }
}
