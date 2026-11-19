package com.shiye.admin.module.settings.service;

import com.shiye.admin.module.settings.domain.dto.AdminBudgetUpdateRequest;
import com.shiye.admin.module.settings.domain.dto.AdminSettingsUpdateRequest;
import com.shiye.admin.module.settings.domain.vo.AdminSettingsVO;
import com.shiye.common.settings.SystemSettings;

public interface AdminSettingsService {

    AdminSettingsVO get();

    AdminSettingsVO update(AdminSettingsUpdateRequest request, long adminId);

    AdminSettingsVO updateBudget(AdminBudgetUpdateRequest request, long adminId);

    SystemSettings current();
}
