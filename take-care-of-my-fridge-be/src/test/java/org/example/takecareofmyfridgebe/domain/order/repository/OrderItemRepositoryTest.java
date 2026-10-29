package org.example.takecareofmyfridgebe.domain.order.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.order.dto.DailyProductSales;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class OrderItemRepositoryTest extends MySqlContainerSupport {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 19);
    private static final LocalDateTime FROM = DAY.atStartOfDay();
    private static final LocalDateTime TO = DAY.plusDays(1).atStartOfDay();

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private OrderItemRepository orderItemRepository;

    private int orderSequence;

    @Test
    @DisplayName("기준 시각 뒤에 구매 확정된 주문은 판매량에 들어가지 않는다")
    void findDailyProductSales_excludesConfirmedAfterAsOf() {
        confirmedOrder(DAY.atTime(10, 0), 10L, 2);
        confirmedOrder(DAY.atTime(15, 0), 10L, 3);
        flushAndClear();

        assertThat(find(DAY.atTime(12, 0)))
            .containsExactly(new DailyProductSales(10L, DAY, 2));
        assertThat(find(DAY.atTime(15, 0)))
            .containsExactly(new DailyProductSales(10L, DAY, 5));
    }

    @Test
    @DisplayName("결제 대기·결제 완료·취소 주문은 판매량에 들어가지 않는다")
    void findDailyProductSales_onlyConfirmedOrders() {
        confirmedOrder(DAY.atTime(10, 0), 10L, 1);

        Order pending = persistOrder();
        persistItem(pending, 10L, 4);

        Order paid = persistOrder();
        paid.markPaid(DAY.atTime(9, 0));
        persistItem(paid, 10L, 4);

        Order cancelled = persistOrder();
        cancelled.cancel();
        persistItem(cancelled, 10L, 4);
        flushAndClear();

        assertThat(find(TO))
            .containsExactly(new DailyProductSales(10L, DAY, 1));
    }

    @Test
    @DisplayName("같은 상품·같은 날 주문은 하나로 합치고, 마지막 날은 하루 통째로 들어가며 그다음 날은 빠진다")
    void findDailyProductSales_sumsByProductAndDayWithinRange() {
        confirmedOrder(DAY.atStartOfDay(), 10L, 1);
        confirmedOrder(DAY.atTime(23, 59, 59), 10L, 2);
        confirmedOrder(DAY.atTime(13, 0), 20L, 4);
        confirmedOrder(DAY.minusDays(1).atTime(23, 59, 59), 10L, 8);
        confirmedOrder(TO, 10L, 16);
        flushAndClear();

        List<DailyProductSales> sales = find(TO.plusDays(1));

        assertThat(sales).containsExactly(
            new DailyProductSales(10L, DAY, 3),
            new DailyProductSales(20L, DAY, 4));
    }

    @Test
    @DisplayName("확정 전에 환불이 완료된 수량은 확정 수량에서 빠져 판매량에 들어가지 않는다")
    void findDailyProductSales_usesConfirmedQuantity() {
        confirmedOrder(DAY.atTime(10, 0), 10L, 5, 2);
        flushAndClear();

        assertThat(find(TO))
            .containsExactly(new DailyProductSales(10L, DAY, 3));
    }

    @Test
    @DisplayName("평상시 기준선은 같은 날의 할인 행사 주문을 제외한다")
    void findDailyRegularProductSales_excludesSaleOrders() {
        confirmedOrder(DAY.atTime(10, 0), 10L, 3, 1);
        Order saleOrder = persistOrder();
        saleOrder.markPaid(DAY.atTime(10, 0));
        saleOrder.confirm(DAY.atTime(11, 0));
        OrderItem saleItem = OrderItem.builder()
            .order(saleOrder).productId(10L).productName("특가 상품")
            .quantity(5).listPrice(1000).unitPrice(900)
            .saleType(SaleType.SPECIAL).saleEventItemId(10L).build();
        entityManager.persist(saleItem);
        saleItem.confirmQuantity(0);
        flushAndClear();

        assertThat(orderItemRepository.findDailyRegularProductSales(OrderStatus.CONFIRMED, FROM, TO, TO))
            .containsExactly(new DailyProductSales(10L, DAY, 2));
    }
    private List<DailyProductSales> find(LocalDateTime asOf) {
        return orderItemRepository.findDailyProductSales(OrderStatus.CONFIRMED, FROM, TO, asOf);
    }

    private void confirmedOrder(LocalDateTime confirmedAt, Long productId, int quantity) {
        confirmedOrder(confirmedAt, productId, quantity, 0);
    }

    // 구매 확정 서비스처럼 확정하면서 주문 수량에서 완료된 환불 수량을 뺀 확정 수량을 저장한다
    private void confirmedOrder(LocalDateTime confirmedAt, Long productId, int quantity, long completedRefundQuantity) {
        Order order = persistOrder();
        order.markPaid(confirmedAt.minusHours(1));
        order.confirm(confirmedAt);
        persistItem(order, productId, quantity).confirmQuantity(completedRefundQuantity);
    }

    private Order persistOrder() {
        orderSequence++;
        Order order = Order.builder()
            .orderNumber("20261019-9%05d".formatted(orderSequence))
            .memberId(1L)
            .totalAmount(1000L * orderSequence)
            .build();
        entityManager.persist(order);
        return order;
    }

    private OrderItem persistItem(Order order, Long productId, int quantity) {
        OrderItem item = OrderItem.builder()
            .order(order)
            .productId(productId)
            .productName("상품" + productId)
            .quantity(quantity)
            .listPrice(1000)
            .unitPrice(1000)
            .saleType(SaleType.NONE)
            .build();
        entityManager.persist(item);
        return item;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
