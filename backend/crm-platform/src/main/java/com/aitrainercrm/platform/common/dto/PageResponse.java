package com.aitrainercrm.platform.common.dto;

import java.util.List;
import lombok.Builder;
import org.springframework.data.domain.Page;

/**
 * Flattened pagination envelope returned by every list endpoint, instead of
 * exposing Spring Data's {@link Page} (and its Pageable/Sort internals)
 * directly on the wire - keeps the API contract stable even if the
 * underlying paging implementation changes later.
 */
@Builder
public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return PageResponse.<T>builder()
                .content(page.getContent())
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    public static <T, R> PageResponse<R> from(Page<T> page, List<R> mappedContent) {
        return PageResponse.<R>builder()
                .content(mappedContent)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }
}
