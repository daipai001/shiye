package com.shiye.admin.common.security;

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
public class AdminJwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;
    private final AdminRateLimitService rateLimitService;

    public AdminJwtAuthFilter(JwtTokenService jwtTokenService, ObjectMapper objectMapper,
                              AdminRateLimitService rateLimitService) {
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
        this.rateLimitService = rateLimitService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path.equals("/admin/api/v1/health")
                || path.equals("/admin/api/v1/auth/login")
                || path.equals("/admin/api/v1/auth/refresh")
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
            if (!"admin".equals(principal.role()) && !"SUPER_ADMIN".equals(principal.role())) {
                writeError(response, ErrorCode.INVALID_TOKEN_AUDIENCE);
                return;
            }
            AdminContext.set(new AdminContext.AdminPrincipal(principal.subjectId(), principal.role()));
            if (!allowRequest(response, principal.subjectId())) {
                return;
            }
            filterChain.doFilter(request, response);
        } catch (BizException ex) {
            writeError(response, ex.getErrorCode());
        } finally {
            AdminContext.clear();
        }
    }

    private boolean allowRequest(HttpServletResponse response, long adminId) throws IOException {
        AdminRateLimitService.RateLimitResult result = rateLimitService.hit(adminId);
        if (result == null) {
            return true;
        }
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetEpochSeconds()));
        if (result.exceeded()) {
            writeError(response, ErrorCode.RATE_LIMITED);
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.httpStatus());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.fail(errorCode));
    }
}
