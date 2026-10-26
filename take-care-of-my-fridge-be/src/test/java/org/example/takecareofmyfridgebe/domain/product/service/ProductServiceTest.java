package org.example.takecareofmyfridgebe.domain.product.service;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.domain.product.dto.AdminProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductCreateRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetailResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductSort;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductUpdateRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductUpdateRequest.IngredientUpdate;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductUpdateRequest.MealkitUpdate;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.IngredientProductRepository;
import org.example.takecareofmyfridgebe.domain.product.repository.MealkitProductRepository;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.dummy.ProductFixture;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private IngredientProductRepository ingredientProductRepository;
    @Mock
    private MealkitProductRepository mealkitProductRepository;
    @Mock
    private IngredientRepository ingredientRepository;
    @InjectMocks
    private ProductService service;
    @Captor
    private ArgumentCaptor<Collection<ProductStatus>> statusCaptor;

    @Test
    @DisplayName("판매 중지(STOPPED) 상품은 조회 대상에서 제외한다")
    void getProducts_excludesStopped() {
        // given
        when(productRepository.findProductsByCondition(any(ProductListRequest.class), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // when
        service.getProducts(listRequest(ProductSort.LATEST, 1));

        // then
        verify(productRepository).findProductsByCondition(any(ProductListRequest.class), statusCaptor.capture(), any(Pageable.class));
        assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrder(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("1부터 시작하는 요청 페이지를 0부터 시작하는 PageRequest로 바꾸고 검색 조건을 그대로 넘긴다")
    void getProducts_convertsPageAndPassesCondition() {
        // given
        ProductListRequest request = new ProductListRequest(
                ProductSort.PRICE_ASC, "두부", ProductType.MEALKIT, 3000, 10000, 2, 20);
        when(productRepository.findProductsByCondition(any(ProductListRequest.class), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // when
        service.getProducts(request);

        // then
        verify(productRepository).findProductsByCondition(eq(request), anyCollection(), eq(PageRequest.of(1, 20)));
    }

    @Test
    @DisplayName("조회한 상품을 목록 응답 DTO와 1부터 시작하는 페이지 정보로 변환한다")
    void getProducts_mapsToResponse() {
        // given
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        when(productRepository.findProductsByCondition(any(ProductListRequest.class), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(onion), PageRequest.of(1, 20), 21));

        // when
        PageResponse<ProductListItemResponse> result = service.getProducts(listRequest(ProductSort.LATEST, 2));

        // then
        assertThat(result.pageNumber()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.content()).containsExactly(new ProductListItemResponse(
                5L, ProductType.INGREDIENT, "양파 1kg", null, 3900, ProductStatus.ON_SALE));
    }

    @Test
    @DisplayName("관리자 목록은 판매 중지 상품을 포함하고 상태 필터와 페이지를 적용한다")
    void getAdminProducts_includesStoppedAndFiltersStatus() {
        when(productRepository.findProductsByCondition(any(ProductListRequest.class), anyCollection(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.getAdminProducts(new AdminProductListRequest(" 양파 ", ProductType.INGREDIENT, null, 2, 10));
        ArgumentCaptor<ProductListRequest> conditionCaptor = ArgumentCaptor.forClass(ProductListRequest.class);
        verify(productRepository).findProductsByCondition(conditionCaptor.capture(), statusCaptor.capture(), eq(PageRequest.of(1, 10)));
        assertThat(conditionCaptor.getValue().keyword()).isEqualTo("양파");
        assertThat(conditionCaptor.getValue().type()).isEqualTo(ProductType.INGREDIENT);
        assertThat(statusCaptor.getValue()).containsExactlyInAnyOrder(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT, ProductStatus.STOPPED);

        service.getAdminProducts(new AdminProductListRequest(null, null, ProductStatus.STOPPED, 1, 20));
        verify(productRepository, times(2))
                .findProductsByCondition(any(ProductListRequest.class), statusCaptor.capture(), any(Pageable.class));
        assertThat(statusCaptor.getValue()).containsExactly(ProductStatus.STOPPED);
    }

    @Test
    @DisplayName("관리자 상세는 판매 중지 상품도 조회한다")
    void getAdminProduct_stopped() {
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        onion.stop();
        when(productRepository.findById(5L)).thenReturn(Optional.of(onion));
        when(ingredientProductRepository.findByProductId(5L))
                .thenReturn(Optional.of(ProductFixture.createIngredientDetail(onion, new BigDecimal("1000"), "g")));

        ProductDetailResponse result = service.getAdminProduct(5L);

        assertThat(result.status()).isEqualTo(ProductStatus.STOPPED);
        assertThat(result.ingredient()).isNotNull();
        verify(productRepository, never()).findByIdAndStatusIn(eq(5L), anyCollection());
    }

    private static ProductListRequest listRequest(ProductSort sort, int page) {
        return new ProductListRequest(sort, null, null, null, null, page, 20);
    }

    @Test
    @DisplayName("재료 상품 상세는 용량 정보를 함께 반환한다")
    void getProduct_ingredient() {
        // given
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        when(productRepository.findByIdAndStatusIn(eq(5L), anyCollection())).thenReturn(Optional.of(onion));
        when(ingredientProductRepository.findByProductId(5L))
                .thenReturn(Optional.of(ProductFixture.createIngredientDetail(onion, new BigDecimal("1000"), "g")));

        // when
        ProductDetailResponse result = service.getProduct(5L);

        // then
        assertThat(result.productId()).isEqualTo(5L);
        assertThat(result.productType()).isEqualTo(ProductType.INGREDIENT);
        assertThat(result.ingredient()).isEqualTo(new ProductDetailResponse.IngredientInfo(new BigDecimal("1000"), "g"));
        assertThat(result.mealkit()).isNull();
    }

    @Test
    @DisplayName("밀키트 상품 상세는 인분 정보를 함께 반환한다")
    void getProduct_mealkit() {
        // given
        Product stew = ProductFixture.createMealkitProduct(7L, "된장찌개 밀키트", 12900);
        when(productRepository.findByIdAndStatusIn(eq(7L), anyCollection())).thenReturn(Optional.of(stew));
        when(mealkitProductRepository.findByProductId(7L))
                .thenReturn(Optional.of(ProductFixture.createMealkitDetail(stew, 2)));

        // when
        ProductDetailResponse result = service.getProduct(7L);

        // then
        assertThat(result.productType()).isEqualTo(ProductType.MEALKIT);
        assertThat(result.mealkit()).isEqualTo(new ProductDetailResponse.MealkitInfo(2));
        assertThat(result.ingredient()).isNull();
    }

    @Test
    @DisplayName("없거나 판매 중지된 상품이면 PRODUCT_NOT_FOUND 예외가 발생한다")
    void getProduct_notFound() {
        // given
        when(productRepository.findByIdAndStatusIn(eq(99L), anyCollection())).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.getProduct(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);

        verify(productRepository).findByIdAndStatusIn(eq(99L), statusCaptor.capture());
        assertThat(statusCaptor.getValue())
                .containsExactlyInAnyOrder(ProductStatus.ON_SALE, ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("재료 상품 등록 시 상품과 재료 상세를 함께 저장한다")
    void createProduct_ingredient() {
        // given
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient("ONION")));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ingredientProductRepository.save(any(IngredientProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ProductDetailResponse result = service.createProduct(ProductFixture.createIngredientRequest(1L));

        // then
        assertThat(result.productType()).isEqualTo(ProductType.INGREDIENT);
        assertThat(result.name()).isEqualTo("양파 1kg");
        assertThat(result.status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(result.ingredient()).isEqualTo(new ProductDetailResponse.IngredientInfo(new BigDecimal("1000"), "g"));
        verify(mealkitProductRepository, never()).save(any());
    }

    @Test
    @DisplayName("밀키트 상품 등록 시 상품과 밀키트 상세를 함께 저장한다")
    void createProduct_mealkit() {
        // given
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mealkitProductRepository.save(any(MealkitProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // when
        ProductDetailResponse result = service.createProduct(ProductFixture.createMealkitRequest());

        // then
        assertThat(result.productType()).isEqualTo(ProductType.MEALKIT);
        assertThat(result.mealkit()).isEqualTo(new ProductDetailResponse.MealkitInfo(2));
        verify(ingredientProductRepository, never()).save(any());
    }

    @Test
    @DisplayName("연결할 재료가 없으면 INGREDIENT_NOT_FOUND 예외가 발생하고 아무것도 저장하지 않는다")
    void createProduct_ingredientNotFound() {
        // given
        when(ingredientRepository.findById(99L)).thenReturn(Optional.empty());
        ProductCreateRequest request = ProductFixture.createIngredientRequest(99L);

        // when & then
        assertThatThrownBy(() -> service.createProduct(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INGREDIENT_NOT_FOUND);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("직접 입력용·밀키트용 기본 재료에는 상품을 연결할 수 없다")
    void createProduct_specialIngredient() {
        for (String code : List.of(Ingredient.ING_CUSTOM, Ingredient.ING_MEALKIT)) {
            // given
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient(code)));
            ProductCreateRequest request = ProductFixture.createIngredientRequest(1L);

            // when & then
            assertThatThrownBy(() -> service.createProduct(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_PRODUCT_INGREDIENT);
        }
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("상품 수정은 보낸 필드만 바꾸고 나머지는 기존 값을 유지한다")
    void updateProduct_changesOnlySentFields() {
        // given
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        IngredientProduct detail = ProductFixture.createIngredientDetail(onion, new BigDecimal("1000"), "g");
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(onion));
        when(ingredientProductRepository.findByProductId(5L)).thenReturn(Optional.of(detail));
        ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, 4500, 0, null, null,
                ProductStatus.SOLD_OUT, new IngredientUpdate(null, null, "kg", null), null);

        // when
        ProductDetailResponse result = service.updateProduct(5L, request);

        // then
        assertThat(result.name()).isEqualTo("양파 1kg");
        assertThat(result.price()).isEqualTo(4500);
        assertThat(result.stock()).isZero();
        assertThat(result.status()).isEqualTo(ProductStatus.SOLD_OUT);
        assertThat(result.ingredient()).isEqualTo(new ProductDetailResponse.IngredientInfo(new BigDecimal("1000"), "kg"));
        verify(ingredientRepository, never()).findById(any());
    }

    @Test
    @DisplayName("연결 재료를 바꾸면 바꿀 재료가 상품에 연결 가능한지 확인한다")
    void updateProduct_validatesNewIngredient() {
        // given
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(onion));
        when(ingredientProductRepository.findByProductId(5L))
                .thenReturn(Optional.of(ProductFixture.createIngredientDetail(onion, BigDecimal.ONE, "개")));
        when(ingredientRepository.findById(2L)).thenReturn(Optional.of(ingredient(Ingredient.ING_CUSTOM)));
        ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, null, null, null, null, null,
                new IngredientUpdate(2L, null, null, null), null);

        // when & then
        assertThatThrownBy(() -> service.updateProduct(5L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PRODUCT_INGREDIENT);
    }

    @Test
    @DisplayName("상품 종류와 다른 상세를 보내면 아무것도 바꾸지 않고 거절한다")
    void updateProduct_rejectsDetailOfOtherType() {
        // given
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(onion));
        ProductUpdateRequest request = new ProductUpdateRequest("새 이름", null, null, null, null, null, null, null,
                null, new MealkitUpdate(2));

        // when & then
        assertThatThrownBy(() -> service.updateProduct(5L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(onion.getName()).isEqualTo("양파 1kg");
    }

    @Test
    @DisplayName("상세를 보냈는데 상세 행이 없으면 PRODUCT_DETAIL_MISSING 예외가 발생한다")
    void updateProduct_detailMissing() {
        // given
        Product stew = ProductFixture.createMealkitProduct(7L, "된장찌개 밀키트", 12900);
        when(productRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(stew));
        when(mealkitProductRepository.findByProductId(7L)).thenReturn(Optional.empty());
        ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, null, null, null, null, null,
                null, new MealkitUpdate(3));

        // when & then
        assertThatThrownBy(() -> service.updateProduct(7L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_DETAIL_MISSING);
    }

    @Test
    @DisplayName("없는 상품을 수정하면 PRODUCT_NOT_FOUND 예외가 발생한다")
    void updateProduct_notFound() {
        // given
        when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, 1000, null, null, null, null,
                null, null);

        // when & then
        assertThatThrownBy(() -> service.updateProduct(99L, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    @DisplayName("상품을 삭제하면 행을 지우지 않고 판매 중지(STOPPED)로 바꾼다")
    void deleteProduct_stopsProduct() {
        // given
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(onion));

        // when
        service.deleteProduct(5L);

        // then
        assertThat(onion.getStatus()).isEqualTo(ProductStatus.STOPPED);
        verify(productRepository, never()).delete(any());
    }

    @Test
    @DisplayName("이미 판매 중지된 상품을 다시 삭제해도 예외 없이 판매 중지 상태를 유지한다")
    void deleteProduct_alreadyStopped() {
        // given
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        onion.stop();
        when(productRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(onion));

        // when
        service.deleteProduct(5L);

        // then
        assertThat(onion.getStatus()).isEqualTo(ProductStatus.STOPPED);
    }

    @Test
    @DisplayName("없는 상품을 삭제하면 PRODUCT_NOT_FOUND 예외가 발생한다")
    void deleteProduct_notFound() {
        // given
        when(productRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.deleteProduct(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    private static Ingredient ingredient(String code) {
        return Ingredient.builder()
                .ingredientCode(code)
                .name(code)
                .baseUnit("개")
                .defaultStorage(StorageType.ROOM_TEMP)
                .defaultShelfDays(7)
                .build();
    }

}
