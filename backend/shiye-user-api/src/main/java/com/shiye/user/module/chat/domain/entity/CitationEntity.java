package com.shiye.user.module.chat.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CitationEntity {
    private Long id;
    private Long messageId;
    private Long userId;
    private Long articleId;
    private Long chunkId;
    private Integer rank;
    private BigDecimal score;
    private String quote;
    private LocalDateTime createdAt;
    private String title;
    private Integer charStart;
    private Integer charEnd;
}
