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
import org.example.takecareofmyfridgebe.domain.sale.dto.PopularSaleResponse;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.service.PopularSaleService;
import org.example.takecareofmyfridgebe.domain.search.dto.ProductClickCount;
import org.example.takecareofmyfridgebe.domain.search.service.SearchClickMetricService;
import org.springframework.stereotype.Component;

// 메인 화면 인기 특가. 진행 중인 할인, 상품, 최근 검색 클릭을 모아 인기 특가 서비스에 넘기고 특가 카드 모양으로 내려 준다.
@Component
@RequiredArgsConstructor
public class PopularSaleFacade {

    private final PopularSaleService popularSaleService;
    private final ProductService productService;
    private final SearchClickMetricService searchClickMetricService;

    public List<PopularSaleResponse> getPopularSales() {
        Map<Long, ActiveSale> salesByProductId = popularSaleService.findActiveSales();
        List<Product> products = productService.getProductsByIds(List.copyOf(salesByProductId.keySet()));
        LocalDateTime now = LocalDateTime.now(SaleEvent.ZONE);
        LocalDate today = now.toLocalDate();
        Map<Long, Long> clicksByProductId = searchClickMetricService.getProductClicks(popularSaleService.clickFrom(today), today, now)
                .stream()
                .collect(Collectors.toMap(ProductClickCount::productId, ProductClickCount::clicks));

        return popularSaleService.rank(products, salesByProductId, clicksByProductId).stream()
                .map(PopularSaleResponse::from)
                .toList();
    }
}
