package com.shiye.admin.module.teams.service;

import com.shiye.admin.module.teams.domain.vo.AdminTeamAuditVO;
import com.shiye.common.api.PageResult;

import java.time.LocalDateTime;

public interface AdminTeamAuditService {

    PageResult<AdminTeamAuditVO> list(int page, int pageSize, Long teamId, Long operatorId,
                                      String action, LocalDateTime from, LocalDateTime to);
}
