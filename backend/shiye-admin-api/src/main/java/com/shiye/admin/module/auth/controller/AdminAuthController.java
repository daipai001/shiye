package com.shiye.admin.module.auth.controller;

import com.shiye.admin.common.security.AdminContext;
import com.shiye.admin.module.auth.domain.dto.AdminLoginRequest;
import com.shiye.admin.module.auth.domain.dto.AdminLogoutRequest;
import com.shiye.admin.module.auth.domain.dto.AdminRefreshRequest;
import com.shiye.admin.module.auth.domain.vo.AdminSummary;
import com.shiye.admin.module.auth.domain.vo.AdminTokenResponse;
import com.shiye.admin.module.auth.service.AdminAuthService;
import com.shiye.common.api.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/v1/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public Result<AdminTokenResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                            HttpServletRequest httpRequest) {
        return Result.ok(adminAuthService.login(request, userAgent(httpRequest), ip(httpRequest)));
    }

    @PostMapping("/refresh")
    public Result<AdminTokenResponse> refresh(@Valid @RequestBody AdminRefreshRequest request,
                                              HttpServletRequest httpRequest) {
        return Result.ok(adminAuthService.refresh(
                request.refreshToken(),
                userAgent(httpRequest),
                ip(httpRequest)
        ));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) AdminLogoutRequest request) {
        adminAuthService.logout(AdminContext.requireAdminId(), request == null ? null : request.refreshToken());
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<AdminSummary> me() {
        return Result.ok(adminAuthService.me(AdminContext.requireAdminId()));
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    private String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
