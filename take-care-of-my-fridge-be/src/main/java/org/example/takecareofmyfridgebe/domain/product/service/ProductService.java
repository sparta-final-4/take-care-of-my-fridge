package org.example.takecareofmyfridgebe.domain.product.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.domain.product.dto.AdminProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductCreateRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetail;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductIndexSource;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductUpdateRequest;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.event.ProductChangedEvent;
import org.example.takecareofmyfridgebe.domain.product.repository.IngredientProductRepository;
import org.example.takecareofmyfridgebe.domain.product.repository.MealkitProductRepository;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private final ApplicationEventPublisher eventPublisher;

    // 정렬은 request.sort()로 저장소가 적용하므로 PageRequest에는 넣지 않는다.
    public Page<Product> getProducts(ProductListRequest request) {
        PageRequest pageable = PageRequest.of(request.page() - 1, request.size());
        return productRepository.findProductsByCondition(request, PUBLIC_STATUSES, pageable);
    }

    /**
     * ES 검색이 고른 ID 순서대로 상품을 돌려준다. 가격·상태는 색인 값이 아니라 DB 값을 쓴다.
     * <br>
     * 색인이 늦어 판매 중지된 상품이 섞여 오면 뺀다.
     */
    public List<Product> getProductsByIds(List<Long> productIds) {
        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return productIds.stream()
                .map(productsById::get)
                .filter(Objects::nonNull)
                .filter(product -> PUBLIC_STATUSES.contains(product.getStatus()))
                .toList();
    }

    // 전체 재색인용. 판매 중지 상품도 넣고, 검색할 때 상태로 거른다
    public List<ProductIndexSource> findIndexSources() {
        return productRepository.findAll(Sort.by("id")).stream()
                .map(ProductIndexSource::from)
                .toList();
    }

    // 단건 색인용
    public Optional<ProductIndexSource> findIndexSource(long productId) {
        return productRepository.findById(productId)
                .map(ProductIndexSource::from);
    }

    public Page<Product> getAdminProducts(AdminProductListRequest request) {
        ProductListRequest condition = request.toProductListRequest();
        PageRequest pageable = PageRequest.of(condition.page() - 1, condition.size());
        List<ProductStatus> statuses = request.status() == null
                ? List.of(ProductStatus.values()) : List.of(request.status());
        return productRepository.findProductsByCondition(condition, statuses, pageable);
    }

    // 판매 중지 상품은 존재하지 않는 상품과 같이 404로 응답
    public ProductDetail getProduct(Long productId) {
        Product product = productRepository.findByIdAndStatusIn(productId, PUBLIC_STATUSES)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return toDetail(product);
    }

    public ProductDetail getAdminProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));

        return toDetail(product);
    }

    private ProductDetail toDetail(Product product) {
        Long productId = product.getId();
        return switch (product.getProductType()) {
            case INGREDIENT -> ProductDetail.ofIngredient(
                    product,
                    ingredientProductRepository.findByProductId(productId).orElse(null)
            );
            case MEALKIT -> ProductDetail.ofMealkit(
                    product,
                    mealkitProductRepository.findByProductId(productId).orElse(null)
            );
        };
    }

    @Transactional
    public ProductDetail createProduct(ProductCreateRequest request) {
        if (request.productType() == ProductType.INGREDIENT) {
            validateLinkableIngredient(request.ingredient().ingredientId());
        }

        Product product = productRepository.save(request.toProduct());

        // 단건 인덱싱 위한 이벤트 발행
        eventPublisher.publishEvent(ProductChangedEvent.from(product.getId()));

        return switch (product.getProductType()) {
            case INGREDIENT -> ProductDetail.ofIngredient(
                    product,
                    ingredientProductRepository.save(request.toIngredientProduct(product))
            );
            case MEALKIT -> ProductDetail.ofMealkit(
                    product,
                    mealkitProductRepository.save(request.toMealkitProduct(product))
            );
        };
    }

    // 관리자는 판매 중지 상품도 수정할 수 있다(판매 재개 포함).
    @Transactional
    public ProductDetail updateProduct(Long productId, ProductUpdateRequest request) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (request.hasDetailOtherThan(product.getProductType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "상품 종류에 맞지 않는 상세 정보입니다.");
        }

        request.applyTo(product);

        // 단건 인덱싱 위한 이벤트 발행
        eventPublisher.publishEvent(ProductChangedEvent.from(product.getId()));

        return switch (product.getProductType()) {
            case INGREDIENT -> ProductDetail.ofIngredient(product, updateIngredientDetail(productId, request));
            case MEALKIT -> ProductDetail.ofMealkit(product, updateMealkitDetail(productId, request));
        };
    }

    // 이미 판매 중지된 상품을 다시 삭제해도 같은 결과이므로 성공으로 처리한다.
    // 주문 생성과 같은 행 잠금을 걸어, 삭제 직후 진행 중인 주문이 판매 중지 상품을 사지 않게 한다.
    @Transactional
    public void deleteProduct(Long productId) {
        Product product = productRepository.findByIdForUpdate(productId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        product.stop();

        // 단건 인덱싱 위한 이벤트 발행
        eventPublisher.publishEvent(ProductChangedEvent.from(product.getId()));
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
