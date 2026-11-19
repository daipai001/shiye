package com.shiye.admin.module.models.controller;

import com.shiye.admin.common.security.AdminContext;
import com.shiye.admin.module.models.domain.dto.AdminModelUpdateRequest;
import com.shiye.admin.module.models.domain.dto.ModelTestRequest;
import com.shiye.admin.module.models.domain.vo.AdminModelConfigVO;
import com.shiye.admin.module.models.domain.vo.ModelTestVO;
import com.shiye.admin.module.models.service.AdminModelService;
import com.shiye.common.api.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/models")
public class AdminModelController {

    private final AdminModelService modelService;

    public AdminModelController(AdminModelService modelService) {
        this.modelService = modelService;
    }

    @GetMapping
    public Result<AdminModelConfigVO> config() {
        return Result.ok(modelService.config());
    }

    @PutMapping
    public Result<AdminModelConfigVO> update(
            @Valid @RequestBody AdminModelUpdateRequest request) {
        return Result.ok(modelService.update(request, AdminContext.requireAdminId()));
    }

    @PostMapping("/test")
    public Result<ModelTestVO> test(@Valid @RequestBody ModelTestRequest request) {
        return Result.ok(modelService.test(request.type()));
    }
}
