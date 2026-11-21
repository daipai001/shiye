package com.shiye.user.module.usage.domain.vo;

import java.math.BigDecimal;

/**
 * 团队问答配额预留：进入流式回答前先占用一部分 token/成本，结束后释放，
 * 避免并发请求同时通过检查导致明显超额。
 */
public record TeamReservation(
        long teamId,
        String month,
        long tokens,
        BigDecimal cost
) {
}
