package org.example.takecareofmyfridgebe.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.RemoveCartItemsRequest;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 DB에 반영되는지 확인한다.
// 테스트 트랜잭션이 켜져 있으면 서비스에서 @Transactional이 빠져도 가려진다.
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, CartService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CartServiceTransactionTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    private final CartService cartService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Autowired
    CartServiceTransactionTest(
            CartService cartService,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository) {
        this.cartService = cartService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
    }

    @AfterEach
    void cleanUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
    }

    @Test
    @DisplayName("수량 변경은 DB에 반영된다")
    void changeQuantity_persistsQuantity() {
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).productId(10L).quantity(1).build());

        ChangeCartItemQuantityResponse response = cartService.changeQuantity(1L, item.getId(), new ChangeCartItemQuantityRequest(3));

        assertThat(response.quantity()).isEqualTo(3);
        assertThat(cartItemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("다른 회원의 줄은 바꿀 수 없고 수량은 그대로다")
    void changeQuantity_rejectsOthersItem() {
        Cart cartA = cartRepository.save(Cart.builder().memberId(1L).build());
        cartRepository.save(Cart.builder().memberId(2L).build());
        CartItem itemOfA = cartItemRepository.save(CartItem.builder().cart(cartA).productId(10L).quantity(1).build());

        assertThatThrownBy(() -> cartService.changeQuantity(2L, itemOfA.getId(), new ChangeCartItemQuantityRequest(5)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
        assertThat(cartItemRepository.findById(itemOfA.getId()).orElseThrow().getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 줄 번호는 찾을 수 없다")
    void changeQuantity_rejectsMissingItem() {
        assertThatThrownBy(() -> cartService.changeQuantity(1L, 999L, new ChangeCartItemQuantityRequest(3)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("목록에 든 내 줄은 DB에서 지워지고 나머지는 남는다")
    void removeItems_deletesListedOwnItems() {
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem first = cartItemRepository.save(CartItem.builder().cart(cart).productId(10L).quantity(1).build());
        CartItem second = cartItemRepository.save(CartItem.builder().cart(cart).productId(20L).quantity(1).build());

        cartService.removeItems(1L, new RemoveCartItemsRequest(List.of(first.getId())));

        assertThat(cartItemRepository.findAll()).extracting(CartItem::getId).containsExactly(second.getId());
    }

    @Test
    @DisplayName("다른 회원의 줄과 없는 번호가 섞여도 내 줄만 지우고 오류는 없다")
    void removeItems_ignoresOthersAndMissingIds() {
        Cart cartA = cartRepository.save(Cart.builder().memberId(1L).build());
        Cart cartB = cartRepository.save(Cart.builder().memberId(2L).build());
        CartItem itemOfA = cartItemRepository.save(CartItem.builder().cart(cartA).productId(10L).quantity(1).build());
        CartItem itemOfB = cartItemRepository.save(CartItem.builder().cart(cartB).productId(10L).quantity(1).build());

        cartService.removeItems(1L, new RemoveCartItemsRequest(List.of(itemOfA.getId(), itemOfB.getId(), 999L)));

        assertThat(cartItemRepository.findAll()).extracting(CartItem::getId).containsExactly(itemOfB.getId());
    }

    @Test
    @DisplayName("같은 요청을 두 번 보내도 같은 결과다")
    void removeItems_isIdempotent() {
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).productId(10L).quantity(1).build());
        RemoveCartItemsRequest request = new RemoveCartItemsRequest(List.of(item.getId()));

        cartService.removeItems(1L, request);
        cartService.removeItems(1L, request);

        assertThat(cartItemRepository.findAll()).isEmpty();
    }
}
