package org.example.takecareofmyfridgebe.domain.sale.facade;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.PopularSaleList;
import org.example.takecareofmyfridgebe.domain.sale.dto.PopularSaleResponse;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.service.PopularSaleService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductClickCount;
import org.example.takecareofmyfridgebe.domain.search.service.SearchClickMetricService;
import org.example.takecareofmyfridgebe.global.config.cache.CacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

// 일반 사용자 화면에 보여 줄 할인 값을 모은다. 진행 중인 할인은 할인 서비스에서, 상품은 상품 서비스에서, 검색 클릭은 검색 서비스에서 읽어 합친다.
@Component
@RequiredArgsConstructor
public class SaleEventFacade {

    private final SaleEventReader saleEventReader;
    private final PopularSaleService popularSaleService;
    private final ProductService productService;
    private final SearchClickMetricService searchClickMetricService;

    // 메인 화면 인기 특가. 진행 중인 할인, 상품, 최근 검색 클릭을 모아 인기 특가 서비스에 넘기고 특가 카드 모양으로 내려 준다.
    // 모든 사용자에게 같은 결과라 캐시한다. 진행 중 할인이 없는 빈 결과도 캐시한다.
    @Cacheable(cacheNames = CacheNames.POPULAR_SALES, key = "'all'")
    public PopularSaleList getPopularSales() {
        Map<Long, ActiveSale> salesByProductId = saleEventReader.findAllActiveSales();
        if (salesByProductId.isEmpty()) {
            return new PopularSaleList(List.of());
        }
        List<Product> products = productService.getProductsByIds(List.copyOf(salesByProductId.keySet()));
        LocalDateTime now = LocalDateTime.now(SaleEvent.ZONE);
        LocalDate today = now.toLocalDate();
        Map<Long, Long> clicksByProductId = searchClickMetricService.getProductClicks(popularSaleService.clickFrom(today), today, now)
                .stream()
                .collect(Collectors.toMap(ProductClickCount::productId, ProductClickCount::clicks));

        return new PopularSaleList(popularSaleService.rank(products, salesByProductId, clicksByProductId).stream()
                .map(PopularSaleResponse::from)
                .toList());
    }
}
