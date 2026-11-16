package com.shiye.user.module.auth.controller;

import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.auth.domain.dto.ExtensionTokenRequest;
import com.shiye.user.module.auth.domain.dto.LoginRequest;
import com.shiye.user.module.auth.domain.dto.LogoutRequest;
import com.shiye.user.module.auth.domain.dto.RefreshRequest;
import com.shiye.user.module.auth.domain.dto.RegisterRequest;
import com.shiye.user.module.auth.domain.vo.ExtensionCodeResponse;
import com.shiye.user.module.auth.domain.vo.TokenResponse;
import com.shiye.user.module.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                          HttpServletRequest httpRequest) {
        return Result.ok(authService.register(request, userAgent(httpRequest), ip(httpRequest)));
    }

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        return Result.ok(authService.login(request, userAgent(httpRequest), ip(httpRequest)));
    }

    @PostMapping("/refresh")
    public Result<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                         HttpServletRequest httpRequest) {
        return Result.ok(authService.refresh(request.refreshToken(), userAgent(httpRequest), ip(httpRequest)));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        authService.logout(UserContext.requireUserId(), request == null ? null : request.refreshToken());
        return Result.ok();
    }

    @PostMapping("/extension-code")
    public Result<ExtensionCodeResponse> extensionCode() {
        return Result.ok(authService.issueExtensionCode(UserContext.requireUserId()));
    }

    @PostMapping("/extension-token")
    public Result<TokenResponse> extensionToken(@Valid @RequestBody ExtensionTokenRequest request,
                                                HttpServletRequest httpRequest) {
        return Result.ok(authService.exchangeExtensionCode(
                request.code(),
                request.clientInfo(),
                userAgent(httpRequest),
                ip(httpRequest)
        ));
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
