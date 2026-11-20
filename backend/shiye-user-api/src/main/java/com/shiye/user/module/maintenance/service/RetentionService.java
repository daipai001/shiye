package com.shiye.user.module.maintenance.service;

public interface RetentionService {

    RetentionResult cleanup();

    record RetentionResult(
            int refreshTokens,
            int extensionCodes,
            int idempotencyKeys,
            int ingestTasks
    ) {
    }
}
