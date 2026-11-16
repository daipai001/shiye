package com.shiye.admin.common.security;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;

public final class AdminContext {

    private static final ThreadLocal<AdminPrincipal> HOLDER = new ThreadLocal<>();

    private AdminContext() {
    }

    public static void set(AdminPrincipal principal) {
        HOLDER.set(principal);
    }

    public static AdminPrincipal get() {
        return HOLDER.get();
    }

    public static long requireAdminId() {
        AdminPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal.adminId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record AdminPrincipal(long adminId, String role) {
    }
}
