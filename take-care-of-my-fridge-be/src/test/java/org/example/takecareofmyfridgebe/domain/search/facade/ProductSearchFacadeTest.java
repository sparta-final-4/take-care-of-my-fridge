package org.example.takecareofmyfridgebe.domain.search.facade;

import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.facade.ProductFacade;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductSearchResult;
import org.example.takecareofmyfridgebe.domain.search.entity.SearchTarget;
import org.example.takecareofmyfridgebe.domain.search.service.ProductSearchService;
import org.example.takecareofmyfridgebe.domain.search.service.SearchZeroResultService;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private SearchZeroResultService searchZeroResultService;
    @InjectMocks
    private ProductSearchFacade facade;

    @SuppressWarnings("unchecked")
    private final PageResponse<ProductListItemResponse> page = mock(PageResponse.class);

    private ProductListRequest request(String keyword) {
        return new ProductListRequest(null, keyword, null, null, null, null, null);
    }

    @Test
    @DisplayName("검색 엔진이 MySQL이면 기존 LIKE 검색을 쓴다")
    void getProducts_usesMySql_whenEngineIsMySql() throws IOException {
        // Given
        ProductListRequest request = request("대파");
        when(searchProperties.useElasticSearch()).thenReturn(false);
        when(productFacade.getProducts(request)).thenReturn(page);

        // When & Then
        assertThat(facade.getProducts(request)).isSameAs(page);
        verify(productSearchService, never()).search(any());
    }

    @Test
    @DisplayName("검색어가 없으면 ES 엔진이어도 기존 목록 조회를 쓴다")
    void getProducts_usesMySql_whenKeywordMissing() throws IOException {
        // Given
        ProductListRequest request = request(null);
        when(productFacade.getProducts(request)).thenReturn(page);

        // When & Then
        assertThat(facade.getProducts(request)).isSameAs(page);
        verify(productSearchService, never()).search(any());
    }

    @Test
    @DisplayName("ES 엔진이고 검색어가 있으면 ES가 고른 ID 순서와 건수로 목록을 만든다")
    void getProducts_usesElasticsearch_whenKeywordPresent() throws IOException {
        // Given
        ProductListRequest request = request("대파");
        when(searchProperties.useElasticSearch()).thenReturn(true);
        when(productSearchService.search(request)).thenReturn(new ProductSearchResult(List.of(9L, 4L), 7));
        when(productFacade.getProductsByIds(List.of(9L, 4L), 7, request)).thenReturn(page);

        // When & Then
        assertThat(facade.getProducts(request)).isSameAs(page);
        verify(productFacade, never()).getProducts(any());
    }

    @Test
    @DisplayName("ES 검색이 실패하면 MySQL 검색으로 대체한다")
    void getProducts_fallsBackToMySql_whenEsFails() throws IOException {
        // Given
        ProductListRequest request = request("대파");
        when(searchProperties.useElasticSearch()).thenReturn(true);
        when(productSearchService.search(request)).thenThrow(new IOException("ES 연결 실패"));
        when(productFacade.getProducts(request)).thenReturn(page);

        // When & Then
        assertThat(facade.getProducts(request)).isSameAs(page);
        verify(productFacade, never()).getProductsByIds(anyList(), anyLong(), any());
    }

    @Test
    @DisplayName("필터 없이 첫 페이지 결과가 0건이면 검색어를 0건 검색어로 기록한다")
    void getProducts_recordsZeroResult_whenNoHits() {
        // Given
        ProductListRequest request = request("대파");
        when(productFacade.getProducts(request)).thenReturn(emptyPage(1));

        // When
        facade.getProducts(request);

        // Then
        verify(searchZeroResultService).record(SearchTarget.PRODUCT, "대파");
    }

    @Test
    @DisplayName("결과가 있으면 0건 검색어로 기록하지 않는다")
    void getProducts_skipsZeroResult_whenHitsExist() {
        // Given
        ProductListRequest request = request("대파");
        when(productFacade.getProducts(request)).thenReturn(new PageResponse<>(List.of(), 1, 20, 3, 1, true));

        // When
        facade.getProducts(request);

        // Then
        verifyNoInteractions(searchZeroResultService);
    }

    @Test
    @DisplayName("상품 종류 필터를 걸어 0건이면 기록하지 않는다")
    void getProducts_skipsZeroResult_whenFiltered() {
        // Given
        ProductListRequest request = new ProductListRequest(null, "대파", ProductType.INGREDIENT, null, null, null, null);
        when(productFacade.getProducts(request)).thenReturn(emptyPage(1));

        // When
        facade.getProducts(request);

        // Then
        verifyNoInteractions(searchZeroResultService);
    }

    @Test
    @DisplayName("2페이지 이후 요청이 0건이면 기록하지 않는다")
    void getProducts_skipsZeroResult_whenNotFirstPage() {
        // Given
        ProductListRequest request = new ProductListRequest(null, "대파", null, null, null, 2, null);
        when(productFacade.getProducts(request)).thenReturn(emptyPage(2));

        // When
        facade.getProducts(request);

        // Then
        verifyNoInteractions(searchZeroResultService);
    }

    private PageResponse<ProductListItemResponse> emptyPage(int pageNumber) {
        return new PageResponse<>(List.of(), pageNumber, 20, 0, 0, true);
    }
}
