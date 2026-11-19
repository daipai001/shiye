package com.shiye.admin.module.eval.controller;

import com.shiye.admin.module.audit.service.AdminAuditService;
import com.shiye.admin.module.eval.domain.dto.EvalCaseCreateRequest;
import com.shiye.admin.module.eval.domain.dto.EvalRunCreateRequest;
import com.shiye.admin.module.eval.domain.vo.EvalCaseVO;
import com.shiye.admin.module.eval.domain.vo.EvalRunVO;
import com.shiye.admin.module.eval.service.AdminEvalService;
import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/eval")
public class AdminEvalController {

    private final AdminEvalService evalService;
    private final AdminAuditService auditService;

    public AdminEvalController(AdminEvalService evalService,
                               AdminAuditService auditService) {
        this.evalService = evalService;
        this.auditService = auditService;
    }

    @GetMapping("/cases")
    public Result<PageResult<EvalCaseVO>> cases(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        return Result.ok(evalService.cases(page, pageSize, userId, status));
    }

    @PostMapping("/cases")
    public Result<EvalCaseVO> createCase(
            @Valid @RequestBody EvalCaseCreateRequest request) {
        EvalCaseVO created = evalService.createCase(request);
        auditService.record("EVAL_CASE_CREATE", "EVAL_CASE", created.id(),
                java.util.Map.of("userId", created.userId()));
        return Result.ok(created);
    }

    @GetMapping("/runs")
    public Result<PageResult<EvalRunVO>> runs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId) {
        return Result.ok(evalService.runs(page, pageSize, userId));
    }

    @PostMapping("/runs")
    public Result<EvalRunVO> createRun(
            @Valid @RequestBody EvalRunCreateRequest request) {
        EvalRunVO run = evalService.createRun(request);
        auditService.record("EVAL_RUN_CREATE", "EVAL_RUN", run.id(),
                java.util.Map.of("userId", run.userId(), "topK", run.topK()));
        return Result.ok(run);
    }

    @GetMapping("/runs/{id}")
    public Result<EvalRunVO> run(@PathVariable long id) {
        return Result.ok(evalService.run(id));
    }
}
