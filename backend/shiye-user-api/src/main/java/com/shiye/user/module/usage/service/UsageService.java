package com.shiye.user.module.usage.service;

import com.shiye.common.api.PageResult;
import com.shiye.user.module.usage.domain.vo.QuotaVO;
import com.shiye.user.module.usage.domain.vo.RecordedUsage;
import com.shiye.user.module.usage.domain.vo.UsageRecordVO;
import com.shiye.user.module.usage.domain.vo.UsageSummaryVO;
import com.shiye.user.module.usage.domain.vo.UsageTotals;

import java.math.BigDecimal;

public interface UsageService {

    QuotaVO quota(long userId);

    UsageTotals monthTotals();

    void assertCanChat(long userId);

    void assertCanChat(long userId, Long teamId);

    default RecordedUsage recordChat(long userId, String requestId, String source, String model,
                                     int promptTokens, int completionTokens,
                                     BigDecimal inputPrice, BigDecimal outputPrice,
                                     int latencyMs, Integer firstTokenMs,
                                     boolean estimated, Long conversationId) {
        return recordChat(userId, requestId, source, model, promptTokens, completionTokens,
                inputPrice, outputPrice, latencyMs, firstTokenMs, estimated, conversationId, null);
    }

    RecordedUsage recordChat(long userId, String requestId, String source, String model,
                             int promptTokens, int completionTokens,
                             BigDecimal inputPrice, BigDecimal outputPrice,
                             int latencyMs, Integer firstTokenMs,
                             boolean estimated, Long conversationId, Long teamId);

    RecordedUsage recordEval(long userId, String requestId, String model,
                            int promptTokens, int completionTokens,
                            BigDecimal inputPrice, BigDecimal outputPrice,
                            int latencyMs, boolean estimated);

    default void recordFailedChat(long userId, String requestId, String source, String model,
                                  String errorCode, int latencyMs, Long conversationId) {
        recordFailedChat(userId, requestId, source, model, errorCode, latencyMs,
                conversationId, null);
    }

    void recordFailedChat(long userId, String requestId, String source, String model,
                          String errorCode, int latencyMs, Long conversationId, Long teamId);

    default void recordRerank(long userId, String requestId, String source, String model,
                              int tokens, int latencyMs, long conversationId) {
        recordRerank(userId, requestId, source, model, tokens, latencyMs, conversationId, null);
    }

    void recordRerank(long userId, String requestId, String source, String model,
                      int tokens, int latencyMs, long conversationId, Long teamId);

    void recordSummary(long userId, String requestId, String source, String model,
                       int promptTokens, int completionTokens,
                       BigDecimal inputPrice, BigDecimal outputPrice,
                       int latencyMs, boolean estimated, long articleId);

    void recordEmbedding(long userId, String requestId, String source, String model,
                         int tokens, int latencyMs, long articleId);

    PageResult<UsageRecordVO> records(long userId, int page, int pageSize,
                                      String source, String scene, String model, String status);

    UsageSummaryVO summary(long userId, int days);

    String exportCsv(long userId, String source, String scene, String model, String status);
}
