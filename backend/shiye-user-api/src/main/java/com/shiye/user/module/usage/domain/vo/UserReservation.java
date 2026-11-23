package com.shiye.user.module.usage.domain.vo;

import java.math.BigDecimal;

/**
 * 个人月度配额预留：流式回答开始前占用一部分 token/成本，结束后释放。
 */
public record UserReservation(
        long userId,
        String month,
        long tokens,
        BigDecimal cost
) {
}
