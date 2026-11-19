package com.shiye.admin.module.settings.controller;

import com.shiye.admin.common.security.AdminContext;
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

    public AdminSettingsController(AdminSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/settings")
    public Result<AdminSettingsVO> settings() {
        return Result.ok(settingsService.get());
    }

    @PutMapping("/settings")
    public Result<AdminSettingsVO> updateSettings(
            @Valid @RequestBody AdminSettingsUpdateRequest request) {
        return Result.ok(settingsService.update(request, AdminContext.requireAdminId()));
    }

    @PutMapping("/budget")
    public Result<AdminSettingsVO> updateBudget(
            @Valid @RequestBody AdminBudgetUpdateRequest request) {
        return Result.ok(settingsService.updateBudget(request, AdminContext.requireAdminId()));
    }
}
