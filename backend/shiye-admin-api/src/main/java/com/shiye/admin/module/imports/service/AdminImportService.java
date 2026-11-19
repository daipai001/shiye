package com.shiye.admin.module.imports.service;

import com.shiye.admin.module.imports.domain.vo.AdminImportItemVO;
import com.shiye.admin.module.imports.domain.vo.AdminImportRetryResponse;
import com.shiye.admin.module.imports.domain.vo.AdminImportStatusResponse;
import com.shiye.admin.module.imports.domain.vo.AdminImportVO;
import com.shiye.common.api.PageResult;

import java.util.List;

public interface AdminImportService {

    PageResult<AdminImportVO> list(int page, int pageSize, Long userId, String status);

    AdminImportVO detail(long id);

    PageResult<AdminImportItemVO> items(long id, int page, int pageSize, String status);

    AdminImportRetryResponse retry(long id, List<Long> itemIds);

    AdminImportStatusResponse pause(long id);

    AdminImportStatusResponse resume(long id);

    AdminImportStatusResponse cancel(long id);
}
