package org.example.takecareofmyfridgebe.domain.product.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;

// keyword,type,minPrice,maxPrice는 모두 선택이다. 비우면 해당 조건 없이 조회한다.
// 가격 범위는 양 끝을 포함한다.
public record ProductListRequest(
        ProductSort sort,
        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.") String keyword,
        ProductType type,
        @Min(value = 0, message = "최소 가격은 0원 이상이어야 합니다.") Integer minPrice,
        @Min(value = 0, message = "최대 가격은 0원 이상이어야 합니다.") Integer maxPrice,
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size
) {
    public ProductListRequest {
        sort = sort == null ? ProductSort.LATEST : sort;
        keyword = keyword == null || keyword.isBlank() ? null : keyword.strip();
        page = page == null ? 1 : page;
        size = size == null ? 20 : size;
    }

    @AssertTrue(message = "최소 가격은 최대 가격보다 클 수 없습니다.")
    private boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }
}
