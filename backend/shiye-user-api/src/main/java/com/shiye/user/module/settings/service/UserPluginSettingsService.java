package com.shiye.user.module.settings.service;

import com.shiye.user.module.settings.domain.dto.PluginSettingsUpdateRequest;
import com.shiye.user.module.settings.domain.vo.PluginSettingsVO;

public interface UserPluginSettingsService {

    PluginSettingsVO get(long userId);

    PluginSettingsVO update(long userId, PluginSettingsUpdateRequest request);
}
