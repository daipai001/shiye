package com.shiye.user.module.team.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiye.common.web.ClientIpResolver;
import com.shiye.user.module.team.mapper.TeamAuditLogMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 团队操作审计：邀请、加入、移除、退出、角色变更、转让、解散、资料共享。
 * 与业务操作处于同一事务；流水表缺失时降级为错误日志，不阻塞团队操作。
 */
@Service
public class TeamAuditService {

    private static final Logger log = LoggerFactory.getLogger(TeamAuditService.class);

    private final TeamAuditLogMapper auditMapper;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final Counter missingTableFailures;
    private final Counter serializeFailures;

    public TeamAuditService(TeamAuditLogMapper auditMapper, ObjectMapper objectMapper,
                            ClientIpResolver clientIpResolver, MeterRegistry meterRegistry) {
        this.auditMapper = auditMapper;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.missingTableFailures = failureCounter(meterRegistry, "missing_table");
        this.serializeFailures = failureCounter(meterRegistry, "serialize");
    }

    public void record(long teamId, long operatorId, String action,
                       String targetType, Object targetId, Object detail) {
        try {
            String json = detail == null ? null : objectMapper.writeValueAsString(detail);
            HttpServletRequest request = currentRequest();
            String ip = request == null ? null : clientIpResolver.resolve(request);
            String userAgent = request == null ? null : request.getHeader("User-Agent");
            auditMapper.insert(
                    teamId,
                    operatorId,
                    truncate(action, 64),
                    truncate(targetType, 32),
                    targetId == null ? null : truncate(String.valueOf(targetId), 64),
                    json,
                    truncate(ip, 45),
                    truncate(userAgent, 512)
            );
        } catch (BadSqlGrammarException ex) {
            missingTableFailures.increment();
            log.error("team_audit_logs missing, run migration "
                    + "2026-11-27-team-audit.sql: {}", ex.getMessage());
        } catch (JsonProcessingException ex) {
            serializeFailures.increment();
            log.error("serialize team audit detail failed: {}", ex.getMessage());
        }
    }

    private Counter failureCounter(MeterRegistry meterRegistry, String reason) {
        return Counter.builder("shiye.team.audit.write_failures")
                .tag("reason", reason)
                .register(meterRegistry);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
