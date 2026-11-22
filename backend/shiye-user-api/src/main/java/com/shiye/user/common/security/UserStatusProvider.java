package com.shiye.user.common.security;

/**
 * 用户状态端口：认证过滤器通过它检查账号是否仍可用。
 */
public interface UserStatusProvider {

    boolean isActive(long userId);
}
