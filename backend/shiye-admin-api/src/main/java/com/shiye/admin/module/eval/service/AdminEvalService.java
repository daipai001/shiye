package com.shiye.admin.module.eval.service;

import com.shiye.admin.module.eval.domain.dto.EvalCaseCreateRequest;
import com.shiye.admin.module.eval.domain.dto.EvalRunCreateRequest;
import com.shiye.admin.module.eval.domain.vo.EvalCaseVO;
import com.shiye.admin.module.eval.domain.vo.EvalRunVO;
import com.shiye.common.api.PageResult;

public interface AdminEvalService {

    PageResult<EvalCaseVO> cases(int page, int pageSize, Long userId, String status);

    EvalCaseVO createCase(EvalCaseCreateRequest request);

    PageResult<EvalRunVO> runs(int page, int pageSize, Long userId);

    EvalRunVO createRun(EvalRunCreateRequest request);

    EvalRunVO run(long id);
}
