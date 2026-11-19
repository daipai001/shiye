package com.shiye.user.module.tag.domain.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TagUpdateRequest(
        @Size(min = 1, max = 64) String name,
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "color 必须是 #RRGGBB")
        String color
) {
}
