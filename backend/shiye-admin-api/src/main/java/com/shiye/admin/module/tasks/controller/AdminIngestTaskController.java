package com.shiye.admin.module.tasks.controller;

import com.shiye.admin.module.tasks.domain.vo.AdminTaskVO;
import com.shiye.admin.module.tasks.service.AdminTaskService;
import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/ingest-tasks")
public class AdminIngestTaskController {

    private final AdminTaskService taskService;

    public AdminIngestTaskController(AdminTaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public Result<PageResult<AdminTaskVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        return Result.ok(taskService.listIngest(page, pageSize, userId, status));
    }
}
