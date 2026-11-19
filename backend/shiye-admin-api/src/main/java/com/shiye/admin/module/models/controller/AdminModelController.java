package com.shiye.admin.module.models.controller;

import com.shiye.admin.common.security.AdminContext;
import com.shiye.admin.module.audit.service.AdminAuditService;
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
    private final AdminAuditService auditService;

    public AdminModelController(AdminModelService modelService,
                                AdminAuditService auditService) {
        this.modelService = modelService;
        this.auditService = auditService;
    }

    @GetMapping
    public Result<AdminModelConfigVO> config() {
        return Result.ok(modelService.config());
    }

    @PutMapping
    public Result<AdminModelConfigVO> update(
            @Valid @RequestBody AdminModelUpdateRequest request) {
        AdminModelConfigVO updated = modelService.update(request, AdminContext.requireAdminId());
        auditService.record("MODEL_UPDATE", "MODEL_CONFIG", null,
                java.util.Map.of("source", updated.source(),
                        "chatModel", updated.chat().model(),
                        "embeddingModel", updated.embedding().model(),
                        "rerankEnabled", updated.rerank().enabled()));
        return Result.ok(updated);
    }

    @PostMapping("/test")
    public Result<ModelTestVO> test(@Valid @RequestBody ModelTestRequest request) {
        return Result.ok(modelService.test(request.type()));
    }
}
