package org.example.takecareofmyfridgebe.domain.sale.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 상품을 지금 얼마에, 어떤 상태로 파는지 답한다. 장바구니·주문하기가 같은 답을 쓰도록 입구를 하나로 둔다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SaleEventReader {

    private final SaleEventItemRepository saleEventItemRepository;

    // 상품 ID별 판매가. 할인이 없는 상품도 정가로 들어 있다.
    public Map<Long, SellingPrice> findSellingPrices(List<Product> products) {
        Map<Long, ActiveSale> salesByProductId = findActiveSales(products.stream().map(Product::getId).toList());
        return products.stream()
                .collect(Collectors.toMap(Product::getId, product -> SellingPrice.of(product, salesByProductId.get(product.getId()))));
    }

    // 상품 ID별 진행 중 할인을 한 번에 돌려준다. 할인이 없는 상품은 맵에 없다.
    Map<Long, ActiveSale> findActiveSales(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return saleEventItemRepository
                // 종료로 닫은 행사만 뺀다. 상태는 스케줄러가 늦게 바꿔도 되고, 진행 중인지는 시각으로 판단한다.
                .findActiveByProductIds(productIds, SaleEventStatus.OPEN, LocalDateTime.now(SaleEvent.ZONE))
                .stream()
                .collect(Collectors.toMap(ActiveSale::productId, Function.identity(), (first, second) -> first));
    }
}
