package com.shiye.user.module.chat.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationRenameRequest(
        @NotBlank @Size(max = 255) String title
) {
}
