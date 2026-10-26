package org.example.takecareofmyfridgebe.domain.refund.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundedQuantity;
import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class RefundItemRepositoryTest extends MySqlContainerSupport {

    private static final List<RefundStatus> ACTIVE_STATUSES = List.of(RefundStatus.REQUESTED, RefundStatus.COMPLETED);

    private final RefundItemRepository refundItemRepository;
    private final EntityManager entityManager;

    private Payment payment;
    private OrderItem itemA;
    private OrderItem itemB;

    @Autowired
    RefundItemRepositoryTest(RefundItemRepository refundItemRepository, EntityManager entityManager) {
        this.refundItemRepository = refundItemRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        Order order = Order.builder().orderNumber("20261022-000001").memberId(1L).totalAmount(50000L).build();
        entityManager.persist(order);
        itemA = orderItem(order, 10L, 5);
        itemB = orderItem(order, 20L, 3);
        payment = Payment.builder().order(order).portonePaymentId("payment-1").amount(50000L)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();
        entityManager.persist(payment);
    }

    @Test
    @DisplayName("요청·완료 상태 환불의 수량을 주문 상품별로 합산한다")
    void sumsRequestedAndCompleted() {
        refund(RefundStatus.REQUESTED, itemA, 1);
        refund(RefundStatus.COMPLETED, itemA, 2);
        refund(RefundStatus.COMPLETED, itemB, 1);
        flushAndClear();

        Map<Long, Long> result = sum(List.of(itemA.getId(), itemB.getId()));

        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(itemA.getId(), 3L, itemB.getId(), 1L));
    }

    @Test
    @DisplayName("실패한 환불의 수량은 합산하지 않는다")
    void excludesFailed() {
        refund(RefundStatus.FAILED, itemA, 4);
        refund(RefundStatus.COMPLETED, itemA, 1);
        flushAndClear();

        assertThat(sum(List.of(itemA.getId()))).containsExactlyEntriesOf(Map.of(itemA.getId(), 1L));
    }

    @Test
    @DisplayName("요청하지 않은 주문 상품의 환불은 섞이지 않는다")
    void onlyRequestedOrderItems() {
        refund(RefundStatus.COMPLETED, itemA, 1);
        refund(RefundStatus.COMPLETED, itemB, 2);
        flushAndClear();

        assertThat(sum(List.of(itemB.getId()))).containsExactlyEntriesOf(Map.of(itemB.getId(), 2L));
    }

    @Test
    @DisplayName("환불이 없는 주문 상품은 결과에 없다")
    void noRefund_isEmpty() {
        flushAndClear();

        assertThat(sum(List.of(itemA.getId(), itemB.getId()))).isEmpty();
    }

    private Map<Long, Long> sum(List<Long> orderItemIds) {
        return refundItemRepository.findRefundedQuantitiesByOrderItemIds(orderItemIds, ACTIVE_STATUSES).stream()
                .collect(Collectors.toMap(RefundedQuantity::orderItemId, RefundedQuantity::quantity));
    }

    private OrderItem orderItem(Order order, Long productId, int quantity) {
        OrderItem item = OrderItem.builder().order(order).productId(productId).productName("상품" + productId)
                .quantity(quantity).listPrice(10000).unitPrice(10000).saleType(SaleType.NONE).build();
        entityManager.persist(item);
        return item;
    }

    private void refund(RefundStatus status, OrderItem orderItem, int quantity) {
        Refund refund = Refund.builder().payment(payment).refundAmount(10000L * quantity)
                .reasonType(RefundReasonType.CHANGE_OF_MIND).build();
        if (status == RefundStatus.COMPLETED) {
            refund.complete("cancellation-" + quantity, LocalDateTime.now());
        } else if (status == RefundStatus.FAILED) {
            refund.fail("PG 오류");
        }
        entityManager.persist(refund);
        entityManager.persist(RefundItem.builder().refund(refund).orderItem(orderItem).quantity(quantity).build());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
