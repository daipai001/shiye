package com.shiye.user.module.article.domain.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ArticleTagAddRequest(
        @Pattern(regexp = "\\d+", message = "tagId 必须是数字") String tagId,
        @Size(max = 64) String name,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "color 必须是 #RRGGBB")
        String color
) {
}
