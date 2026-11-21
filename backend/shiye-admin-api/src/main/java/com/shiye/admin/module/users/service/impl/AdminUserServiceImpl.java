package com.shiye.admin.module.users.service.impl;

import com.shiye.admin.module.users.domain.dto.AdminUserQuotaRequest;
import com.shiye.admin.module.users.domain.dto.AdminUserPasswordResetRequest;
import com.shiye.admin.module.users.domain.dto.AdminUserStatusRequest;
import com.shiye.admin.module.users.domain.entity.AdminUserRowEntity;
import com.shiye.admin.module.users.domain.vo.AdminUserVO;
import com.shiye.admin.module.teams.mapper.AdminTeamMapper;
import com.shiye.admin.module.users.mapper.AdminUserQueryMapper;
import com.shiye.admin.module.users.service.AdminUserService;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.PageResult;
import com.shiye.common.exception.BizException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserQueryMapper userMapper;
    private final AdminTeamMapper teamMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserServiceImpl(AdminUserQueryMapper userMapper,
                                AdminTeamMapper teamMapper,
                                PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.teamMapper = teamMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PageResult<AdminUserVO> list(int page, int pageSize, String q, String status, String sort) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String qFilter = q == null || q.isBlank() ? null : q.trim();
        String statusFilter = normalizeStatus(status);
        String sortFilter = "usage".equalsIgnoreCase(sort) ? "usage" : null;
        long total = userMapper.count(qFilter, statusFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<AdminUserVO> items = userMapper
                .findPage(qFilter, statusFilter, sortFilter, currentMonth(),
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(AdminUserVO::from)
                .toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    public AdminUserVO detail(long id) {
        return AdminUserVO.from(findUser(id));
    }

    @Override
    @Transactional
    public AdminUserVO updateStatus(long id, AdminUserStatusRequest request) {
        AdminUserRowEntity user = findUser(id);
        String status = request.status().toUpperCase(Locale.ROOT);
        if ("DISABLED".equals(status) && teamMapper.countActiveOwnedBy(id) > 0) {
            throw new BizException(ErrorCode.CONFLICT,
                    "该用户仍拥有团队，请先转让所有权或解散团队");
        }
        userMapper.updateStatus(user.getId(), status);
        if ("DISABLED".equals(status)) {
            userMapper.revokeUserTokens(user.getId());
        }
        return AdminUserVO.from(findUser(id));
    }

    @Override
    @Transactional
    public AdminUserVO updateQuota(long id, AdminUserQuotaRequest request) {
        findUser(id);
        userMapper.updateQuota(id, request.monthlyTokenQuota(), request.monthlyCostQuota());
        return AdminUserVO.from(findUser(id));
    }

    @Override
    @Transactional
    public void resetPassword(long id, AdminUserPasswordResetRequest request) {
        findUser(id);
        userMapper.updatePassword(id, passwordEncoder.encode(request.newPassword()));
        userMapper.revokeUserTokens(id);
    }

    private AdminUserRowEntity findUser(long id) {
        AdminUserRowEntity user = userMapper.findById(id, currentMonth());
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String value = status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(value) && !"DISABLED".equals(value)) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "status 仅支持 ACTIVE / DISABLED");
        }
        return value;
    }

    private String currentMonth() {
        return YearMonth.now(ZoneOffset.UTC).toString();
    }
}
