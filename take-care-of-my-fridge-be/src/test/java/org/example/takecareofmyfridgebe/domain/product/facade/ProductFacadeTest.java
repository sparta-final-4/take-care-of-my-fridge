package org.example.takecareofmyfridgebe.domain.product.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetail;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetailResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListItemResponse;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductUpdateRequest;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.SaleResponse;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventStatus;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.dummy.ProductFixture;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    private static final LocalDateTime ENDS_AT = LocalDateTime.of(2026, 11, 1, 18, 0);

    @Mock
    private ProductService productService;

    @Mock
    private SaleEventItemRepository saleEventItemRepository;

    private ProductFacade productFacade;

    // 판매가 판단까지 실제로 거치도록 SaleEventReader는 진짜를 쓰고 그 아래 조회만 대신한다
    @BeforeEach
    void setUp() {
        productFacade = new ProductFacade(productService, new SaleEventReader(saleEventItemRepository));
    }

    @Test
    @DisplayName("할인 없는 판매 중 상품은 sale이 null이고 품절이 아니며, 정가와 상태는 상품 값 그대로다")
    void getProducts_withoutSale() {
        // given
        Product onion = ProductFixture.createProduct(1L, "양파 1kg", 3900);
        givenProducts(onion);

        // when
        ProductListItemResponse item = productFacade.getProducts(listRequest(1)).content().get(0);

        // then
        assertThat(item.sale()).isNull();
        assertThat(item.soldOut()).isFalse();
        assertThat(item.price()).isEqualTo(3900);
        assertThat(item.status()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("특가 상품은 구분·할인가·할인율과 +09:00 종료 시각을 담고, 남은·한정 수량은 null이다")
    void getProducts_specialSale() {
        // given
        Product onion = ProductFixture.createProduct(1L, "양파 1kg", 3900);
        givenProducts(onion);
        givenActiveSales(List.of(1L), activeSale(1L, SaleEventType.SPECIAL, 3120, 20, null));

        // when
        ProductListItemResponse item = productFacade.getProducts(listRequest(1)).content().get(0);

        // then
        SaleResponse sale = item.sale();
        assertThat(sale.type()).isEqualTo(SaleEventType.SPECIAL);
        assertThat(sale.salePrice()).isEqualTo(3120);
        assertThat(sale.discountRate()).isEqualTo(20);
        assertThat(sale.endsAt().getOffset()).isEqualTo(ZoneOffset.ofHours(9));
        assertThat(sale.endsAt().toLocalDateTime()).isEqualTo(ENDS_AT);
        assertThat(sale.remainingQuantity()).isNull();
        assertThat(sale.limitedQuantity()).isNull();
        assertThat(item.soldOut()).isFalse();
    }

    @Test
    @DisplayName("번개 드롭 남은 수량이 0이면 상품 상태는 판매 중 그대로이고 품절로 내려 준다")
    void getProducts_flashDropExhausted() {
        // given
        Product leek = ProductFixture.createProduct(2L, "대파 1단", 2900);
        givenProducts(leek);
        givenActiveSales(List.of(2L), activeSale(2L, SaleEventType.FLASH_DROP, 1450, 50, 0));

        // when
        ProductListItemResponse item = productFacade.getProducts(listRequest(1)).content().get(0);

        // then
        assertThat(item.soldOut()).isTrue();
        assertThat(item.status()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(item.sale().remainingQuantity()).isZero();
    }

    @Test
    @DisplayName("품절 상태 상품은 특가 중이어도 품절이고, 할인 정보는 그대로 담는다")
    void getProducts_soldOutProductOnSale() {
        // given
        Product tofu = ProductFixture.createProduct(3L, "두부", 2000);
        tofu.decreaseStock(10);
        givenProducts(tofu);
        givenActiveSales(List.of(3L), activeSale(3L, SaleEventType.SPECIAL, 1800, 10, null));

        // when
        ProductListItemResponse item = productFacade.getProducts(listRequest(1)).content().get(0);

        // then
        assertThat(item.status()).isEqualTo(ProductStatus.SOLD_OUT);
        assertThat(item.soldOut()).isTrue();
        assertThat(item.sale()).isNotNull();
    }

    @Test
    @DisplayName("상품이 여러 개여도 진행 중 할인은 모든 상품 ID로 한 번만 조회한다")
    void getProducts_readsSalesOnce() {
        // given
        givenProducts(ProductFixture.createProduct(1L, "양파 1kg", 3900),
                ProductFixture.createProduct(2L, "대파 1단", 2900),
                ProductFixture.createProduct(3L, "두부", 2000));

        // when
        productFacade.getProducts(listRequest(1));

        // then
        verify(saleEventItemRepository, times(1))
                .findActiveByProductIds(eq(List.of(1L, 2L, 3L)), eq(SaleEventStatus.OPEN), any());
    }

    @Test
    @DisplayName("빈 페이지면 진행 중 할인을 조회하지 않고 빈 목록을 돌려준다")
    void getProducts_emptyPageSkipsSaleQuery() {
        // given
        givenProducts();

        // when
        PageResponse<ProductListItemResponse> result = productFacade.getProducts(listRequest(1));

        // then
        assertThat(result.content()).isEmpty();
        verify(saleEventItemRepository, never()).findActiveByProductIds(anyCollection(), anyCollection(), any());
    }

    @Test
    @DisplayName("응답 페이지 번호는 1부터 시작하고 전체 건수는 조회 결과 그대로다")
    void getProducts_pageInfo() {
        // given
        Product onion = ProductFixture.createProduct(1L, "양파 1kg", 3900);
        given(productService.getProducts(any(ProductListRequest.class)))
                .willReturn(new PageImpl<>(List.of(onion), PageRequest.of(1, 20), 21));

        // when
        PageResponse<ProductListItemResponse> result = productFacade.getProducts(listRequest(2));

        // then
        assertThat(result.pageNumber()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(21);
        assertThat(result.content()).extracting(ProductListItemResponse::productId).containsExactly(1L);
    }

    @Test
    @DisplayName("검색 결과는 상품 서비스가 돌려준 순서를 지키고, 전체 건수는 ES 값으로 페이지를 계산한다")
    void getProductsByIds_keepsOrderAndEsTotal() {
        // given
        Product leek = ProductFixture.createProduct(9L, "대파 1단", 2900);
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        given(productService.getProductsByIds(List.of(9L, 4L, 5L))).willReturn(List.of(leek, onion));

        // when
        PageResponse<ProductListItemResponse> result = productFacade.getProductsByIds(
                List.of(9L, 4L, 5L), 30, new ProductListRequest(null, "파", null, null, null, 2, 3));

        // then
        assertThat(result.content()).extracting(ProductListItemResponse::productId).containsExactly(9L, 5L);
        assertThat(result.totalElements()).isEqualTo(30);
        assertThat(result.pageNumber()).isEqualTo(2);
        assertThat(result.totalPages()).isEqualTo(10);
        assertThat(result.isLast()).isFalse();
    }

    @Test
    @DisplayName("상세는 재료 정보와 진행 중 번개 드롭, 품절 여부를 함께 담는다")
    void getProduct_ingredientWithFlashDrop() {
        // given
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        given(productService.getProduct(5L)).willReturn(ProductDetail.ofIngredient(onion,
                ProductFixture.createIngredientDetail(onion, new BigDecimal("1000"), "g")));
        givenActiveSales(List.of(5L), activeSale(5L, SaleEventType.FLASH_DROP, 1950, 50, 3));

        // when
        ProductDetailResponse result = productFacade.getProduct(5L);

        // then
        assertThat(result.ingredient()).isEqualTo(new ProductDetailResponse.IngredientInfo(new BigDecimal("1000"), "g"));
        assertThat(result.sale().type()).isEqualTo(SaleEventType.FLASH_DROP);
        assertThat(result.sale().remainingQuantity()).isEqualTo(3);
        assertThat(result.soldOut()).isFalse();
    }

    @Test
    @DisplayName("관리자 상세에서 판매 중지 상품은 상태가 판매 중지이고 품절로 내려 준다")
    void getAdminProduct_stoppedIsSoldOut() {
        // given
        Product onion = ProductFixture.createProduct(5L, "양파 1kg", 3900);
        onion.stop();
        given(productService.getAdminProduct(5L)).willReturn(ProductDetail.ofIngredient(onion, null));

        // when
        ProductDetailResponse result = productFacade.getAdminProduct(5L);

        // then
        assertThat(result.status()).isEqualTo(ProductStatus.STOPPED);
        assertThat(result.soldOut()).isTrue();
        assertThat(result.sale()).isNull();
    }

    @Test
    @DisplayName("관리자 수정 응답에는 수정이 끝난 뒤 읽은 진행 중 특가가 붙는다")
    void updateProduct_attachesSaleAfterUpdate() {
        // given
        Product stew = ProductFixture.createMealkitProduct(7L, "된장찌개 밀키트", 12900);
        ProductUpdateRequest request = new ProductUpdateRequest(null, null, null, 13900, null, null, null, null, null, null);
        given(productService.updateProduct(7L, request))
                .willReturn(ProductDetail.ofMealkit(stew, ProductFixture.createMealkitDetail(stew, 2)));
        givenActiveSales(List.of(7L), activeSale(7L, SaleEventType.SPECIAL, 10320, 20, null));

        // when
        ProductDetailResponse result = productFacade.updateProduct(7L, request);

        // then
        assertThat(result.sale().type()).isEqualTo(SaleEventType.SPECIAL);
        assertThat(result.mealkit()).isEqualTo(new ProductDetailResponse.MealkitInfo(2));
        InOrder order = inOrder(productService, saleEventItemRepository);
        order.verify(productService).updateProduct(7L, request);
        order.verify(saleEventItemRepository).findActiveByProductIds(eq(List.of(7L)), eq(SaleEventStatus.OPEN), any());
    }

    private void givenProducts(Product... products) {
        given(productService.getProducts(any(ProductListRequest.class))).willReturn(new PageImpl<>(List.of(products)));
    }

    private void givenActiveSales(List<Long> productIds, ActiveSale... sales) {
        given(saleEventItemRepository.findActiveByProductIds(eq(productIds), eq(SaleEventStatus.OPEN), any()))
                .willReturn(List.of(sales));
    }

    private ActiveSale activeSale(Long productId, SaleEventType type, int salePrice, int discountRate, Integer remaining) {
        return new ActiveSale(productId, productId * 100, type, salePrice, discountRate,
                ENDS_AT, remaining, remaining == null ? null : 10);
    }

    private static ProductListRequest listRequest(int page) {
        return new ProductListRequest(null, null, null, null, null, page, 20);
    }
}
