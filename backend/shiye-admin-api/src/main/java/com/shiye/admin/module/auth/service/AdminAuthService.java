package com.shiye.admin.module.auth.service;

import com.shiye.admin.module.auth.domain.dto.AdminLoginRequest;
import com.shiye.admin.module.auth.domain.vo.AdminSummary;
import com.shiye.admin.module.auth.domain.vo.AdminTokenResponse;

public interface AdminAuthService {

    AdminTokenResponse login(AdminLoginRequest request, String userAgent, String ip);

    AdminTokenResponse refresh(String refreshToken, String userAgent, String ip);

    void logout(long adminId, String refreshToken);

    AdminSummary me(long adminId);

    /**
     * 校验管理员仍然存在且启用，并返回最新角色。
     * 禁用或删除后，已签发的 access token 会在下一次请求立即失效。
     */
    AdminSummary authenticate(long adminId);
}
