package com.shiye.user.module.article.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ArticleCollectRequest(
        @NotBlank @Pattern(regexp = "URL|CURRENT_PAGE", message = "mode 仅支持 URL / CURRENT_PAGE")
        String mode,
        @Size(max = 2048) String url,
        @Size(max = 512) String title,
        @Size(max = 2000000) String content
) {
}
