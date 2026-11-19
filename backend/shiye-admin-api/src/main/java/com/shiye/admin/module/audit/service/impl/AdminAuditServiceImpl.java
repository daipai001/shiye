package com.shiye.admin.module.audit.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.admin.common.security.AdminContext;
import com.shiye.admin.module.audit.domain.entity.AdminAuditLogRow;
import com.shiye.admin.module.audit.domain.vo.AdminAuditLogVO;
import com.shiye.admin.module.audit.mapper.AdminAuditMapper;
import com.shiye.admin.module.audit.service.AdminAuditService;
import com.shiye.common.api.PageResult;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminAuditServiceImpl implements AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAuditMapper auditMapper;
    private final ObjectMapper objectMapper;

    public AdminAuditServiceImpl(AdminAuditMapper auditMapper, ObjectMapper objectMapper) {
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void record(String action, String resourceType, String resourceId, Object detail) {
        try {
            String json = detail == null ? null : objectMapper.writeValueAsString(detail);
            HttpServletRequest request = currentRequest();
            String ip = request == null ? null : clientIp(request);
            String userAgent = request == null ? null : request.getHeader("User-Agent");
            auditMapper.insert(
                    AdminContext.requireAdminId(),
                    truncate(action, 64),
                    truncate(resourceType, 64),
                    truncate(resourceId, 64),
                    json,
                    truncate(ip, 45),
                    truncate(userAgent, 512)
            );
        } catch (Exception ex) {
            log.warn("write audit log failed: {}", ex.getMessage());
        }
    }

    @Override
    public PageResult<AdminAuditLogVO> list(int page, int pageSize, Long adminId,
                                            String action, LocalDateTime from,
                                            LocalDateTime to) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String actionFilter = action == null || action.isBlank() ? null : action.trim();
        long total = auditMapper.count(adminId, actionFilter, from, to);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<AdminAuditLogVO> items = auditMapper
                .findPage(adminId, actionFilter, from, to,
                        (safePage - 1) * safeSize, safeSize)
                .stream()
                .map(this::toVO)
                .toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    private AdminAuditLogVO toVO(AdminAuditLogRow row) {
        JsonNode detail = null;
        if (row.getDetail() != null && !row.getDetail().isBlank()) {
            try {
                detail = objectMapper.readTree(row.getDetail());
            } catch (Exception ignored) {
                // 历史脏数据不影响列表
            }
        }
        return AdminAuditLogVO.from(row, detail);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
