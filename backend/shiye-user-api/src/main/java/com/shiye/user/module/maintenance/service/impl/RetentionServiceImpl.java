package com.shiye.user.module.maintenance.service.impl;

import com.shiye.user.module.maintenance.mapper.RetentionMapper;
import com.shiye.user.module.maintenance.service.RetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.Function;

@Service
public class RetentionServiceImpl implements RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionServiceImpl.class);
    private static final int MAX_BATCHES = 10;

    private final RetentionMapper mapper;
    private final int refreshTokenDays;
    private final int extensionCodeDays;
    private final int idempotencyDays;
    private final int ingestTaskDays;
    private final int batchLimit;

    public RetentionServiceImpl(
            RetentionMapper mapper,
            @Value("${shiye.retention.refresh-token-days:30}") int refreshTokenDays,
            @Value("${shiye.retention.extension-code-days:7}") int extensionCodeDays,
            @Value("${shiye.retention.idempotency-days:1}") int idempotencyDays,
            @Value("${shiye.retention.ingest-task-days:30}") int ingestTaskDays,
            @Value("${shiye.retention.batch-limit:1000}") int batchLimit) {
        this.mapper = mapper;
        this.refreshTokenDays = Math.max(0, refreshTokenDays);
        this.extensionCodeDays = Math.max(0, extensionCodeDays);
        this.idempotencyDays = Math.max(0, idempotencyDays);
        this.ingestTaskDays = Math.max(0, ingestTaskDays);
        this.batchLimit = Math.max(100, Math.min(batchLimit, 10000));
    }

    @Override
    public RetentionResult cleanup() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int refreshTokens = deleteInBatches(
                cutoff -> mapper.deleteRefreshTokens(cutoff, batchLimit),
                now.minusDays(refreshTokenDays), "refresh_tokens");
        int extensionCodes = deleteInBatches(
                cutoff -> mapper.deleteExtensionCodes(cutoff, batchLimit),
                now.minusDays(extensionCodeDays), "extension_auth_codes");
        int idempotencyKeys = deleteInBatches(
                cutoff -> mapper.deleteIdempotencyKeys(cutoff, batchLimit),
                now.minusDays(idempotencyDays), "idempotency_keys");
        int ingestTasks = deleteInBatches(
                cutoff -> mapper.deleteIngestTasks(cutoff, batchLimit),
                now.minusDays(ingestTaskDays), "ingest_tasks");
        return new RetentionResult(refreshTokens, extensionCodes, idempotencyKeys, ingestTasks);
    }

    private int deleteInBatches(Function<LocalDateTime, Integer> deleter,
                                LocalDateTime cutoff, String table) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES; i++) {
            try {
                int deleted = deleter.apply(cutoff);
                total += deleted;
                if (deleted < batchLimit) {
                    break;
                }
            } catch (RuntimeException ex) {
                log.warn("cleanup {} failed: {}", table, ex.getMessage());
                break;
            }
        }
        return total;
    }
}
