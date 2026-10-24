package org.example.takecareofmyfridgebe.domain.sale.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;

// status, type, keyword(상품 이름), 기간, 정렬은 선택이다. 비우면 해당 조건 없이 조회한다.
// 시작일·종료일은 한쪽만 주면 그 하루만, 둘 다 주면 그 기간(양 끝 포함)을 찾는다.
// 정렬 기준을 비우면 최신 행사부터, 방향을 비우면 내림차순이다.
public record SaleEventListRequest(
        SaleEventStatus status,
        SaleEventType type,
        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.") String keyword,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endTo,
        SaleEventListSort sort,
        Sort.Direction direction,
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size
) {
    public SaleEventListRequest {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        startTo = startTo == null ? startFrom : startTo;
        endTo = endTo == null ? endFrom : endTo;
        direction = direction == null ? Sort.Direction.DESC : direction;
        page = page == null ? 1 : page;
        size = size == null ? 10 : size;
    }

    @AssertTrue(message = "시작일 검색의 끝 날짜는 시작 날짜보다 빠를 수 없습니다.")
    public boolean isStartRangeOrdered() {
        return startFrom == null || startTo == null || !startTo.isBefore(startFrom);
    }

    @AssertTrue(message = "종료일 검색의 끝 날짜는 시작 날짜보다 빠를 수 없습니다.")
    public boolean isEndRangeOrdered() {
        return endFrom == null || endTo == null || !endTo.isBefore(endFrom);
    }
}
