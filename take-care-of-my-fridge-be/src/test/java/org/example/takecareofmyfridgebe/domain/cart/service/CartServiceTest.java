package org.example.takecareofmyfridgebe.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResult;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartItemResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartItemsResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityRequest;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.dummy.ProductFixture;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private SaleEventItemRepository saleEventItemRepository;

    private CartService cartService;

    // 판매가 판단까지 실제로 거치도록 SaleEventReader는 진짜를 쓰고 그 아래 조회만 대신한다
    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, productRepository,
                new SaleEventReader(saleEventItemRepository));
    }

    @Test
    @DisplayName("장바구니가 없으면 만들고 상품을 담는다")
    void addItem_createsCartAndItem() {
        Cart cart = Cart.builder().memberId(1L).build();
        given(productRepository.findById(10L)).willReturn(Optional.of(productWithStock(10)));
        given(cartRepository.findByMemberId(1L)).willReturn(Optional.empty());
        given(cartRepository.save(any(Cart.class))).willReturn(cart);
        given(cartItemRepository.findByCartAndProductId(cart, 10L)).willReturn(Optional.empty());
        given(cartItemRepository.save(any(CartItem.class))).willAnswer(returnsFirstArg());

        AddCartItemResult result = cartService.addItem(1L, new AddCartItemRequest(10L, 2));

        assertThat(result.created()).isTrue();
        assertThat(result.response().quantity()).isEqualTo(2);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(10L);
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 담긴 상품이면 수량을 더한다")
    void addItem_addsQuantityToExistingItem() {
        Cart cart = Cart.builder().memberId(1L).build();
        CartItem item = CartItem.builder().cart(cart).productId(10L).quantity(2).build();
        given(productRepository.findById(10L)).willReturn(Optional.of(productWithStock(10)));
        given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
        given(cartItemRepository.findByCartAndProductId(cart, 10L)).willReturn(Optional.of(item));

        AddCartItemResult result = cartService.addItem(1L, new AddCartItemRequest(10L, 3));

        assertThat(result.created()).isFalse();
        assertThat(result.response().quantity()).isEqualTo(5);

        assertThat(item.getQuantity()).isEqualTo(5);
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("담은 줄을 담은 순서대로 돌려준다")
    void getItems_returnsItemsInOrder() {
        Cart cart = Cart.builder().memberId(1L).build();
        CartItem first = CartItem.builder().cart(cart).productId(10L).quantity(2).build();
        CartItem second = CartItem.builder().cart(cart).productId(20L).quantity(1).build();
        given(cartItemRepository.findByCartMemberIdOrderByIdAsc(1L)).willReturn(List.of(first, second));
        given(productRepository.findAllById(List.of(10L, 20L))).willReturn(List.of(
                ProductFixture.createProduct(20L, "두부", 2000),
                ProductFixture.createProduct(10L, "대파", 3000)));

        CartItemsResponse result = cartService.getItems(1L);

        assertThat(result.items()).extracting(CartItemResponse::productId).containsExactly(10L, 20L);
        assertThat(result.items()).extracting(CartItemResponse::quantity).containsExactly(2, 1);
        assertThat(result.items()).extracting(CartItemResponse::productName).containsExactly("대파", "두부");
    }

    @Test
    @DisplayName("장바구니가 비어 있으면 빈 목록을 돌려준다")
    void getItems_returnsEmptyListWhenNothingInCart() {
        given(cartItemRepository.findByCartMemberIdOrderByIdAsc(1L)).willReturn(List.of());

        assertThat(cartService.getItems(1L).items()).isEmpty();
    }

    @Test
    @DisplayName("진행 중인 특가·번개 드롭은 항목마다 할인가와 구분이 붙고, 번개 드롭 남은 수량이 0이면 그 항목만 품절이다")
    void getItems_appliesActiveSales() {
        Cart cart = Cart.builder().memberId(1L).build();
        CartItem normal = CartItem.builder().cart(cart).productId(10L).quantity(1).build();
        CartItem special = CartItem.builder().cart(cart).productId(20L).quantity(2).build();
        CartItem flashDrop = CartItem.builder().cart(cart).productId(30L).quantity(1).build();
        given(cartItemRepository.findByCartMemberIdOrderByIdAsc(1L)).willReturn(List.of(normal, special, flashDrop));
        given(productRepository.findAllById(List.of(10L, 20L, 30L))).willReturn(List.of(
                ProductFixture.createProduct(10L, "대파", 3000),
                ProductFixture.createProduct(20L, "두부", 3000),
                ProductFixture.createProduct(30L, "우유", 4000)));
        given(saleEventItemRepository.findActiveByProductIds(eq(List.of(10L, 20L, 30L)), any(), any())).willReturn(List.of(
                activeSale(20L, SaleEventType.SPECIAL, 2400, 20, null),
                activeSale(30L, SaleEventType.FLASH_DROP, 2000, 50, 0)));

        CartItemsResponse result = cartService.getItems(1L);

        assertThat(result.items()).extracting(CartItemResponse::unitPrice).containsExactly(3000, 2400, 2000);
        assertThat(result.items()).extracting(CartItemResponse::listPrice).containsExactly(3000, 3000, 4000);
        assertThat(result.items()).extracting(CartItemResponse::saleType)
                .containsExactly(null, SaleEventType.SPECIAL, SaleEventType.FLASH_DROP);
        assertThat(result.items()).extracting(CartItemResponse::discountRate).containsExactly(null, 20, 50);
        assertThat(result.items()).extracting(CartItemResponse::flashDropRemaining).containsExactly(null, null, 0);
        assertThat(result.items()).extracting(CartItemResponse::flashDropLimit).containsExactly(null, null, 10);
        assertThat(result.items()).extracting(CartItemResponse::saleEndsAt)
                .satisfiesExactly(ends -> assertThat(ends).isNull(),
                        ends -> assertThat(ends.getOffset()).isEqualTo(ZoneOffset.of("+09:00")),
                        ends -> assertThat(ends.getOffset()).isEqualTo(ZoneOffset.of("+09:00")));
        assertThat(result.items()).extracting(CartItemResponse::soldOut).containsExactly(false, false, true);
    }

    @Test
    @DisplayName("번개 드롭 상품을 이미 담았으면 한 개 더 담을 수 없고 수량은 그대로다")
    void addItem_flashDropAlreadyInCart_rejects() {
        Cart cart = Cart.builder().memberId(1L).build();
        CartItem item = CartItem.builder().cart(cart).productId(10L).quantity(1).build();
        given(productRepository.findById(10L)).willReturn(Optional.of(productWithStock(10)));
        given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
        given(cartItemRepository.findByCartAndProductId(cart, 10L)).willReturn(Optional.of(item));
        given(saleEventItemRepository.findActiveByProductIds(eq(List.of(10L)), any(), any()))
                .willReturn(List.of(activeSale(10L, SaleEventType.FLASH_DROP, 500, 50, 5)));

        assertThatThrownBy(() -> cartService.addItem(1L, new AddCartItemRequest(10L, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("구매 한도 초과 · 대파")
                .extracting("errorCode").isEqualTo(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
        assertThat(item.getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("번개 드롭 상품의 수량을 2로 바꿀 수 없고 수량은 그대로다")
    void changeQuantity_flashDropOverOne_rejects() {
        Cart cart = Cart.builder().memberId(1L).build();
        CartItem item = CartItem.builder().cart(cart).productId(10L).quantity(1).build();
        given(cartItemRepository.findByIdAndCartMemberId(5L, 1L)).willReturn(Optional.of(item));
        given(productRepository.findById(10L)).willReturn(Optional.of(productWithStock(10)));
        given(saleEventItemRepository.findActiveByProductIds(eq(List.of(10L)), any(), any()))
                .willReturn(List.of(activeSale(10L, SaleEventType.FLASH_DROP, 500, 50, 5)));

        assertThatThrownBy(() -> cartService.changeQuantity(1L, 5L, new ChangeCartItemQuantityRequest(2)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
        assertThat(item.getQuantity()).isEqualTo(1);
    }

    private ActiveSale activeSale(Long productId, SaleEventType type, int salePrice, int discountRate, Integer remaining) {
        return new ActiveSale(productId, productId * 100, type, salePrice, discountRate,
                LocalDateTime.now().plusHours(1), remaining, remaining == null ? null : 10);
    }

    private Product productWithStock(int stock) {
        Product product = Product.builder()
                .productType(ProductType.INGREDIENT)
                .name("대파")
                .price(1000)
                .stock(stock)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .build();
        ReflectionTestUtils.setField(product, "id", 10L);
        return product;
    }
}
