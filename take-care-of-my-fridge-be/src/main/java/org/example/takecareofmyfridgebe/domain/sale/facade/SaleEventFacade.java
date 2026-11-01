package org.example.takecareofmyfridgebe.domain.sale.facade;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetail;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleEventDetailResponse;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventQueryService;
import org.springframework.stereotype.Component;

// 행사 수정 화면에 보여 줄 행사 하나에 상품 이름·정가를 붙인다. 행사는 할인 서비스에서, 상품은 상품 서비스에서 읽어 합친다.
// 목록·후보 상품 조회와 쓰기는 합칠 것이 없어 컨트롤러가 서비스를 바로 부른다
@Component
@RequiredArgsConstructor
public class SaleEventFacade {

    private final SaleEventQueryService saleEventQueryService;
    private final ProductService productService;

    public SaleEventDetailResponse getSaleEvent(Long saleEventId) {
        SaleEvent saleEvent = saleEventQueryService.getSaleEvent(saleEventId);
        List<SaleEventItem> items = saleEventQueryService.getSaleEventItems(saleEventId);
        // 종료된 행사의 상품이 판매 중지됐어도 보여야 해서 상태로 거르지 않는 조회를 쓴다
        Map<Long, Product> productsById = productService
                .getProductDetailsByIds(items.stream().map(SaleEventItem::getProductId).toList()).stream()
                .map(ProductDetail::product)
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return SaleEventDetailResponse.from(saleEvent, items, productsById);
    }
}
