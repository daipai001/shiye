package com.shiye.user.module.auth.service;

import com.shiye.user.module.auth.domain.dto.LoginRequest;
import com.shiye.user.module.auth.domain.dto.RegisterRequest;
import com.shiye.user.module.auth.domain.dto.UserPasswordUpdateRequest;
import com.shiye.user.module.auth.domain.dto.UserProfileUpdateRequest;
import com.shiye.user.module.auth.domain.vo.ExtensionCodeResponse;
import com.shiye.user.module.auth.domain.vo.TokenResponse;
import com.shiye.user.module.auth.domain.vo.UserQuotaVO;
import com.shiye.user.module.auth.domain.vo.UserSummary;

public interface AuthService {

    TokenResponse register(RegisterRequest request, String userAgent, String ip);

    TokenResponse login(LoginRequest request, String userAgent, String ip);

    TokenResponse refresh(String refreshToken, String userAgent, String ip);

    void logout(long userId, String refreshToken);

    ExtensionCodeResponse issueExtensionCode(long userId);

    TokenResponse exchangeExtensionCode(String code, String clientInfo, String userAgent, String ip);

    UserSummary me(long userId);

    UserSummary findByEmail(String email);

    UserSummary updateProfile(long userId, UserProfileUpdateRequest request);

    void changePassword(long userId, UserPasswordUpdateRequest request);

    UserQuotaVO quota(long userId);
}
