package org.example.takecareofmyfridgebe.domain.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;

public record AdminProductListRequest(
        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.") String keyword,
        ProductType type,
        ProductStatus status,
        @Min(value = 1, message = "페이지는 1 이상이어야 합니다.") Integer page,
        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.") Integer size
) {
    public ProductListRequest toProductListRequest() {
        return new ProductListRequest(null, keyword, type, null, null, page, size);
    }
}
