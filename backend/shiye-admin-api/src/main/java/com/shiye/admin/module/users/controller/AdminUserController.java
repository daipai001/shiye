package com.shiye.admin.module.users.controller;

import com.shiye.admin.module.users.domain.dto.AdminUserQuotaRequest;
import com.shiye.admin.module.users.domain.dto.AdminUserStatusRequest;
import com.shiye.admin.module.users.domain.vo.AdminUserVO;
import com.shiye.admin.module.users.service.AdminUserService;
import com.shiye.common.api.PageResult;
import com.shiye.common.api.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public Result<PageResult<AdminUserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort) {
        return Result.ok(adminUserService.list(page, pageSize, q, status, sort));
    }

    @GetMapping("/{id}")
    public Result<AdminUserVO> detail(@PathVariable long id) {
        return Result.ok(adminUserService.detail(id));
    }

    @PutMapping("/{id}/status")
    public Result<AdminUserVO> status(@PathVariable long id,
                                      @Valid @RequestBody AdminUserStatusRequest request) {
        return Result.ok(adminUserService.updateStatus(id, request));
    }

    @PutMapping("/{id}/quota")
    public Result<AdminUserVO> quota(@PathVariable long id,
                                     @Valid @RequestBody AdminUserQuotaRequest request) {
        return Result.ok(adminUserService.updateQuota(id, request));
    }
}
