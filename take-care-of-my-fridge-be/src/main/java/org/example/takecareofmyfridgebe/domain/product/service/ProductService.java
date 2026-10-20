package org.example.takecareofmyfridgebe.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetailResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductSort;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.IngredientProductRepository;
import org.example.takecareofmyfridgebe.domain.product.repository.MealkitProductRepository;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    // 판매 중지(STOPPED) 상품은 공개 목록에서 제외
    private static final List<ProductStatus> PUBLIC_STATUSES = List.of(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);

    private final ProductRepository productRepository;
    private final IngredientProductRepository ingredientProductRepository;
    private final MealkitProductRepository mealkitProductRepository;

    public PageResponse<ProductListItemResponse> getProducts(ProductSort sort, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size, sort.toSort());

        Page<ProductListItemResponse> response = productRepository.findByStatusIn(PUBLIC_STATUSES, pageable)
                .map(ProductListItemResponse::from);

        return PageResponse.of(response);
    }

    // 판매 중지 상품은 존재하지 않는 상품과 같이 404로 응답
    public ProductDetailResponse getProduct(Long productId) {
        Product product = productRepository.findByIdAndStatusIn(productId, PUBLIC_STATUSES)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return switch (product.getProductType()) {
            case INGREDIENT -> ProductDetailResponse.ofIngredient(
                    product,
                    ingredientProductRepository.findByProductId(productId).orElse(null)
            );
            case MEALKIT -> ProductDetailResponse.ofMealkit(
                    product,
                    mealkitProductRepository.findByProductId(productId).orElse(null)
            );
        };
    }
}
