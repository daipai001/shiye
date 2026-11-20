package com.shiye.user.module.team.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TeamTagRequest(
        @NotBlank
        @Size(max = 64)
        String name,

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "color 需为 #RRGGBB")
        String color
) {
}
