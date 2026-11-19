package com.shiye.user.module.settings.controller;

import com.shiye.common.api.Result;
import com.shiye.user.common.security.UserContext;
import com.shiye.user.module.settings.domain.dto.PluginSettingsUpdateRequest;
import com.shiye.user.module.settings.domain.vo.PluginSettingsVO;
import com.shiye.user.module.settings.service.UserPluginSettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/plugin-settings")
public class UserPluginSettingsController {

    private final UserPluginSettingsService settingsService;

    public UserPluginSettingsController(UserPluginSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public Result<PluginSettingsVO> get() {
        return Result.ok(settingsService.get(UserContext.requireUserId()));
    }

    @PutMapping
    public Result<PluginSettingsVO> update(
            @Valid @RequestBody PluginSettingsUpdateRequest request) {
        return Result.ok(settingsService.update(UserContext.requireUserId(), request));
    }
}
