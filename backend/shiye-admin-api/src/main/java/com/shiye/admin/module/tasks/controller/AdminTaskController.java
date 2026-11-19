package com.shiye.admin.module.tasks.controller;

import com.shiye.admin.module.audit.service.AdminAuditService;
import com.shiye.admin.module.tasks.domain.vo.AdminTaskVO;
import com.shiye.admin.module.tasks.service.AdminTaskService;
import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/tasks")
public class AdminTaskController {

    private final AdminTaskService taskService;
    private final AdminAuditService auditService;

    public AdminTaskController(AdminTaskService taskService,
                               AdminAuditService auditService) {
        this.taskService = taskService;
        this.auditService = auditService;
    }

    @GetMapping
    public Result<PageResult<AdminTaskVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        return Result.ok(taskService.list(page, pageSize, userId, status));
    }

    @GetMapping("/{id}")
    public Result<AdminTaskVO> detail(@PathVariable long id) {
        return Result.ok(taskService.detail(id));
    }

    @PostMapping("/{id}/retry")
    public Result<AdminTaskVO> retry(@PathVariable long id) {
        AdminTaskVO task = taskService.retry(id);
        auditService.record("TASK_RETRY", "INGEST_TASK", String.valueOf(id), null);
        return Result.ok(task);
    }
}
