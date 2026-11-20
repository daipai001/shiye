package com.shiye.user.module.ingest.service;

public interface IngestTaskService {

    long begin(long userId, long articleId, String sourceType, String stage);

    void advance(long taskId, String stage);

    void complete(long taskId);

    void fail(long taskId, String errorCode, String errorMessage);
}
