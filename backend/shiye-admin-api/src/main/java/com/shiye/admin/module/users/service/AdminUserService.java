package com.shiye.admin.module.users.service;

import com.shiye.admin.module.users.domain.dto.AdminUserQuotaRequest;
import com.shiye.admin.module.users.domain.dto.AdminUserPasswordResetRequest;
import com.shiye.admin.module.users.domain.dto.AdminUserStatusRequest;
import com.shiye.admin.module.users.domain.vo.AdminUserVO;
import com.shiye.common.api.PageResult;

public interface AdminUserService {

    PageResult<AdminUserVO> list(int page, int pageSize, String q, String status, String sort);

    AdminUserVO detail(long id);

    AdminUserVO updateStatus(long id, AdminUserStatusRequest request);

    AdminUserVO updateQuota(long id, AdminUserQuotaRequest request);

    void resetPassword(long id, AdminUserPasswordResetRequest request);
}
