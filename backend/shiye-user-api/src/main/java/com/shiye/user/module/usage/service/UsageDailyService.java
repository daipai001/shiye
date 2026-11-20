package com.shiye.user.module.usage.service;

import java.time.LocalDate;

public interface UsageDailyService {

    int aggregate(LocalDate date);

    int aggregateRecent(int days);
}
