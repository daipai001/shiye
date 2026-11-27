package com.shiye.admin.module.teams.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.admin.module.teams.domain.entity.AdminTeamAuditRowEntity;
import com.shiye.admin.module.teams.domain.vo.AdminTeamAuditVO;
import com.shiye.admin.module.teams.mapper.AdminTeamAuditMapper;
import com.shiye.admin.module.teams.service.AdminTeamAuditService;
import com.shiye.common.api.PageResult;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminTeamAuditServiceImpl implements AdminTeamAuditService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminTeamAuditMapper auditMapper;
    private final ObjectMapper objectMapper;

    public AdminTeamAuditServiceImpl(AdminTeamAuditMapper auditMapper,
                                     ObjectMapper objectMapper) {
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<AdminTeamAuditVO> list(int page, int pageSize, Long teamId,
                                             Long operatorId, String action,
                                             LocalDateTime from, LocalDateTime to) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String actionFilter = action == null || action.isBlank() ? null : action.trim();
        long total = auditMapper.count(teamId, operatorId, actionFilter, from, to);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<AdminTeamAuditVO> items = auditMapper
                .findPage(teamId, operatorId, actionFilter, from, to,
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    private AdminTeamAuditVO toVO(AdminTeamAuditRowEntity row) {
        JsonNode detail = null;
        if (row.getDetail() != null && !row.getDetail().isBlank()) {
            try {
                detail = objectMapper.readTree(row.getDetail());
            } catch (Exception ignored) {
                // 历史脏数据不影响列表
            }
        }
        return AdminTeamAuditVO.from(row, detail);
    }
}
