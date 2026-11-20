package com.shiye.admin.module.auth.controller;

import com.shiye.admin.common.security.AdminContext;
import com.shiye.admin.module.audit.service.AdminAuditService;
import com.shiye.admin.module.auth.domain.dto.AdminAccountCreateRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountPasswordResetRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountStatusRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountUpdateRequest;
import com.shiye.admin.module.auth.domain.vo.AdminAccountVO;
import com.shiye.admin.module.auth.service.AdminAccountService;
import com.shiye.common.api.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/v1/admins")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;
    private final AdminAuditService auditService;

    public AdminAccountController(AdminAccountService adminAccountService,
                                  AdminAuditService auditService) {
        this.adminAccountService = adminAccountService;
        this.auditService = auditService;
    }

    @GetMapping
    public Result<List<AdminAccountVO>> list() {
        return Result.ok(adminAccountService.list());
    }

    @PostMapping
    public Result<AdminAccountVO> create(@Valid @RequestBody AdminAccountCreateRequest request) {
        AdminAccountVO created = adminAccountService.create(request);
        auditService.record("ADMIN_CREATE", "ADMIN", created.id(),
                Map.of("email", created.email(), "role", created.role()));
        return Result.ok(created);
    }

    @PutMapping("/{id}")
    public Result<AdminAccountVO> update(@PathVariable long id,
                                         @Valid @RequestBody AdminAccountUpdateRequest request) {
        AdminAccountVO updated = adminAccountService.update(
                AdminContext.requireAdminId(), id, request);
        auditService.record("ADMIN_UPDATE", "ADMIN", updated.id(),
                Map.of("name", updated.name(), "role", updated.role()));
        return Result.ok(updated);
    }

    @PutMapping("/{id}/status")
    public Result<AdminAccountVO> status(@PathVariable long id,
                                         @Valid @RequestBody AdminAccountStatusRequest request) {
        AdminAccountVO updated = adminAccountService.updateStatus(
                AdminContext.requireAdminId(), id, request);
        auditService.record("ADMIN_STATUS_UPDATE", "ADMIN", updated.id(),
                Map.of("status", updated.status()));
        return Result.ok(updated);
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable long id,
                                      @Valid @RequestBody AdminAccountPasswordResetRequest request) {
        adminAccountService.resetPassword(id, request);
        auditService.record("ADMIN_PASSWORD_RESET", "ADMIN", String.valueOf(id), null);
        return Result.ok();
    }
}
