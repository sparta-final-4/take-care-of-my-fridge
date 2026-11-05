package org.example.takecareofmyfridgebe.domain.sale.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.PopularSaleList;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.service.PopularSaleService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductClickCount;
import org.example.takecareofmyfridgebe.domain.search.service.SearchClickMetricService;
import org.example.takecareofmyfridgebe.dummy.ProductFixture;
import org.example.takecareofmyfridgebe.global.config.CacheConfig;
import org.example.takecareofmyfridgebe.global.config.CacheNames;
import org.example.takecareofmyfridgebe.global.config.RedisConfig;
import org.example.takecareofmyfridgebe.support.RedisContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// 인기 특가 캐시를 실제 Redis에 넣고 꺼내 본다. 순위 규칙은 PopularSaleServiceTest가 확인한다.
@SpringBootTest(classes = {
        RedisConfig.class,
        CacheConfig.class,
        SaleEventFacade.class,
        PopularSaleService.class
})
@ImportAutoConfiguration({
        DataRedisAutoConfiguration.class,
        CacheAutoConfiguration.class
})
class SaleEventFacadeCacheIntegrationTest extends RedisContainerSupport {

    private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 11, 9, 0, 0);

    @MockitoBean
    private SaleEventReader saleEventReader;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private SearchClickMetricService searchClickMetricService;

    @Autowired
    private SaleEventFacade saleEventFacade;

    @Autowired
    private CacheManager cacheManager;

    // clear()는 Redis에서 비동기로 지워 다음 테스트가 넣은 값까지 늦게 지울 수 있어, 바로 지우는 invalidate()를 쓴다.
    @BeforeEach
    void clearCache() {
        cacheManager.getCache(CacheNames.POPULAR_SALES).invalidate();
    }

    @Test
    @DisplayName("두 번째 호출은 캐시에서 꺼내므로 진행 중 할인·상품·검색 클릭을 다시 읽지 않는다")
    void readsOnceWhenCalledTwice() {
        // given
        givenOneActiveSale();

        // when
        saleEventFacade.getPopularSales();
        awaitCached();
        saleEventFacade.getPopularSales();

        // then
        verify(saleEventReader, times(1)).findAllActiveSales();
        verify(productService, times(1)).getProductsByIds(anyList());
        verify(searchClickMetricService, times(1)).getProductClicks(any(), any(), any());
    }

    @Test
    @DisplayName("캐시에서 꺼낸 결과는 첫 호출과 같고, 종료 시각이 +09:00으로 남는다")
    void keepsSameResultFromCache() {
        // given
        givenOneActiveSale();

        // when
        PopularSaleList first = saleEventFacade.getPopularSales();
        awaitCached();
        PopularSaleList cached = saleEventFacade.getPopularSales();

        // then
        assertThat(cached).isEqualTo(first);
        assertThat(cached.sales()).hasSize(1);
        assertThat(cached.sales().get(0).sale().endsAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    }

    @Test
    @DisplayName("진행 중 할인이 없는 빈 결과도 캐시해 두 번째 호출은 진행 중 할인을 다시 읽지 않는다")
    void cachesEmptyResult() {
        // given
        when(saleEventReader.findAllActiveSales()).thenReturn(Map.of());

        // when
        PopularSaleList first = saleEventFacade.getPopularSales();
        awaitCached();
        PopularSaleList cached = saleEventFacade.getPopularSales();

        // then
        assertThat(first.sales()).isEmpty();
        assertThat(cached.sales()).isEmpty();
        verify(saleEventReader, times(1)).findAllActiveSales();
        verify(searchClickMetricService, never()).getProductClicks(any(), any(), any());
    }

    // 캐시에 넣는 일은 Redis에 비동기로 쓰므로, 값이 들어간 뒤에 두 번째로 호출한다.
    private void awaitCached() {
        await().atMost(Duration.ofSeconds(2))
                .until(() -> cacheManager.getCache(CacheNames.POPULAR_SALES).get("all") != null);
    }

    private void givenOneActiveSale() {
        ActiveSale sale = new ActiveSale(1L, 100L, SaleEventType.SPECIAL, 1600, 20, ENDS_AT, null, null);
        when(saleEventReader.findAllActiveSales()).thenReturn(Map.of(1L, sale));
        when(productService.getProductsByIds(List.of(1L)))
                .thenReturn(List.of(ProductFixture.createProduct(1L, "두부", 2000)));
        when(searchClickMetricService.getProductClicks(any(), any(), any()))
                .thenReturn(List.of(new ProductClickCount(1L, 5L)));
    }
}
