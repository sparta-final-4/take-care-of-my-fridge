package org.example.takecareofmyfridgebe.domain.search.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.facade.ProductFacade;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductSearchResult;
import org.example.takecareofmyfridgebe.domain.search.service.ProductSearchService;
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

    /**
     * 상품 검색
     */
    public PageResponse<ProductListItemResponse> getProducts(ProductListRequest request) {
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

}
