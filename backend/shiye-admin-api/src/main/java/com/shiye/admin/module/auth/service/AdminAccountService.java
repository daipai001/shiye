package com.shiye.admin.module.auth.service;

import com.shiye.admin.module.auth.domain.dto.AdminAccountCreateRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountPasswordResetRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountStatusRequest;
import com.shiye.admin.module.auth.domain.dto.AdminAccountUpdateRequest;
import com.shiye.admin.module.auth.domain.vo.AdminAccountVO;

import java.util.List;

public interface AdminAccountService {

    List<AdminAccountVO> list();

    AdminAccountVO create(AdminAccountCreateRequest request);

    AdminAccountVO update(long actorId, long id, AdminAccountUpdateRequest request);

    AdminAccountVO updateStatus(long actorId, long id, AdminAccountStatusRequest request);

    void resetPassword(long id, AdminAccountPasswordResetRequest request);
}
