package org.example.takecareofmyfridgebe.global.response;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast
) {
    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber() + 1,
                page.getSize(), page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * 검색 엔진이 준 전체 건수를 그대로 쓰는 페이지. pageNumber는 1부터 센다.
     * <br>
     * PageImpl은 받은 개수가 페이지 크기보다 적으면 전체 건수를 다시 계산하므로,
     * DB에서 빠진 항목이 있는 마지막 페이지에서도 검색 건수를 지키도록 직접 만든다.
     */
    public static <T> PageResponse<T> of(List<T> content, int pageNumber, int pageSize, long total) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PageResponse<>(content, pageNumber, pageSize, total, totalPages, pageNumber >= totalPages);
    }
}
