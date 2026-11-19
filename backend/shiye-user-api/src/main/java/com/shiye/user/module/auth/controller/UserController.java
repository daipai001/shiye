package com.shiye.user.module.auth.controller;

import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.auth.domain.vo.UserSummary;
import com.shiye.user.module.auth.service.AuthService;
import com.shiye.user.module.auth.domain.dto.UserPasswordUpdateRequest;
import com.shiye.user.module.auth.domain.dto.UserProfileUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public Result<UserSummary> me() {
        return Result.ok(authService.me(UserContext.requireUserId()));
    }

    @PatchMapping("/me")
    public Result<UserSummary> updateProfile(
            @Valid @RequestBody UserProfileUpdateRequest request) {
        return Result.ok(authService.updateProfile(UserContext.requireUserId(), request));
    }

    @PostMapping("/me/password")
    public Result<Void> changePassword(
            @Valid @RequestBody UserPasswordUpdateRequest request) {
        authService.changePassword(UserContext.requireUserId(), request);
        return Result.ok();
    }
}
