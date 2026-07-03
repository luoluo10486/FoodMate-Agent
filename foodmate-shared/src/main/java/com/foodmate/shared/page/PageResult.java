package com.foodmate.shared.page;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * 分页结果封装。
 */
public record PageResult<T>(
        List<T> items,
        int page,
        @JsonProperty("page_size") int pageSize,
        long total
) {
    public static <T> PageResult<T> of(List<T> items, PageRequest request, long total) {
        return new PageResult<>(List.copyOf(items), request.page(), request.pageSize(), total);
    }
}
