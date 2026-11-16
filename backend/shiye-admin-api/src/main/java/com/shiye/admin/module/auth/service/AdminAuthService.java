package com.shiye.admin.module.auth.service;

import com.shiye.admin.module.auth.domain.dto.AdminLoginRequest;
import com.shiye.admin.module.auth.domain.vo.AdminSummary;
import com.shiye.admin.module.auth.domain.vo.AdminTokenResponse;

public interface AdminAuthService {

    AdminTokenResponse login(AdminLoginRequest request, String userAgent, String ip);

    AdminTokenResponse refresh(String refreshToken, String userAgent, String ip);

    void logout(long adminId, String refreshToken);

    AdminSummary me(long adminId);
}
