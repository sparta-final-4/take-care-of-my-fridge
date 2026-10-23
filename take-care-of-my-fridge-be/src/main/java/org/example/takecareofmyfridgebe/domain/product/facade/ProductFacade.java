package org.example.takecareofmyfridgebe.domain.product.facade;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetailResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleInfoResponse;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.stereotype.Component;

// 상품 목록·상세 응답에 진행 중인 할인 정보(`sale`)를 합친다. 상품 서비스는 할인 행사를 모르고,
// 할인 행사 서비스는 상품 목록을 모른다. 장바구니·주문은 이 퍼사드를 거치지 않고 금액을 정하는 조회를 직접 부른다.
@Component
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductService productService;
    private final SaleEventReader saleEventReader;

    // 목록 한 페이지의 상품 번호를 모아 할인 조회를 한 번만 한다.
    public PageResponse<ProductListItemResponse> getProducts(ProductListRequest request) {
        PageResponse<ProductListItemResponse> products = productService.getProducts(request);
        List<Long> productIds = products.content().stream().map(ProductListItemResponse::productId).toList();
        Map<Long, SaleInfoResponse> saleInfos = saleEventReader.findSaleInfos(productIds);

        return new PageResponse<>(
                products.content().stream().map(item -> item.withSale(saleInfos.get(item.productId()))).toList(),
                products.pageNumber(),
                products.pageSize(),
                products.totalElements(),
                products.totalPages(),
                products.isLast());
    }

    public ProductDetailResponse getProduct(Long productId) {
        ProductDetailResponse product = productService.getProduct(productId);
        return product.withSale(saleEventReader.findSaleInfos(List.of(productId)).get(productId));
    }
}
