package com.shiye.user.module.usage.domain.vo;

/**
 * 一次问答的配额预留：个人 + 可选团队。
 */
public record ChatReservation(
        UserReservation user,
        TeamReservation team
) {
}
