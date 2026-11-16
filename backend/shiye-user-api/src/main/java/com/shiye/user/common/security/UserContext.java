package com.shiye.user.common.security;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.exception.BizException;

public final class UserContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    public static UserPrincipal get() {
        return HOLDER.get();
    }

    public static long requireUserId() {
        UserPrincipal principal = HOLDER.get();
        if (principal == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return principal.userId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public record UserPrincipal(long userId, String role) {
    }
}
