package com.shiye.user.module.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MessageFeedbackRequest(
        @NotBlank
        @Pattern(regexp = "UP|DOWN", message = "rating 仅支持 UP / DOWN")
        String rating,
        @Size(max = 500) String comment
) {
}
