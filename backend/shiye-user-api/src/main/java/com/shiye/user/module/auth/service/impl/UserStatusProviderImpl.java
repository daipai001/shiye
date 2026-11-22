package com.shiye.user.module.auth.service.impl;

import com.shiye.user.common.security.UserStatusProvider;
import com.shiye.user.module.auth.mapper.UserMapper;
import org.springframework.stereotype.Service;

@Service
public class UserStatusProviderImpl implements UserStatusProvider {

    private final UserMapper userMapper;

    public UserStatusProviderImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public boolean isActive(long userId) {
        return "ACTIVE".equals(userMapper.findStatusById(userId));
    }
}
