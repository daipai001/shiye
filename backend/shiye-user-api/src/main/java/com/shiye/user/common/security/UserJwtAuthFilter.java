package com.shiye.user.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.Result;
import com.shiye.common.exception.BizException;
import com.shiye.common.security.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class UserJwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;
    private final UserStatusProvider userStatusProvider;

    public UserJwtAuthFilter(JwtTokenService jwtTokenService, ObjectMapper objectMapper,
                             UserStatusProvider userStatusProvider) {
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
        this.userStatusProvider = userStatusProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path.equals("/api/v1/health")
                || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh")
                || path.equals("/api/v1/auth/extension-token")
                || path.equals("/api/v1/auth/email/verify")
                || path.equals("/api/v1/auth/password/forgot")
                || path.equals("/api/v1/auth/password/reset")
                || path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            writeError(response, ErrorCode.UNAUTHORIZED);
            return;
        }
        try {
            JwtTokenService.JwtPrincipal principal = jwtTokenService.parse(authorization.substring(7));
            if (!"USER".equals(principal.role())) {
                writeError(response, ErrorCode.INVALID_TOKEN_AUDIENCE);
                return;
            }
            if (!userStatusProvider.isActive(principal.subjectId())) {
                writeError(response, ErrorCode.USER_DISABLED);
                return;
            }
            UserContext.set(new UserContext.UserPrincipal(principal.subjectId(), principal.role()));
            filterChain.doFilter(request, response);
        } catch (BizException ex) {
            writeError(response, ex.getErrorCode());
        } finally {
            UserContext.clear();
        }
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(errorCode));
    }
}
