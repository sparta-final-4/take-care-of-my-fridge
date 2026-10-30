package org.example.takecareofmyfridgebe.domain.cart.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.cart.service.CartService;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.payment.event.PaymentCompletedEvent;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

// 결제 완료 트랜잭션이 커밋된 뒤 리스너가 새 트랜잭션으로 장바구니를 지우는지 실제 DB로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, CartService.class, SaleEventReader.class,
        PaymentCompletedCartListener.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentCompletedCartListenerTest extends MySqlContainerSupport {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    PaymentCompletedCartListenerTest(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("결제 완료가 커밋되면 그 주문의 상품 줄만 내 장바구니에서 지우고 다른 상품과 다른 회원의 줄은 남긴다")
    void deletesOrderedProductsAfterCommit() {
        Cart mine = cartRepository.save(Cart.builder().memberId(1L).build());
        Cart others = cartRepository.save(Cart.builder().memberId(2L).build());
        saveCartItem(mine, 10L, 1);
        saveCartItem(mine, 20L, 5); // 주문 뒤 수량을 바꾼 줄도 지운다
        saveCartItem(mine, 30L, 1);
        saveCartItem(others, 10L, 1);
        Order order = saveOrder(1L, 10L, 20L);

        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new PaymentCompletedEvent(order.getId(), 1L)));

        assertThat(cartItemRepository.findAll())
                .extracting(item -> item.getCart().getId(), CartItem::getProductId)
                .containsExactlyInAnyOrder(tuple(mine.getId(), 30L), tuple(others.getId(), 10L));
    }

    @Test
    @DisplayName("결제 완료 트랜잭션이 롤백되면 장바구니를 지우지 않는다")
    void keepsCartWhenRolledBack() {
        Cart mine = cartRepository.save(Cart.builder().memberId(1L).build());
        saveCartItem(mine, 10L, 1);
        Order order = saveOrder(1L, 10L);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(new PaymentCompletedEvent(order.getId(), 1L));
            status.setRollbackOnly();
        });

        assertThat(cartItemRepository.findAll()).hasSize(1);
    }

    private void saveCartItem(Cart cart, Long productId, int quantity) {
        cartItemRepository.save(CartItem.builder().cart(cart).productId(productId).quantity(quantity).build());
    }

    private Order saveOrder(Long memberId, Long... productIds) {
        Order order = orderRepository.save(
                Order.builder().orderNumber("20261030-000001").memberId(memberId).totalAmount(1000L).build());
        for (Long productId : productIds) {
            orderItemRepository.save(OrderItem.builder().order(order).productId(productId)
                    .productName("상품" + productId).quantity(1).listPrice(1000).unitPrice(1000)
                    .saleType(SaleType.NONE).build());
        }
        return order;
    }
}
