package com.shiye.admin.module.tasks.service.impl;

import com.shiye.admin.module.tasks.domain.entity.AdminTaskRowEntity;
import com.shiye.admin.module.tasks.domain.vo.AdminTaskVO;
import com.shiye.admin.module.tasks.mapper.AdminTaskMapper;
import com.shiye.admin.module.tasks.service.AdminTaskService;
import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.PageResult;
import com.shiye.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
public class AdminTaskServiceImpl implements AdminTaskService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminTaskMapper taskMapper;

    public AdminTaskServiceImpl(AdminTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    @Override
    public PageResult<AdminTaskVO> list(int page, int pageSize, Long userId, String status) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String statusFilter = status == null || status.isBlank()
                ? null : status.trim().toUpperCase(Locale.ROOT);
        long total = taskMapper.count(userId, statusFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<AdminTaskVO> items = taskMapper
                .findPage(userId, statusFilter, (safePage - 1) * safeSize, safeSize)
                .stream().map(AdminTaskVO::from).toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    public PageResult<AdminTaskVO> listIngest(int page, int pageSize, Long userId, String status) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? 20 : Math.min(pageSize, MAX_PAGE_SIZE);
        String statusFilter = status == null || status.isBlank()
                ? null : status.trim().toUpperCase(Locale.ROOT);
        long total = taskMapper.countIngest(userId, statusFilter);
        if (total == 0) {
            return PageResult.empty(safePage, safeSize);
        }
        List<AdminTaskVO> items = taskMapper
                .findIngestPage(userId, statusFilter, (safePage - 1) * safeSize, safeSize)
                .stream().map(AdminTaskVO::from).toList();
        return PageResult.of(items, total, safePage, safeSize);
    }

    @Override
    public AdminTaskVO detail(long id) {
        return AdminTaskVO.from(findTask(id));
    }

    @Override
    @Transactional
    public AdminTaskVO retry(long id) {
        AdminTaskRowEntity task = findTask(id);
        if (!"FAILED".equals(task.getStatus())) {
            throw new BizException(ErrorCode.INVALID_STATUS_TRANSITION, "只有失败任务可以重试");
        }
        taskMapper.retryFailed(id);
        taskMapper.reopenImport(task.getImportId());
        return AdminTaskVO.from(findTask(id));
    }

    private AdminTaskRowEntity findTask(long id) {
        AdminTaskRowEntity task = taskMapper.findById(id);
        if (task == null) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }
}
