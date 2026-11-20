package com.shiye.user.module.article.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ArticleShareRequest(
        @NotNull
        @Min(1)
        Long teamId
) {
}
