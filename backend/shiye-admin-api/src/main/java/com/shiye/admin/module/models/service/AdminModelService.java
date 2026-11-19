package com.shiye.admin.module.models.service;

import com.shiye.admin.module.models.domain.dto.AdminModelUpdateRequest;
import com.shiye.admin.module.models.domain.vo.AdminModelConfigVO;
import com.shiye.admin.module.models.domain.vo.ModelTestVO;

public interface AdminModelService {

    AdminModelConfigVO config();

    AdminModelConfigVO update(AdminModelUpdateRequest request, long adminId);

    ModelTestVO test(String type);
}
