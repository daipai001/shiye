package com.shiye.user.module.usage.controller;

import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.usage.domain.vo.QuotaVO;
import com.shiye.user.module.usage.domain.vo.UsageRecordVO;
import com.shiye.user.module.usage.domain.vo.UsageSummaryVO;
import com.shiye.user.module.usage.service.UsageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/usage")
public class UsageController {

    private final UsageService usageService;

    public UsageController(UsageService usageService) {
        this.usageService = usageService;
    }

    @GetMapping("/quota")
    public Result<QuotaVO> quota() {
        return Result.ok(usageService.quota(UserContext.requireUserId()));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status) {
        String csv = usageService.exportCsv(
                UserContext.requireUserId(), source, scene, model, status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"shiye-usage.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/summary")
    public Result<UsageSummaryVO> summary(
            @RequestParam(defaultValue = "7") int days) {
        return Result.ok(usageService.summary(UserContext.requireUserId(), days));
    }

    @GetMapping("/records")
    public Result<PageResult<UsageRecordVO>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String scene,
            @RequestParam(required = false) String model,
            @RequestParam(required = false) String status) {
        return Result.ok(usageService.records(
                UserContext.requireUserId(), page, pageSize, source, scene, model, status));
    }
}
