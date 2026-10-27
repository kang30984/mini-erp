package io.github.kang30984.minierp.common;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalCount,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> items, PageParams params, long totalCount) {
        int totalPages = (int) Math.ceil((double) totalCount / params.size());
        return new PageResponse<>(items, params.page(), params.size(), totalCount, totalPages);
    }
}
