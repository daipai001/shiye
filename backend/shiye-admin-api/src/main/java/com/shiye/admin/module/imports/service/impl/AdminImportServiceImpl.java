package com.shiye.admin.module.imports.service.impl;

import com.shiye.admin.module.imports.domain.entity.AdminImportRowEntity;
import com.shiye.admin.module.imports.domain.vo.AdminImportItemVO;
import com.shiye.admin.module.imports.domain.vo.AdminImportRetryResponse;
import com.shiye.admin.module.imports.domain.vo.AdminImportStatusResponse;
import com.shiye.admin.module.imports.domain.vo.AdminImportVO;
import com.shiye.admin.module.imports.mapper.AdminImportMapper;
import com.shiye.admin.module.imports.service.AdminImportService;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.PageResult;
import com.shiye.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AdminImportServiceImpl implements AdminImportService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminImportMapper importMapper;

    public AdminImportServiceImpl(AdminImportMapper importMapper) {
        this.importMapper = importMapper;
    }

    @Override
    public PageResult<AdminImportVO> list(int page, int pageSize, Long userId, String status) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String statusFilter = blankToNull(status);
        long total = importMapper.count(userId, statusFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<AdminImportVO> items = importMapper
                .findPage(userId, statusFilter, (safePage - 1) * safeSize, safeSize)
                .stream().map(AdminImportVO::from).toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    public AdminImportVO detail(long id) {
        return AdminImportVO.from(findImport(id));
    }

    @Override
    public PageResult<AdminImportItemVO> items(long id, int page, int pageSize, String status) {
        findImport(id);
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String statusFilter = blankToNull(status);
        long total = importMapper.countItems(id, statusFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<AdminImportItemVO> items = importMapper
                .findItems(id, statusFilter, (safePage - 1) * safeSize, safeSize)
                .stream().map(AdminImportItemVO::from).toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    @Transactional
    public AdminImportRetryResponse retry(long id, List<Long> itemIds) {
        AdminImportRowEntity entity = findImport(id);
        String status = entity.getStatus().toUpperCase(Locale.ROOT);
        if ("PENDING".equals(status) || "RUNNING".equals(status) || "PAUSED".equals(status)) {
            throw new BizException(ErrorCode.IMPORT_ALREADY_RUNNING, "任务尚未结束，不能重试");
        }
        int retried = importMapper.retryFailed(id, itemIds);
        if (retried > 0) {
            importMapper.markPending(id);
            importMapper.refreshCounts(id);
        }
        return new AdminImportRetryResponse(retried);
    }

    @Override
    @Transactional
    public AdminImportStatusResponse pause(long id) {
        AdminImportRowEntity entity = findImport(id);
        String status = entity.getStatus().toUpperCase(Locale.ROOT);
        if ("PAUSED".equals(status)) {
            return new AdminImportStatusResponse(status);
        }
        if (!"PENDING".equals(status) && !"RUNNING".equals(status)) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "当前状态不能暂停");
        }
        importMapper.updateStatus(id, "PAUSED");
        return new AdminImportStatusResponse("PAUSED");
    }

    @Override
    @Transactional
    public AdminImportStatusResponse resume(long id) {
        AdminImportRowEntity entity = findImport(id);
        String status = entity.getStatus().toUpperCase(Locale.ROOT);
        if ("RUNNING".equals(status)) {
            return new AdminImportStatusResponse(status);
        }
        if (!"PAUSED".equals(status)) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "只有暂停的任务可以恢复");
        }
        importMapper.markRunning(id);
        return new AdminImportStatusResponse("RUNNING");
    }

    @Override
    @Transactional
    public AdminImportStatusResponse cancel(long id) {
        AdminImportRowEntity entity = findImport(id);
        String status = entity.getStatus().toUpperCase(Locale.ROOT);
        if ("CANCELLED".equals(status)) {
            return new AdminImportStatusResponse(status);
        }
        if (!"PENDING".equals(status) && !"RUNNING".equals(status) && !"PAUSED".equals(status)) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "当前状态不能取消");
        }
        importMapper.cancelPending(id);
        importMapper.markFinished(id, "CANCELLED");
        importMapper.refreshCounts(id);
        return new AdminImportStatusResponse("CANCELLED");
    }

    private AdminImportRowEntity findImport(long id) {
        AdminImportRowEntity entity = importMapper.findById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "导入任务不存在");
        }
        return entity;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
