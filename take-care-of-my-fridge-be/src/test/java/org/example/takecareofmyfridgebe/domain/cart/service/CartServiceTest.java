package org.example.takecareofmyfridgebe.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResult;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartQuantityLimit;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityRequest;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    private static final CartQuantityLimit STOCK_10 = new CartQuantityLimit("대파", 10, false);
    private static final CartQuantityLimit FLASH_DROP = new CartQuantityLimit("대파", 10, true);

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository);
    }

    @Test
    @DisplayName("장바구니가 없으면 만들고 상품을 담는다")
    void createsCartAndItemOnAdd() {
        Cart cart = Cart.builder().memberId(1L).build();
        given(cartRepository.findByMemberId(1L)).willReturn(Optional.empty());
        given(cartRepository.save(any(Cart.class))).willReturn(cart);
        given(cartItemRepository.findByCartAndProductId(cart, 10L)).willReturn(Optional.empty());
        given(cartItemRepository.save(any(CartItem.class))).willAnswer(returnsFirstArg());

        AddCartItemResult result = cartService.addItem(1L, new AddCartItemRequest(10L, 2), STOCK_10);

        assertThat(result.created()).isTrue();
        assertThat(result.response().quantity()).isEqualTo(2);

        ArgumentCaptor<CartItem> captor = ArgumentCaptor.forClass(CartItem.class);
        verify(cartItemRepository).save(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(10L);
        assertThat(captor.getValue().getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("이미 담긴 상품이면 수량을 더한다")
    void addsQuantityToExistingItemOnAdd() {
        Cart cart = Cart.builder().memberId(1L).build();
        CartItem item = CartItem.builder().cart(cart).productId(10L).quantity(2).build();
        given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
        given(cartItemRepository.findByCartAndProductId(cart, 10L)).willReturn(Optional.of(item));

        AddCartItemResult result = cartService.addItem(1L, new AddCartItemRequest(10L, 3), STOCK_10);

        assertThat(result.created()).isFalse();
        assertThat(result.response().quantity()).isEqualTo(5);

        assertThat(item.getQuantity()).isEqualTo(5);
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    @Test
    @DisplayName("번개 드롭 상품을 이미 담았으면 한 개 더 담을 수 없고 수량은 그대로다")
    void rejectsAddingFlashDropAlreadyInCart() {
        Cart cart = Cart.builder().memberId(1L).build();
        CartItem item = CartItem.builder().cart(cart).productId(10L).quantity(1).build();
        given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
        given(cartItemRepository.findByCartAndProductId(cart, 10L)).willReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.addItem(1L, new AddCartItemRequest(10L, 1), FLASH_DROP))
                .isInstanceOf(BusinessException.class)
                .hasMessage("구매 한도 초과 · 대파")
                .extracting("errorCode").isEqualTo(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
        assertThat(item.getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("기존 수량에 더해 재고를 넘으면 거절되고 수량은 그대로다")
    void rejectsAddingOverStock() {
        Cart cart = Cart.builder().memberId(1L).build();
        CartItem item = CartItem.builder().cart(cart).productId(10L).quantity(8).build();
        given(cartRepository.findByMemberId(1L)).willReturn(Optional.of(cart));
        given(cartItemRepository.findByCartAndProductId(cart, 10L)).willReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.addItem(1L, new AddCartItemRequest(10L, 3), STOCK_10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        assertThat(item.getQuantity()).isEqualTo(8);
    }

    @Test
    @DisplayName("번개 드롭 상품의 수량을 2로 바꿀 수 없고 수량은 그대로다")
    void rejectsFlashDropQuantityOverOne() {
        Cart cart = Cart.builder().memberId(1L).build();
        CartItem item = CartItem.builder().cart(cart).productId(10L).quantity(1).build();
        given(cartItemRepository.findByIdAndCartMemberId(5L, 1L)).willReturn(Optional.of(item));

        assertThatThrownBy(() -> cartService.changeQuantity(1L, 5L, new ChangeCartItemQuantityRequest(2), FLASH_DROP))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
        assertThat(item.getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("재고가 줄어든 뒤에도 수량을 내리는 것은 반영된다")
    void allowsDecreasingEvenOverStock() {
        Cart cart = Cart.builder().memberId(1L).build();
        CartItem item = CartItem.builder().cart(cart).productId(10L).quantity(5).build();
        given(cartItemRepository.findByIdAndCartMemberId(5L, 1L)).willReturn(Optional.of(item));

        cartService.changeQuantity(1L, 5L, new ChangeCartItemQuantityRequest(3), new CartQuantityLimit("대파", 2, false));

        assertThat(item.getQuantity()).isEqualTo(3);
    }
}
