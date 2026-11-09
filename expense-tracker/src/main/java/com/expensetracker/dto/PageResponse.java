package com.expensetracker.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Page JSON exactly as the contract (section 6.3) promises P3's table and pagination UI.
 * Returned instead of Spring's Page so the shape can never change with a library upgrade.
 */
public record PageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
