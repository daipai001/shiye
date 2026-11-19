package com.shiye.admin.module.imports.controller;

import com.shiye.admin.module.audit.service.AdminAuditService;
import com.shiye.admin.module.imports.domain.dto.AdminImportRetryRequest;
import com.shiye.admin.module.imports.domain.vo.AdminImportItemVO;
import com.shiye.admin.module.imports.domain.vo.AdminImportRetryResponse;
import com.shiye.admin.module.imports.domain.vo.AdminImportStatusResponse;
import com.shiye.admin.module.imports.domain.vo.AdminImportVO;
import com.shiye.admin.module.imports.service.AdminImportService;
import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/imports")
public class AdminImportController {

    private final AdminImportService importService;
    private final AdminAuditService auditService;

    public AdminImportController(AdminImportService importService,
                                 AdminAuditService auditService) {
        this.importService = importService;
        this.auditService = auditService;
    }

    @GetMapping
    public Result<PageResult<AdminImportVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status) {
        return Result.ok(importService.list(page, pageSize, userId, status));
    }

    @GetMapping("/{id}")
    public Result<AdminImportVO> detail(@PathVariable long id) {
        return Result.ok(importService.detail(id));
    }

    @GetMapping("/{id}/items")
    public Result<PageResult<AdminImportItemVO>> items(
            @PathVariable long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        return Result.ok(importService.items(id, page, pageSize, status));
    }

    @PostMapping("/{id}/retry")
    public Result<AdminImportRetryResponse> retry(
            @PathVariable long id,
            @RequestBody(required = false) AdminImportRetryRequest request) {
        AdminImportRetryResponse response = importService.retry(
                id, request == null ? null : request.itemIds());
        auditService.record("IMPORT_RETRY", "BOOKMARK_IMPORT", String.valueOf(id),
                java.util.Map.of("retried", response.retried()));
        return Result.ok(response);
    }

    @PostMapping("/{id}/pause")
    public Result<AdminImportStatusResponse> pause(@PathVariable long id) {
        AdminImportStatusResponse response = importService.pause(id);
        auditService.record("IMPORT_PAUSE", "BOOKMARK_IMPORT", String.valueOf(id), null);
        return Result.ok(response);
    }

    @PostMapping("/{id}/resume")
    public Result<AdminImportStatusResponse> resume(@PathVariable long id) {
        AdminImportStatusResponse response = importService.resume(id);
        auditService.record("IMPORT_RESUME", "BOOKMARK_IMPORT", String.valueOf(id), null);
        return Result.ok(response);
    }

    @PostMapping("/{id}/cancel")
    public Result<AdminImportStatusResponse> cancel(@PathVariable long id) {
        AdminImportStatusResponse response = importService.cancel(id);
        auditService.record("IMPORT_CANCEL", "BOOKMARK_IMPORT", String.valueOf(id), null);
        return Result.ok(response);
    }
}
