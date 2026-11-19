package com.shiye.user.module.auth.controller;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.Result;
import com.shiye.common.exception.BizException;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.auth.domain.dto.ExtensionTokenRequest;
import com.shiye.user.module.auth.domain.dto.LoginRequest;
import com.shiye.user.module.auth.domain.dto.LogoutRequest;
import com.shiye.user.module.auth.domain.dto.RefreshRequest;
import com.shiye.user.module.auth.domain.dto.RegisterRequest;
import com.shiye.user.module.auth.domain.vo.ExtensionCodeResponse;
import com.shiye.user.module.auth.domain.vo.TokenResponse;
import com.shiye.user.module.auth.service.AuthRateLimitService;
import com.shiye.user.module.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final AuthRateLimitService rateLimitService;

    public AuthController(AuthService authService,
                          AuthRateLimitService rateLimitService) {
        this.authService = authService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register")
    public Result<TokenResponse> register(@Valid @RequestBody RegisterRequest request,
                                          HttpServletRequest httpRequest,
                                          HttpServletResponse httpResponse) {
        String ip = ip(httpRequest);
        applyRateLimit(httpResponse, ip);
        return Result.ok(authService.register(request, userAgent(httpRequest), ip));
    }

    @PostMapping("/login")
    public Result<TokenResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        String ip = ip(httpRequest);
        applyRateLimit(httpResponse, ip);
        return Result.ok(authService.login(request, userAgent(httpRequest), ip));
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

    private void applyRateLimit(HttpServletResponse response, String ip) {
        AuthRateLimitService.RateLimitResult result = rateLimitService.hit(ip);
        if (result == null) {
            return;
        }
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetEpochSeconds()));
        if (result.exceeded()) {
            throw new BizException(ErrorCode.RATE_LIMITED, "登录/注册过于频繁，请稍后再试");
        }
    }

    private String ip(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
