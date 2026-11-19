package com.shiye.user.module.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ArticleDeleteByUrlRequest(
        @NotBlank @Size(max = 2048) String url
) {
}
