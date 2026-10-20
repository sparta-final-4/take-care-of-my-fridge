package org.example.takecareofmyfridgebe.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResult;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartItemResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartItemsResponse;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.dummy.ProductFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

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

    private Product productWithStock(int stock) {
        return Product.builder()
                .productType(ProductType.INGREDIENT)
                .name("대파")
                .price(1000)
                .stock(stock)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .build();
    }
}
