package org.example.takecareofmyfridgebe.domain.search.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.facade.ProductFacade;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductSearchResult;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchTarget;
import org.example.takecareofmyfridgebe.domain.search.service.ProductSearchService;
import org.example.takecareofmyfridgebe.domain.search.service.SearchZeroResultService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchFacade {

    private final ProductFacade productFacade;
    private final ProductSearchService productSearchService;
    private final RecipeSearchProperties searchProperties;
    private final SearchZeroResultService searchZeroResultService;

    /**
     * 상품 검색
     */
    public PageResponse<ProductListItemResponse> getProducts(ProductListRequest request) {
        PageResponse<ProductListItemResponse> response = search(request);
        if (response.totalElements() == 0 && isPlainFirstPage(request)) {
            searchZeroResultService.record(SearchTarget.PRODUCT, request.keyword());
        }
        return response;
    }

    private PageResponse<ProductListItemResponse> search(ProductListRequest request) {
        if (request.keyword() == null || !searchProperties.useElasticSearch()) {
            // mysql 조회
            return productFacade.getProducts(request);
        }

        ProductSearchResult result;
        try {
            // es 조회
            result = productSearchService.search(request);
        } catch (IOException | RuntimeException e) {
            // es 조회 실패 시 mysql 조회로 대체한다
            log.warn("ES 상품 검색 실패, MySQL 검색으로 대체: keyword={}", request.keyword(), e);
            return productFacade.getProducts(request);
        }

        return productFacade.getProductsByIds(result.productIds(), result.total(), request);
    }

    // 필터 때문에 0건이 된 경우와 2페이지 이후는 사전 보강 근거가 아니라서 기록하지 않는다
    private boolean isPlainFirstPage(ProductListRequest request) {
        return request.keyword() != null
                && request.page() == 1
                && request.type() == null
                && request.minPrice() == null
                && request.maxPrice() == null;
    }

}
