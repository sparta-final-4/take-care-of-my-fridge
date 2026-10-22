package org.example.takecareofmyfridgebe.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductCreateRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetailResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductUpdateRequest;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
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
    private final IngredientRepository ingredientRepository;

    // 정렬은 request.sort()로 저장소가 적용하므로 PageRequest에는 넣지 않는다.
    public PageResponse<ProductListItemResponse> getProducts(ProductListRequest request) {
        PageRequest pageable = PageRequest.of(request.page() - 1, request.size());

        Page<ProductListItemResponse> response = productRepository.findProductsByCondition(request, PUBLIC_STATUSES, pageable)
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

    // TODO: ElasticSearch 에 인덱싱 과정 추가 필수...
    @Transactional
    public ProductDetailResponse createProduct(ProductCreateRequest request) {
        if (request.productType() == ProductType.INGREDIENT) {
            validateLinkableIngredient(request.ingredient().ingredientId());
        }

        Product product = productRepository.save(request.toProduct());

        return switch (product.getProductType()) {
            case INGREDIENT -> ProductDetailResponse.ofIngredient(
                    product,
                    ingredientProductRepository.save(request.toIngredientProduct(product))
            );
            case MEALKIT -> ProductDetailResponse.ofMealkit(
                    product,
                    mealkitProductRepository.save(request.toMealkitProduct(product))
            );
        };
    }

    // 관리자는 판매 중지 상품도 수정할 수 있다(판매 재개 포함).
    @Transactional
    public ProductDetailResponse updateProduct(Long productId, ProductUpdateRequest request) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (request.hasDetailOtherThan(product.getProductType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "상품 종류에 맞지 않는 상세 정보입니다.");
        }

        request.applyTo(product);

        return switch (product.getProductType()) {
            case INGREDIENT -> ProductDetailResponse.ofIngredient(product, updateIngredientDetail(productId, request));
            case MEALKIT -> ProductDetailResponse.ofMealkit(product, updateMealkitDetail(productId, request));
        };
    }

    private IngredientProduct updateIngredientDetail(Long productId, ProductUpdateRequest request) {
        IngredientProduct detail = ingredientProductRepository.findByProductId(productId).orElse(null);
        if (request.ingredient() == null) {
            return detail;
        }
        if (detail == null) {
            throw new BusinessException(ErrorCode.PRODUCT_DETAIL_MISSING);
        }
        if (request.ingredient().ingredientId() != null) {
            validateLinkableIngredient(request.ingredient().ingredientId());
        }
        request.applyTo(detail);
        return detail;
    }

    private MealkitProduct updateMealkitDetail(Long productId, ProductUpdateRequest request) {
        MealkitProduct detail = mealkitProductRepository.findByProductId(productId).orElse(null);
        if (request.mealkit() == null) {
            return detail;
        }
        if (detail == null) {
            throw new BusinessException(ErrorCode.PRODUCT_DETAIL_MISSING);
        }
        request.applyTo(detail);
        return detail;
    }

    // ingredient_products.ingredient_id는 FK 없이 ID로만 참조하므로 저장 전에 직접 확인한다.
    // 직접 입력용(ING_CUSTOM)·밀키트용(ING_MEALKIT) 기본 재료는 상품에 연결하지 않는다.
    private void validateLinkableIngredient(Long ingredientId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INGREDIENT_NOT_FOUND));
        if (ingredient.isCustom() || ingredient.isMealkit()) {
            throw new BusinessException(ErrorCode.INVALID_PRODUCT_INGREDIENT);
        }
    }
}
