package org.example.takecareofmyfridgebe.domain.product.facade;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.dto.AdminProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductCreateRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetail;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetailResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductUpdateRequest;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

// 상품 응답에 지금 판매가(할인·품절)를 붙인다. 상품은 상품 서비스가, 판매가는 할인 행사 도메인의 입구가 정하고 여기서 합친다.
@Component
@RequiredArgsConstructor
public class ProductFacade {

    private final ProductService productService;
    private final SaleEventReader saleEventReader;

    public PageResponse<ProductListItemResponse> getProducts(ProductListRequest request) {
        return toListPage(productService.getProducts(request));
    }

    public PageResponse<ProductListItemResponse> getAdminProducts(AdminProductListRequest request) {
        return toListPage(productService.getAdminProducts(request));
    }

    // ES 검색이 고른 ID 순서대로 목록을 만든다. 전체 건수는 ES 값을 그대로 쓴다.
    // PageImpl은 받은 개수가 페이지 크기보다 적으면 전체 건수를 다시 계산하므로, 상품을 뺀 페이지에서도 ES 건수를 지키도록 직접 만든다
    public PageResponse<ProductListItemResponse> getProductsByIds(List<Long> productIds, long total, ProductListRequest request) {
        List<ProductListItemResponse> content = toListItems(productService.getProductsByIds(productIds));
        int totalPages = (int) Math.ceil((double) total / request.size());
        return new PageResponse<>(content, request.page(), request.size(), total, totalPages, request.page() >= totalPages);
    }

    public ProductDetailResponse getProduct(Long productId) {
        return toDetailResponse(productService.getProduct(productId));
    }

    public ProductDetailResponse getAdminProduct(Long productId) {
        return toDetailResponse(productService.getAdminProduct(productId));
    }

    // 판매가는 상품 저장이 커밋된 뒤에 읽는다
    public ProductDetailResponse createProduct(ProductCreateRequest request) {
        return toDetailResponse(productService.createProduct(request));
    }

    public ProductDetailResponse updateProduct(Long productId, ProductUpdateRequest request) {
        return toDetailResponse(productService.updateProduct(productId, request));
    }

    private PageResponse<ProductListItemResponse> toListPage(Page<Product> products) {
        Map<Long, SellingPrice> pricesByProductId = saleEventReader.findSellingPrices(products.getContent());
        return PageResponse.of(products.map(product -> ProductListItemResponse.from(product, pricesByProductId.get(product.getId()))));
    }

    private List<ProductListItemResponse> toListItems(List<Product> products) {
        Map<Long, SellingPrice> pricesByProductId = saleEventReader.findSellingPrices(products);
        return products.stream()
                .map(product -> ProductListItemResponse.from(product, pricesByProductId.get(product.getId())))
                .toList();
    }

    private ProductDetailResponse toDetailResponse(ProductDetail detail) {
        return ProductDetailResponse.from(detail, saleEventReader.findSellingPrice(detail.product()));
    }
}
