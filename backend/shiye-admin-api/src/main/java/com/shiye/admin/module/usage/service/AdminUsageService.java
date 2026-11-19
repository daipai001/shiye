package com.shiye.admin.module.usage.service;

import com.shiye.admin.module.usage.domain.vo.AdminUsageResponse;

public interface AdminUsageService {

    AdminUsageResponse list(int page, int pageSize, Long userId, String source, String scene,
                            String model, String status, String from, String to);

    String exportCsv(Long userId, String source, String scene, String model,
                     String status, String from, String to);
}
