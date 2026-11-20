package com.shiye.user.module.ingest.service.impl;

import com.shiye.user.module.ingest.domain.entity.IngestTaskEntity;
import com.shiye.user.module.ingest.mapper.IngestTaskMapper;
import com.shiye.user.module.ingest.service.IngestTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class IngestTaskServiceImpl implements IngestTaskService {

    private static final Logger log = LoggerFactory.getLogger(IngestTaskServiceImpl.class);

    private final IngestTaskMapper mapper;

    public IngestTaskServiceImpl(IngestTaskMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public long begin(long userId, long articleId, String sourceType, String stage) {
        try {
            IngestTaskEntity task = new IngestTaskEntity();
            task.setUserId(userId);
            task.setArticleId(articleId);
            task.setSourceType(sourceType);
            task.setStage(stage);
            task.setStatus("RUNNING");
            task.setIdempotencyKey("ingest:" + sourceType + ":" + articleId + ":"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            mapper.insert(task);
            return task.getId();
        } catch (RuntimeException ex) {
            log.warn("begin ingest task failed: {}", ex.getMessage());
            return 0;
        }
    }

    @Override
    public void advance(long taskId, String stage) {
        if (taskId <= 0) {
            return;
        }
        try {
            mapper.updateStage(taskId, stage);
        } catch (RuntimeException ex) {
            log.warn("update ingest task {} stage failed: {}", taskId, ex.getMessage());
        }
    }

    @Override
    public void complete(long taskId) {
        if (taskId <= 0) {
            return;
        }
        try {
            mapper.markSuccess(taskId);
        } catch (RuntimeException ex) {
            log.warn("complete ingest task {} failed: {}", taskId, ex.getMessage());
        }
    }

    @Override
    public void fail(long taskId, String errorCode, String errorMessage) {
        if (taskId <= 0) {
            return;
        }
        try {
            mapper.markFailed(taskId, truncate(errorCode, 64), truncate(errorMessage, 1024));
        } catch (RuntimeException ex) {
            log.warn("fail ingest task {} failed: {}", taskId, ex.getMessage());
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
