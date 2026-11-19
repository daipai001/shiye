package com.shiye.user.module.usage.service;

import com.shiye.common.api.PageResult;
import com.shiye.user.module.usage.domain.vo.QuotaVO;
import com.shiye.user.module.usage.domain.vo.RecordedUsage;
import com.shiye.user.module.usage.domain.vo.UsageRecordVO;

import java.math.BigDecimal;

public interface UsageService {

    QuotaVO quota(long userId);

    void assertCanChat(long userId);

    RecordedUsage recordChat(long userId, String requestId, String source, String model,
                             int promptTokens, int completionTokens,
                             BigDecimal inputPrice, BigDecimal outputPrice,
                             int latencyMs, Integer firstTokenMs,
                             boolean estimated, long conversationId);

    void recordFailedChat(long userId, String requestId, String source, String model,
                          String errorCode, int latencyMs, long conversationId);

    void recordRerank(long userId, String requestId, String source, String model,
                      int tokens, int latencyMs, long conversationId);

    void recordSummary(long userId, String requestId, String source, String model,
                       int promptTokens, int completionTokens,
                       BigDecimal inputPrice, BigDecimal outputPrice,
                       int latencyMs, boolean estimated, long articleId);

    PageResult<UsageRecordVO> records(long userId, int page, int pageSize,
                                      String source, String scene, String model, String status);
}
