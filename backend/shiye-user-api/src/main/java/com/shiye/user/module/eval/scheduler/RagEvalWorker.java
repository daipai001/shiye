package com.shiye.user.module.eval.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.user.common.ai.embedding.EmbeddingService;
import com.shiye.user.module.chat.service.ChunkRetrievalService;
import com.shiye.user.module.chat.support.RetrievalResult;
import com.shiye.user.module.chat.support.RetrievedChunk;
import com.shiye.user.module.eval.domain.entity.EvalCaseRow;
import com.shiye.user.module.eval.domain.entity.EvalRunRow;
import com.shiye.user.module.eval.mapper.UserEvalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 消费管理端创建的 RAG 评测运行，复用线上检索链路，计算 recall@5。
 */
@Component
@ConditionalOnProperty(name = "shiye.eval.worker-enabled", havingValue = "true", matchIfMissing = true)
public class RagEvalWorker {

    private static final Logger log = LoggerFactory.getLogger(RagEvalWorker.class);
    private static final int MAX_RUNS_PER_TICK = 3;

    private final UserEvalMapper mapper;
    private final ChunkRetrievalService retrievalService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public RagEvalWorker(UserEvalMapper mapper,
                         ChunkRetrievalService retrievalService,
                         EmbeddingService embeddingService,
                         ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.retrievalService = retrievalService;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(initialDelayString = "${shiye.eval.worker-initial-delay-ms:15000}",
            fixedDelayString = "${shiye.eval.worker-delay-ms:30000}")
    public void tick() {
        for (int i = 0; i < MAX_RUNS_PER_TICK; i++) {
            EvalRunRow run = mapper.findPendingRun();
            if (run == null) {
                return;
            }
            if (mapper.markStarted(run.getId()) == 0) {
                continue;
            }
            try {
                execute(run);
            } catch (Exception ex) {
                log.warn("eval run {} failed: {}", run.getId(), ex.getMessage());
                finishWithError(run, ex);
            }
        }
    }

    private void execute(EvalRunRow run) throws Exception {
        int topK = run.getTopK() == null ? 5 : Math.max(1, Math.min(run.getTopK(), 20));
        List<EvalCaseRow> cases = mapper.findActiveCases(run.getUserId());
        List<BigDecimal> recalls = new ArrayList<>();
        for (EvalCaseRow evalCase : cases) {
            long started = System.currentTimeMillis();
            RetrievalResult result = retrievalService.retrieve(
                    run.getUserId(), evalCase.getQuestion(), "ALL", null, topK,
                    Boolean.TRUE.equals(run.getRerank()));
            List<Long> retrievedChunkIds = result.chunks().stream()
                    .map(RetrievedChunk::chunkId)
                    .toList();
            BigDecimal recall = recallAt5(evalCase, result.chunks());
            if (recall != null) {
                recalls.add(recall);
            }
            mapper.insertResult(
                    run.getId(),
                    evalCase.getId(),
                    objectMapper.writeValueAsString(retrievedChunkIds),
                    recall,
                    (int) (System.currentTimeMillis() - started)
            );
        }
        BigDecimal avgRecall = recalls.isEmpty()
                ? BigDecimal.ZERO
                : recalls.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(recalls.size()), 4, RoundingMode.HALF_UP);
        Map<String, Object> metrics = Map.of(
                "cases", cases.size(),
                "scoredCases", recalls.size(),
                "avgRecallAt5", avgRecall,
                "topK", topK
        );
        mapper.finishRun(run.getId(), objectMapper.writeValueAsString(metrics),
                embeddingService.model(), topK);
        log.info("eval run {} finished: {} cases, avg recall@5={}",
                run.getId(), cases.size(), avgRecall);
    }

    private BigDecimal recallAt5(EvalCaseRow evalCase, List<RetrievedChunk> chunks) {
        Set<Long> expectedChunks = parseIds(evalCase.getExpectedChunkIds());
        if (!expectedChunks.isEmpty()) {
            Set<Long> retrieved = chunks.stream().limit(5)
                    .map(RetrievedChunk::chunkId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return ratio(expectedChunks, retrieved);
        }
        Set<Long> expectedArticles = parseIds(evalCase.getExpectedArticleIds());
        if (!expectedArticles.isEmpty()) {
            Set<Long> retrieved = chunks.stream().limit(5)
                    .map(RetrievedChunk::articleId)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            return ratio(expectedArticles, retrieved);
        }
        return null;
    }

    private BigDecimal ratio(Set<Long> expected, Set<Long> retrieved) {
        Set<Long> hit = new HashSet<>(expected);
        hit.retainAll(retrieved);
        return BigDecimal.valueOf(hit.size())
                .divide(BigDecimal.valueOf(expected.size()), 4, RoundingMode.HALF_UP);
    }

    private Set<Long> parseIds(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<Long> ids = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Long.class));
            return new HashSet<>(ids);
        } catch (Exception ex) {
            return Set.of();
        }
    }

    private void finishWithError(EvalRunRow run, Exception ex) {
        try {
            Map<String, Object> metrics = Map.of(
                    "cases", 0,
                    "scoredCases", 0,
                    "avgRecallAt5", BigDecimal.ZERO,
                    "error", ex.getMessage() == null ? "EVAL_FAILED" : ex.getMessage()
            );
            mapper.finishRun(run.getId(), objectMapper.writeValueAsString(metrics),
                    embeddingService.model(), run.getTopK() == null ? 5 : run.getTopK());
        } catch (Exception ignored) {
            // 保留 RUNNING 状态，后续人工排查
        }
    }
}
