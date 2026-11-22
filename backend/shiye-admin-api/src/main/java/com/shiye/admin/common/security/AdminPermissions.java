package com.shiye.admin.common.security;

import java.util.Locale;
import java.util.Set;

/**
 * 管理端角色与接口权限矩阵。
 *
 * <p>SUPER_ADMIN：全部权限；OPERATOR：除管理员、模型、系统设置外的读写；
 * VIEWER：只读（GET/HEAD）。</p>
 */
public final class AdminPermissions {

    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_OPERATOR = "OPERATOR";
    public static final String ROLE_VIEWER = "VIEWER";

    private static final Set<String> ROLES = Set.of(
            ROLE_SUPER_ADMIN, ROLE_OPERATOR, ROLE_VIEWER);

    private AdminPermissions() {
    }

    public static String normalize(String role) {
        if (role == null) {
            return "";
        }
        String value = role.trim().toUpperCase(Locale.ROOT);
        // 兼容早期 token 中的 "ADMIN"
        return "ADMIN".equals(value) ? ROLE_SUPER_ADMIN : value;
    }

    public static boolean isKnownRole(String role) {
        return ROLES.contains(normalize(role));
    }

    public static boolean isWriteMethod(String method) {
        if (method == null) {
            return true;
        }
        return !("GET".equalsIgnoreCase(method)
                || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method));
    }

    public static boolean allows(String role, String method, String path) {
        String normalized = normalize(role);
        if (!ROLES.contains(normalized)) {
            return false;
        }
        if (ROLE_SUPER_ADMIN.equals(normalized)) {
            return true;
        }
        String requestPath = path == null ? "" : path;
        if (requestPath.startsWith("/admin/api/v1/admins")) {
            return false;
        }
        if (requestPath.startsWith("/admin/api/v1/models")
                || requestPath.startsWith("/admin/api/v1/settings")
                || requestPath.startsWith("/admin/api/v1/budget")) {
            return false;
        }
        return !ROLE_VIEWER.equals(normalized) || !isWriteMethod(method);
    }
}
