package com.shiye.admin.module.teams.controller;

import com.shiye.admin.module.teams.domain.vo.AdminTeamAuditVO;
import com.shiye.admin.module.teams.service.AdminTeamAuditService;
import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/api/v1/team-audits")
public class AdminTeamAuditController {

    private final AdminTeamAuditService auditService;

    public AdminTeamAuditController(AdminTeamAuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public Result<PageResult<AdminTeamAuditVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long teamId,
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.ok(auditService.list(
                page, pageSize, teamId, operatorId, action, from, to));
    }
}
