package com.shiye.admin.module.eval.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.admin.module.eval.domain.dto.EvalCaseCreateRequest;
import com.shiye.admin.module.eval.domain.dto.EvalRunCreateRequest;
import com.shiye.admin.module.eval.domain.entity.EvalCaseEntity;
import com.shiye.admin.module.eval.domain.entity.EvalResultEntity;
import com.shiye.admin.module.eval.domain.entity.EvalRunEntity;
import com.shiye.admin.module.eval.domain.vo.EvalCaseVO;
import com.shiye.admin.module.eval.domain.vo.EvalResultVO;
import com.shiye.admin.module.eval.domain.vo.EvalRunVO;
import com.shiye.admin.module.eval.mapper.AdminEvalMapper;
import com.shiye.admin.module.eval.service.AdminEvalService;
import com.shiye.common.api.PageResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminEvalServiceImpl implements AdminEvalService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminEvalMapper mapper;
    private final ObjectMapper objectMapper;

    public AdminEvalServiceImpl(AdminEvalMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<EvalCaseVO> cases(int page, int pageSize, Long userId, String status) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String statusFilter = status == null || status.isBlank() ? null : status.trim();
        long total = mapper.countCases(userId, statusFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<EvalCaseVO> items = mapper.findCases(userId, statusFilter,
                        (safePage - 1) * safeSize, safeSize)
                .stream().map(this::toCaseVO).toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    public EvalCaseVO createCase(EvalCaseCreateRequest request) {
        EvalCaseEntity entity = new EvalCaseEntity();
        entity.setUserId(request.userId());
        entity.setQuestion(request.question().trim());
        entity.setExpectedArticleIds(writeJson(request.expectedArticleIds()));
        entity.setExpectedChunkIds(writeJson(request.expectedChunkIds()));
        entity.setGoldAnswer(trimToNull(request.goldAnswer()));
        entity.setTags(writeJson(request.tags()));
        entity.setStatus(request.status() == null || request.status().isBlank()
                ? "ACTIVE" : request.status().trim().toUpperCase());
        mapper.insertCase(entity);
        return toCaseVO(entity);
    }

    @Override
    public PageResult<EvalRunVO> runs(int page, int pageSize, Long userId) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = mapper.countRuns(userId);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<EvalRunVO> items = mapper.findRuns(userId, (safePage - 1) * safeSize, safeSize)
                .stream().map(run -> toRunVO(run, false)).toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    public EvalRunVO createRun(EvalRunCreateRequest request) {
        EvalRunEntity entity = new EvalRunEntity();
        entity.setUserId(request.userId());
        entity.setName(request.name().trim());
        entity.setModel(trimToNull(request.model()));
        entity.setTopK(request.topK() == null ? 5 : request.topK());
        entity.setRerank(Boolean.TRUE.equals(request.rerank()));
        mapper.insertRun(entity);
        EvalRunEntity saved = mapper.findRun(entity.getId());
        return toRunVO(saved == null ? entity : saved, false);
    }

    @Override
    public EvalRunVO run(long id) {
        EvalRunEntity entity = mapper.findRun(id);
        if (entity == null) {
            throw new com.shiye.common.exception.BizException(
                    com.shiye.common.api.ErrorCode.NOT_FOUND, "评测运行不存在");
        }
        return toRunVO(entity, true);
    }

    private EvalCaseVO toCaseVO(EvalCaseEntity entity) {
        return new EvalCaseVO(
                String.valueOf(entity.getId()),
                String.valueOf(entity.getUserId()),
                entity.getUserEmail(),
                entity.getQuestion(),
                readLongList(entity.getExpectedArticleIds()),
                readLongList(entity.getExpectedChunkIds()),
                entity.getGoldAnswer(),
                readStringList(entity.getTags()),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private EvalRunVO toRunVO(EvalRunEntity entity, boolean includeResults) {
        List<EvalResultVO> results = includeResults
                ? mapper.findResults(entity.getId()).stream().map(this::toResultVO).toList()
                : List.of();
        return new EvalRunVO(
                String.valueOf(entity.getId()),
                String.valueOf(entity.getUserId()),
                entity.getUserName(),
                entity.getName(),
                entity.getModel(),
                entity.getEmbeddingModel(),
                entity.getTopK(),
                Boolean.TRUE.equals(entity.getRerank()),
                statusOf(entity),
                readJson(entity.getMetrics()),
                entity.getStartedAt(),
                entity.getFinishedAt(),
                entity.getCreatedAt(),
                results
        );
    }

    private EvalResultVO toResultVO(EvalResultEntity entity) {
        return new EvalResultVO(
                String.valueOf(entity.getId()),
                String.valueOf(entity.getCaseId()),
                entity.getQuestion(),
                readLongList(entity.getRetrievedChunkIds()),
                entity.getRecallAt5(),
                entity.getCitationAccuracy(),
                entity.getAnswerCorrectness(),
                entity.getAnswerText(),
                entity.getJudgeReason(),
                entity.getLatencyMs(),
                entity.getCost(),
                entity.getCreatedAt()
        );
    }

    private String statusOf(EvalRunEntity entity) {
        if (entity.getFinishedAt() != null) {
            String metrics = entity.getMetrics();
            return metrics != null && metrics.contains("\"error\"") ? "FAILED" : "DONE";
        }
        return entity.getStartedAt() == null ? "PENDING" : "RUNNING";
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            return null;
        }
    }

    private List<Long> readLongList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
