package com.shiye.user.module.usage.domain.vo;

/**
 * 一次问答的配额预留：个人 + 团队 + 全站预算。id 对应 usage_reservations。
 */
public record ChatReservation(
        Long id,
        UserReservation user,
        TeamReservation team,
        GlobalReservation global
) {
}
