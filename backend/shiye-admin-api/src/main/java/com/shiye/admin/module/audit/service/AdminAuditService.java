package com.shiye.admin.module.audit.service;

import com.shiye.common.api.PageResult;

import java.time.LocalDateTime;

public interface AdminAuditService {

    void record(String action, String resourceType, String resourceId, Object detail);

    PageResult<com.shiye.admin.module.audit.domain.vo.AdminAuditLogVO> list(
            int page, int pageSize, Long adminId, String action,
            LocalDateTime from, LocalDateTime to);
}
