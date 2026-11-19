package com.shiye.admin.module.models.service;

import com.shiye.common.ai.ModelConfig;

public interface AdminModelSettingsService {

    ModelConfig current();

    ModelConfig save(ModelConfig config, Long adminId);

    boolean fromDatabase();
}
