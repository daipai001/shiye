package com.shiye.admin.module.usage.service.impl;

import com.shiye.admin.module.usage.domain.entity.AdminUsageRowEntity;
import com.shiye.admin.module.usage.domain.entity.AdminUsageSummaryRow;
import com.shiye.admin.module.usage.domain.vo.AdminUsageGroupVO;
import com.shiye.admin.module.usage.domain.vo.AdminUsageRecordVO;
import com.shiye.admin.module.usage.domain.vo.AdminUsageResponse;
import com.shiye.admin.module.usage.domain.vo.AdminUsageSummaryVO;
import com.shiye.admin.module.usage.mapper.AdminUsageMapper;
import com.shiye.admin.module.usage.service.AdminUsageService;
import com.shiye.common.util.Csv;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AdminUsageServiceImpl implements AdminUsageService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_LIMIT = 5000;

    private final AdminUsageMapper usageMapper;

    public AdminUsageServiceImpl(AdminUsageMapper usageMapper) {
        this.usageMapper = usageMapper;
    }

    @Override
    public AdminUsageResponse list(int page, int pageSize, Long userId, String source, String scene,
                                   String model, String status, String from, String to) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        LocalDateTime fromTime = parseFrom(from);
        LocalDateTime toTime = parseTo(to);
        String sourceFilter = upper(source);
        String sceneFilter = upper(scene);
        String modelFilter = blankToNull(model);
        String statusFilter = upper(status);

        long total = usageMapper.count(userId, sourceFilter, sceneFilter, modelFilter,
                statusFilter, fromTime, toTime);
        List<AdminUsageRecordVO> items = usageMapper.findPage(
                        userId, sourceFilter, sceneFilter, modelFilter, statusFilter,
                        fromTime, toTime, (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(AdminUsageRecordVO::from)
                .toList();

        AdminUsageSummaryRow summaryRow = usageMapper.summary(
                userId, sourceFilter, sceneFilter, modelFilter, statusFilter, fromTime, toTime);
        long calls = summaryRow == null || summaryRow.getCalls() == null ? 0 : summaryRow.getCalls();
        long tokens = summaryRow == null || summaryRow.getTokens() == null ? 0 : summaryRow.getTokens();
        BigDecimal cost = summaryRow == null || summaryRow.getCost() == null
                ? BigDecimal.ZERO : summaryRow.getCost();
        long errors = summaryRow == null || summaryRow.getErrors() == null ? 0 : summaryRow.getErrors();
        double errorRate = calls == 0 ? 0 : (double) errors / calls;

        List<AdminUsageGroupVO> sourceSplit = usageMapper.sourceSplit(
                        userId, sourceFilter, sceneFilter, modelFilter, statusFilter, fromTime, toTime)
                .stream().map(AdminUsageGroupVO::from).toList();
        List<AdminUsageGroupVO> modelSplit = usageMapper.modelSplit(
                        userId, sourceFilter, sceneFilter, modelFilter, statusFilter, fromTime, toTime)
                .stream().map(AdminUsageGroupVO::from).toList();

        return new AdminUsageResponse(
                items,
                total,
                safePage,
                safeSize,
                new AdminUsageSummaryVO(
                        calls,
                        tokens,
                        cost,
                        BigDecimal.valueOf(errorRate).setScale(4, RoundingMode.HALF_UP).doubleValue(),
                        sourceSplit,
                        modelSplit
                )
        );
    }

    @Override
    public String exportCsv(Long userId, String source, String scene, String model,
                            String status, String from, String to) {
        List<AdminUsageRowEntity> rows = usageMapper.findPage(
                userId, upper(source), upper(scene), blankToNull(model), upper(status),
                parseFrom(from), parseTo(to), 0, EXPORT_LIMIT);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("id,createdAt,userEmail,source,scene,model,promptTokens,completionTokens,")
                .append("totalTokens,cost,latencyMs,status,requestId\n");
        for (AdminUsageRowEntity row : rows) {
            csv.append(csv(row.getId())).append(',')
                    .append(csv(row.getCreatedAt())).append(',')
                    .append(csv(row.getUserEmail())).append(',')
                    .append(csv(row.getSource())).append(',')
                    .append(csv(row.getScene())).append(',')
                    .append(csv(row.getModel())).append(',')
                    .append(row.getPromptTokens() == null ? 0 : row.getPromptTokens()).append(',')
                    .append(row.getCompletionTokens() == null ? 0 : row.getCompletionTokens()).append(',')
                    .append(row.getTotalTokens() == null ? 0 : row.getTotalTokens()).append(',')
                    .append(row.getCost() == null ? "0" : row.getCost().toPlainString()).append(',')
                    .append(row.getLatencyMs() == null ? "" : row.getLatencyMs()).append(',')
                    .append(csv(row.getStatus())).append(',')
                    .append(csv(row.getRequestId())).append('\n');
        }
        return csv.toString();
    }

    private String csv(Object value) {
        return Csv.escape(value);
    }

    private LocalDateTime parseFrom(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.length() == 10) {
            return LocalDate.parse(text).atStartOfDay();
        }
        return LocalDateTime.parse(text);
    }

    private LocalDateTime parseTo(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim();
        if (text.length() == 10) {
            return LocalDate.parse(text).plusDays(1).atStartOfDay();
        }
        return LocalDateTime.parse(text);
    }

    private String upper(String value) {
        return value == null || value.isBlank()
                ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
