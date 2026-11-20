package com.shiye.admin.module.teams.service;

import com.shiye.admin.module.teams.domain.dto.AdminTeamQuotaRequest;
import com.shiye.admin.module.teams.domain.vo.AdminTeamVO;
import com.shiye.common.api.PageResult;

public interface AdminTeamService {

    PageResult<AdminTeamVO> list(int page, int pageSize, String q, String status);

    AdminTeamVO updateQuota(long id, AdminTeamQuotaRequest request);
}
