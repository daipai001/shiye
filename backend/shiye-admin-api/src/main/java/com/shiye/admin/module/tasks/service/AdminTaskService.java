package com.shiye.admin.module.tasks.service;

import com.shiye.admin.module.tasks.domain.vo.AdminTaskVO;
import com.shiye.common.api.PageResult;

public interface AdminTaskService {

    PageResult<AdminTaskVO> list(int page, int pageSize, Long userId, String status);

    PageResult<AdminTaskVO> listIngest(int page, int pageSize, Long userId, String status);

    AdminTaskVO detail(long id);

    AdminTaskVO retry(long id);
}
