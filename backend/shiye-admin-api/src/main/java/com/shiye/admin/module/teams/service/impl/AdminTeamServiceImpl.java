package com.shiye.admin.module.teams.service.impl;

import com.shiye.admin.module.teams.domain.dto.AdminTeamQuotaRequest;
import com.shiye.admin.module.teams.domain.entity.AdminTeamRowEntity;
import com.shiye.admin.module.teams.domain.vo.AdminTeamVO;
import com.shiye.admin.module.teams.mapper.AdminTeamMapper;
import com.shiye.admin.module.teams.service.AdminTeamService;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.PageResult;
import com.shiye.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class AdminTeamServiceImpl implements AdminTeamService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminTeamMapper teamMapper;

    public AdminTeamServiceImpl(AdminTeamMapper teamMapper) {
        this.teamMapper = teamMapper;
    }

    @Override
    public PageResult<AdminTeamVO> list(int page, int pageSize, String q, String status) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String qFilter = q == null || q.isBlank() ? null : q.trim();
        String statusFilter = normalizeStatus(status);
        long total = teamMapper.count(qFilter, statusFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<AdminTeamVO> items = teamMapper
                .findPage(qFilter, statusFilter, currentMonth(),
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(AdminTeamVO::from)
                .toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    @Transactional
    public AdminTeamVO updateQuota(long id, AdminTeamQuotaRequest request) {
        AdminTeamRowEntity team = teamMapper.findById(id, currentMonth());
        if (team == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "团队不存在");
        }
        teamMapper.updateQuota(id, request.monthlyTokenQuota(), request.monthlyCostQuota());
        return AdminTeamVO.from(teamMapper.findById(id, currentMonth()));
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(value) && !"DISABLED".equals(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "status 仅支持 ACTIVE / DISABLED");
        }
        return value;
    }

    private String currentMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }
}
