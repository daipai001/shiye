package com.shiye.admin.module.usage.controller;

import com.shiye.admin.module.usage.domain.vo.AdminUsageResponse;
import com.shiye.admin.module.usage.service.AdminUsageService;
import com.shiye.common.api.Result;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/usage")
public class AdminUsageController {

    private final AdminUsageService usageService;

    public AdminUsageController(AdminUsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping
    public Result<AdminUsageResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Result.ok(usageService.list(page, pageSize, userId, source, scene,
                model, status, from, to));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> export(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        String csv = usageService.exportCsv(userId, source, scene, model, status, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"shiye-usage.csv\"")
                .body(csv);
    }
}
