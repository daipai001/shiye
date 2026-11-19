package com.shiye.admin.module.settings.controller;

import com.shiye.admin.common.security.AdminContext;
import com.shiye.admin.module.audit.service.AdminAuditService;
import com.shiye.admin.module.settings.domain.dto.AdminBudgetUpdateRequest;
import com.shiye.admin.module.settings.domain.dto.AdminSettingsUpdateRequest;
import com.shiye.admin.module.settings.domain.vo.AdminSettingsVO;
import com.shiye.admin.module.settings.service.AdminSettingsService;
import com.shiye.common.api.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1")
public class AdminSettingsController {

    private final AdminSettingsService settingsService;
    private final AdminAuditService auditService;

    public AdminSettingsController(AdminSettingsService settingsService,
                                   AdminAuditService auditService) {
        this.settingsService = settingsService;
        this.auditService = auditService;
    }

    @GetMapping("/settings")
    public Result<AdminSettingsVO> settings() {
        return Result.ok(settingsService.get());
    }

    @PutMapping("/settings")
    public Result<AdminSettingsVO> updateSettings(
            @Valid @RequestBody AdminSettingsUpdateRequest request) {
        AdminSettingsVO updated = settingsService.update(
                request, AdminContext.requireAdminId());
        auditService.record("SETTINGS_UPDATE", "SYSTEM_SETTINGS", null,
                java.util.Map.of("registrationEnabled", updated.registrationEnabled(),
                        "ragTopK", updated.ragTopK(),
                        "ragHybridEnabled", updated.ragHybridEnabled(),
                        "ragRerankEnabled", updated.ragRerankEnabled()));
        return Result.ok(updated);
    }

    @PutMapping("/budget")
    public Result<AdminSettingsVO> updateBudget(
            @Valid @RequestBody AdminBudgetUpdateRequest request) {
        AdminSettingsVO updated = settingsService.updateBudget(
                request, AdminContext.requireAdminId());
        auditService.record("BUDGET_UPDATE", "SYSTEM_SETTINGS", null,
                java.util.Map.of("globalMonthlyTokens", updated.globalMonthlyTokens(),
                        "globalMonthlyCost", updated.globalMonthlyCost(),
                        "alertThreshold", updated.alertThreshold()));
        return Result.ok(updated);
    }
}
