package com.shiye.common.api;

import java.util.List;

/**
 * 统一分页结构。
 */
public record PageResult<T>(
        List<T> items,
        long total,
        int page,
        int pageSize
) {

    public static <T> PageResult<T> of(List<T> items, long total, int page, int pageSize) {
        return new PageResult<>(items, total, page, pageSize);
    }

    public static <T> PageResult<T> empty(int page, int pageSize) {
        return new PageResult<>(List.of(), 0, page, pageSize);
    }
}
