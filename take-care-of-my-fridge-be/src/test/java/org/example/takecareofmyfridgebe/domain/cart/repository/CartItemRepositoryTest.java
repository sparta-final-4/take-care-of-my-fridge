package org.example.takecareofmyfridgebe.domain.cart.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class CartItemRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final EntityManager entityManager;

    @Autowired
    CartItemRepositoryTest(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            EntityManager entityManager) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.entityManager = entityManager;
    }

    @Test
    @DisplayName("같은 상품이 다른 회원의 장바구니에 있어도 내 장바구니의 줄만 찾는다")
    void findByCartAndProductId_doesNotMixCarts() {
        Cart cartA = cartRepository.save(Cart.builder().memberId(1L).build());
        Cart cartB = cartRepository.save(Cart.builder().memberId(2L).build());
        cartItemRepository.save(CartItem.builder().cart(cartA).productId(10L).quantity(1).build());
        cartItemRepository.save(CartItem.builder().cart(cartB).productId(10L).quantity(5).build());
        flushAndClear();

        CartItem foundA = cartItemRepository.findByCartAndProductId(cartA, 10L).orElseThrow();
        CartItem foundB = cartItemRepository.findByCartAndProductId(cartB, 10L).orElseThrow();

        assertThat(foundA.getQuantity()).isEqualTo(1);
        assertThat(foundB.getQuantity()).isEqualTo(5);
        assertThat(foundA.getId()).isNotEqualTo(foundB.getId());
    }

    @Test
    @DisplayName("다른 회원의 줄은 조회되지 않는다")
    void findByCartMemberId_excludesOtherMembersItems() {
        Cart cartA = cartRepository.save(Cart.builder().memberId(1L).build());
        cartRepository.save(Cart.builder().memberId(2L).build());
        cartItemRepository.save(CartItem.builder().cart(cartA).productId(10L).quantity(1).build());
        cartItemRepository.save(CartItem.builder().cart(cartA).productId(20L).quantity(2).build());
        flushAndClear();

        List<CartItem> memberB = cartItemRepository.findByCartMemberIdOrderByIdAsc(2L);
        List<CartItem> memberA = cartItemRepository.findByCartMemberIdOrderByIdAsc(1L);

        assertThat(memberB).isEmpty();
        assertThat(memberA).extracting(CartItem::getProductId).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("장바구니가 없는 회원은 빈 목록이다")
    void findByCartMemberId_returnsEmptyWithoutCart() {
        Cart cartA = cartRepository.save(Cart.builder().memberId(1L).build());
        cartItemRepository.save(CartItem.builder().cart(cartA).productId(10L).quantity(1).build());
        flushAndClear();

        List<CartItem> memberWithoutCart = cartItemRepository.findByCartMemberIdOrderByIdAsc(3L);

        assertThat(memberWithoutCart).isEmpty();
    }

    @Test
    @DisplayName("담은 순서(id 오름차순)로 조회한다")
    void findByCartMemberId_ordersById() {
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        cartItemRepository.save(CartItem.builder().cart(cart).productId(30L).quantity(1).build());
        cartItemRepository.save(CartItem.builder().cart(cart).productId(10L).quantity(1).build());
        cartItemRepository.save(CartItem.builder().cart(cart).productId(20L).quantity(1).build());
        flushAndClear();

        List<CartItem> items = cartItemRepository.findByCartMemberIdOrderByIdAsc(1L);

        assertThat(items).extracting(CartItem::getProductId).containsExactly(30L, 10L, 20L);
    }

    // 영속성 컨텍스트 캐시가 아니라 DB에서 다시 읽게 한다.
    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
