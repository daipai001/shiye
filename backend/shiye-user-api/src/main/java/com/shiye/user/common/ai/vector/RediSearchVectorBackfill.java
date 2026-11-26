package com.shiye.user.common.ai.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 首次启用 RediSearch 时，按开关把旧 base64 向量回填为二进制 vector_bin。
 */
@Component
public class RediSearchVectorBackfill {

    private static final Logger log = LoggerFactory.getLogger(RediSearchVectorBackfill.class);

    private final RediSearchVectorIndex index;
    private final boolean enabled;

    public RediSearchVectorBackfill(
            RediSearchVectorIndex index,
            @Value("${shiye.ai.vector.backfill:false}") boolean enabled) {
        this.index = index;
        this.enabled = enabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfill() {
        if (!enabled || index.isBackfillComplete()) {
            return;
        }
        int migrated = index.backfillExistingVectors();
        log.info("RediSearch vector backfill finished, migrated={}", migrated);
    }
}
