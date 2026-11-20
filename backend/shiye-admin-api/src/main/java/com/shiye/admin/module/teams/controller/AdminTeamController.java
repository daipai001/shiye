package com.shiye.admin.module.teams.controller;

import com.shiye.admin.module.audit.service.AdminAuditService;
import com.shiye.admin.module.teams.domain.dto.AdminTeamQuotaRequest;
import com.shiye.admin.module.teams.domain.vo.AdminTeamVO;
import com.shiye.admin.module.teams.service.AdminTeamService;
import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin/api/v1/teams")
public class AdminTeamController {

    private final AdminTeamService teamService;
    private final AdminAuditService auditService;

    public AdminTeamController(AdminTeamService teamService,
                               AdminAuditService auditService) {
        this.teamService = teamService;
        this.auditService = auditService;
    }

    @GetMapping
    public Result<PageResult<AdminTeamVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status) {
        return Result.ok(teamService.list(page, pageSize, q, status));
    }

    @PutMapping("/{id}/quota")
    public Result<AdminTeamVO> quota(@PathVariable long id,
                                     @Valid @RequestBody AdminTeamQuotaRequest request) {
        AdminTeamVO updated = teamService.updateQuota(id, request);
        auditService.record("TEAM_QUOTA_UPDATE", "TEAM", String.valueOf(id),
                Map.of("monthlyTokenQuota", request.monthlyTokenQuota(),
                        "monthlyCostQuota", request.monthlyCostQuota()));
        return Result.ok(updated);
    }
}
