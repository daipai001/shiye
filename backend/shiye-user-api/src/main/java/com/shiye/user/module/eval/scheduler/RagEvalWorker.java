package com.shiye.user.module.eval.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.user.common.ai.chat.ChatCompletion;
import com.shiye.user.common.ai.chat.ChatMessage;
import com.shiye.user.common.ai.chat.DeepSeekChatClient;
import com.shiye.user.common.ai.embedding.EmbeddingService;
import com.shiye.user.module.chat.service.ChunkRetrievalService;
import com.shiye.user.module.chat.support.RetrievalResult;
import com.shiye.user.module.chat.support.RetrievedChunk;
import com.shiye.user.module.eval.domain.entity.EvalCaseRow;
import com.shiye.user.module.eval.domain.entity.EvalRunRow;
import com.shiye.user.module.eval.mapper.UserEvalMapper;
import com.shiye.user.module.eval.support.EvalMetrics;
import com.shiye.user.module.usage.domain.vo.RecordedUsage;
import com.shiye.user.module.usage.service.UsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 消费管理端创建的 RAG 评测运行：复用线上检索链路，生成回答并计算
 * recall@5、引用准确率、LLM-as-judge 答案正确率、延迟和成本。
 */
@Component
@ConditionalOnProperty(name = "shiye.eval.worker-enabled", havingValue = "true", matchIfMissing = true)
public class RagEvalWorker {

    private static final Logger log = LoggerFactory.getLogger(RagEvalWorker.class);
    private static final int MAX_RUNS_PER_TICK = 3;
    private static final int ANSWER_MAX_TOKENS = 800;
    private static final int JUDGE_MAX_TOKENS = 300;
    private static final int MAX_CONTEXT_CHARS_PER_CHUNK = 1200;

    private final UserEvalMapper mapper;
    private final ChunkRetrievalService retrievalService;
    private final EmbeddingService embeddingService;
    private final DeepSeekChatClient chatClient;
    private final UsageService usageService;
    private final ObjectMapper objectMapper;
    private final int staleMinutes;

    public RagEvalWorker(UserEvalMapper mapper,
                         ChunkRetrievalService retrievalService,
                         EmbeddingService embeddingService,
                         DeepSeekChatClient chatClient,
                         UsageService usageService,
                         ObjectMapper objectMapper,
                         @Value("${shiye.eval.stale-minutes:30}") int staleMinutes) {
        this.mapper = mapper;
        this.retrievalService = retrievalService;
        this.embeddingService = embeddingService;
        this.chatClient = chatClient;
        this.usageService = usageService;
        this.objectMapper = objectMapper;
        this.staleMinutes = Math.max(5, staleMinutes);
    }

    @Scheduled(initialDelayString = "${shiye.eval.worker-initial-delay-ms:15000}",
            fixedDelayString = "${shiye.eval.worker-delay-ms:30000}")
    public void tick() {
        int recovered = mapper.recoverStaleRuns(
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(staleMinutes));
        if (recovered > 0) {
            log.warn("recovered {} stale eval runs as FAILED", recovered);
        }
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
        String model = chatClient.resolveModel(run.getModel());
        List<EvalCaseRow> cases = mapper.findActiveCases(run.getUserId());
        List<BigDecimal> recalls = new ArrayList<>();
        List<BigDecimal> citations = new ArrayList<>();
        List<BigDecimal> correctness = new ArrayList<>();
        List<Integer> latencies = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;

        for (EvalCaseRow evalCase : cases) {
            long started = System.currentTimeMillis();
            RetrievalResult result = retrievalService.retrieve(
                    run.getUserId(), evalCase.getQuestion(), "ALL", null, topK,
                    Boolean.TRUE.equals(run.getRerank()));
            List<RetrievedChunk> chunks = result.chunks();
            List<Long> retrievedChunkIds = chunks.stream()
                    .map(RetrievedChunk::chunkId)
                    .toList();
            BigDecimal recall = recallAt5(evalCase, chunks);
            if (recall != null) {
                recalls.add(recall);
            }

            String answerText = null;
            String judgeReason = null;
            BigDecimal citationAccuracy = null;
            BigDecimal answerCorrectness = null;
            BigDecimal caseCost = BigDecimal.ZERO;
            if (chatClient.isConfigured()) {
                try {
                    long answerStarted = System.currentTimeMillis();
                    ChatCompletion answer = chatClient.complete(
                            answerMessages(evalCase.getQuestion(), chunks),
                            ANSWER_MAX_TOKENS, false, model);
                    answerText = truncate(answer.content(), 8000);
                    caseCost = caseCost.add(recordUsage(run, evalCase, "eval_answer",
                            answer, (int) (System.currentTimeMillis() - answerStarted)));
                    citationAccuracy = EvalMetrics.citationAccuracy(
                            answerText, chunks,
                            parseIds(evalCase.getExpectedChunkIds()),
                            parseIds(evalCase.getExpectedArticleIds()));
                    if (citationAccuracy != null) {
                        citations.add(citationAccuracy);
                    }
                    if (evalCase.getGoldAnswer() != null
                            && !evalCase.getGoldAnswer().isBlank()) {
                        long judgeStarted = System.currentTimeMillis();
                        ChatCompletion judge = chatClient.complete(
                                judgeMessages(evalCase, answerText),
                                JUDGE_MAX_TOKENS, true, model);
                        caseCost = caseCost.add(recordUsage(run, evalCase, "eval_judge",
                                judge, (int) (System.currentTimeMillis() - judgeStarted)));
                        EvalMetrics.JudgeResult judged = EvalMetrics.parseJudge(judge.content());
                        if (judged != null) {
                            answerCorrectness = judged.score();
                            judgeReason = truncate(judged.reason(), 2000);
                            correctness.add(answerCorrectness);
                        } else {
                            judgeReason = "judge 返回无法解析";
                        }
                    } else {
                        judgeReason = "未提供标准答案，跳过答案正确率";
                    }
                } catch (RuntimeException ex) {
                    log.warn("eval case {} answer failed: {}", evalCase.getId(), ex.getMessage());
                    judgeReason = "回答生成失败：" + truncate(ex.getMessage(), 500);
                }
            } else {
                judgeReason = "AI 未配置，仅计算检索指标";
            }

            int latency = (int) (System.currentTimeMillis() - started);
            latencies.add(latency);
            totalCost = totalCost.add(caseCost);
            mapper.insertResult(
                    run.getId(),
                    evalCase.getId(),
                    objectMapper.writeValueAsString(retrievedChunkIds),
                    recall,
                    citationAccuracy,
                    answerCorrectness,
                    answerText,
                    judgeReason,
                    latency,
                    caseCost
            );
            mapper.touchRun(run.getId());
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("cases", cases.size());
        metrics.put("scoredCases", recalls.size());
        metrics.put("avgRecallAt5", average(recalls));
        metrics.put("citationScoredCases", citations.size());
        metrics.put("avgCitationAccuracy", average(citations));
        metrics.put("correctnessScoredCases", correctness.size());
        metrics.put("avgAnswerCorrectness", average(correctness));
        metrics.put("avgLatencyMs", averageInt(latencies));
        metrics.put("totalCost", totalCost.setScale(8, RoundingMode.HALF_UP));
        metrics.put("model", model);
        metrics.put("topK", topK);
        mapper.finishRun(run.getId(), objectMapper.writeValueAsString(metrics),
                embeddingService.model(), model, topK);
        log.info("eval run {} finished: {} cases, recall@5={}, citationAccuracy={}, correctness={}",
                run.getId(), cases.size(), metrics.get("avgRecallAt5"),
                metrics.get("avgCitationAccuracy"), metrics.get("avgAnswerCorrectness"));
    }

    private List<ChatMessage> answerMessages(String question, List<RetrievedChunk> chunks) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievedChunk chunk = chunks.get(i);
            context.append('[').append(i + 1).append("] ")
                    .append(chunk.title() == null ? "资料" : chunk.title())
                    .append('\n')
                    .append(truncate(chunk.content(), MAX_CONTEXT_CHARS_PER_CHUNK))
                    .append("\n\n");
        }
        String system = """
                你是拾页的 RAG 评测助手。只根据下面提供的资料回答，不要编造资料外的信息。
                每个关键结论后用 [1]、[2] 这样的编号标注来源；资料不足时明确说“资料中没有足够信息”。
                资料：
                """ + context;
        return List.of(new ChatMessage("system", system),
                new ChatMessage("user", question));
    }

    private List<ChatMessage> judgeMessages(EvalCaseRow evalCase, String answer) {
        String user = """
                问题：%s
                标准答案：%s
                模型回答：%s
                请只输出 JSON：{"score": 0到1之间的数字, "reason": "不超过100字的判断依据"}。
                """.formatted(evalCase.getQuestion(), evalCase.getGoldAnswer(), answer);
        return List.of(
                new ChatMessage("system", "你是严格的 RAG 答案评测员，按事实正确性、完整性和是否遵守资料边界打分。"),
                new ChatMessage("user", user)
        );
    }

    private BigDecimal recordUsage(EvalRunRow run, EvalCaseRow evalCase, String prefix,
                                   ChatCompletion completion, int latencyMs) {
        String requestId = prefix + "_" + run.getId() + "_" + evalCase.getId() + "_"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try {
            RecordedUsage usage = usageService.recordEval(
                    run.getUserId(), requestId,
                    chatClient.resolveModel(run.getModel()),
                    completion.promptTokens(), completion.completionTokens(),
                    chatClient.inputPrice(), chatClient.outputPrice(),
                    latencyMs, completion.estimated());
            return usage.cost();
        } catch (RuntimeException ex) {
            log.warn("record eval usage failed: {}", ex.getMessage());
            return BigDecimal.ZERO;
        }
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

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal averageInt(List<Integer> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        long total = values.stream().mapToLong(Integer::longValue).sum();
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
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

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private void finishWithError(EvalRunRow run, Exception ex) {
        try {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("cases", 0);
            metrics.put("scoredCases", 0);
            metrics.put("avgRecallAt5", BigDecimal.ZERO);
            metrics.put("citationScoredCases", 0);
            metrics.put("avgCitationAccuracy", BigDecimal.ZERO);
            metrics.put("correctnessScoredCases", 0);
            metrics.put("avgAnswerCorrectness", BigDecimal.ZERO);
            metrics.put("avgLatencyMs", BigDecimal.ZERO);
            metrics.put("totalCost", BigDecimal.ZERO);
            metrics.put("error", ex.getMessage() == null ? "EVAL_FAILED" : ex.getMessage());
            String model = chatClient.resolveModel(run.getModel());
            mapper.finishRun(run.getId(), objectMapper.writeValueAsString(metrics),
                    embeddingService.model(), model,
                    run.getTopK() == null ? 5 : run.getTopK());
        } catch (Exception ignored) {
            // 保留 RUNNING 状态，后续人工排查
        }
    }
}
