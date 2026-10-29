package org.example.takecareofmyfridgebe.domain.search.facade;

import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.facade.ProductFacade;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductSearchResult;
import org.example.takecareofmyfridgebe.domain.search.service.ProductSearchService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchFacadeTest {

    @Mock
    private ProductFacade productFacade;
    @Mock
    private ProductSearchService productSearchService;
    @Mock
    private RecipeSearchProperties searchProperties;
    @InjectMocks
    private ProductSearchFacade facade;

    @SuppressWarnings("unchecked")
    private final PageResponse<ProductListItemResponse> page = mock(PageResponse.class);

    private ProductListRequest request(String keyword) {
        return new ProductListRequest(null, keyword, null, null, null, null, null);
    }

    @Test
    @DisplayName("검색 엔진이 MySQL이면 기존 LIKE 검색을 쓴다")
    void usesMySqlWhenEngineIsMySql() throws IOException {
        // Given
        ProductListRequest request = request("대파");
        when(searchProperties.useElasticSearch()).thenReturn(false);
        when(productFacade.getProducts(request)).thenReturn(page);

        // When & Then
        assertSame(page, facade.getProducts(request));
        verify(productSearchService, never()).search(any());
    }

    @Test
    @DisplayName("검색어가 없으면 ES 엔진이어도 기존 목록 조회를 쓴다")
    void usesMySqlWithoutKeyword() throws IOException {
        // Given
        ProductListRequest request = request(null);
        when(productFacade.getProducts(request)).thenReturn(page);

        // When & Then
        assertSame(page, facade.getProducts(request));
        verify(productSearchService, never()).search(any());
    }

    @Test
    @DisplayName("ES 엔진이고 검색어가 있으면 ES가 고른 ID 순서와 건수로 목록을 만든다")
    void usesElasticsearch() throws IOException {
        // Given
        ProductListRequest request = request("대파");
        when(searchProperties.useElasticSearch()).thenReturn(true);
        when(productSearchService.search(request)).thenReturn(new ProductSearchResult(List.of(9L, 4L), 7));
        when(productFacade.getProductsByIds(List.of(9L, 4L), 7, request)).thenReturn(page);

        // When & Then
        assertSame(page, facade.getProducts(request));
        verify(productFacade, never()).getProducts(any());
    }

    @Test
    @DisplayName("ES 검색이 실패하면 MySQL 검색으로 대체한다")
    void fallsBackToMySqlOnFailure() throws IOException {
        // Given
        ProductListRequest request = request("대파");
        when(searchProperties.useElasticSearch()).thenReturn(true);
        when(productSearchService.search(request)).thenThrow(new IOException("ES 연결 실패"));
        when(productFacade.getProducts(request)).thenReturn(page);

        // When & Then
        assertSame(page, facade.getProducts(request));
        verify(productFacade, never()).getProductsByIds(anyList(), anyLong(), any());
    }
}
