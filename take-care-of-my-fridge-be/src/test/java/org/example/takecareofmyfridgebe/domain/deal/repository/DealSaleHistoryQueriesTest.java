package org.example.takecareofmyfridgebe.domain.deal.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class DealSaleHistoryQueriesTest extends MySqlContainerSupport {

    @Autowired private EntityManager entityManager;
    @Autowired private SaleEventItemRepository saleEventItemRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    @Test
    void readsFinishedSpecialAndOnlyConfirmedNetUnits() {
        LocalDateTime start = LocalDateTime.of(2026, 9, 1, 0, 0);
        LocalDateTime end = start.plusDays(1);
        SaleEventItem special = eventItem(SaleEventType.SPECIAL, 1L, start, end);
        eventItem(SaleEventType.FLASH_DROP, 2L, start, end);
        orderItem(special, "20260901-000001", start.plusHours(2), end.plusDays(1), 2);
        orderItem(special, "20260901-000002", start.plusHours(3), null, 3);
        entityManager.flush();
        entityManager.clear();

        assertThat(saleEventItemRepository.findFinishedForTraining(SaleEventType.SPECIAL, end.minusSeconds(1)))
                .isEmpty();
        assertThat(saleEventItemRepository.findFinishedForTraining(SaleEventType.SPECIAL, end))
                .extracting(SaleEventItem::getProductId).containsExactly(1L);
        assertThat(orderItemRepository.sumConfirmedSaleQuantity(
                special.getId(), OrderStatus.CONFIRMED, end.plusDays(2))).isEqualTo(2);
        assertThat(orderItemRepository.sumConfirmedSaleQuantity(
                special.getId(), OrderStatus.CONFIRMED, end)).isZero();
        assertThat(orderItemRepository.countUnconfirmedPaidSaleOrders(
                special.getId(), OrderStatus.PAID, end.plusDays(2))).isEqualTo(1);
    }

    private SaleEventItem eventItem(SaleEventType type, Long productId,
                                    LocalDateTime start, LocalDateTime end) {
        SaleEvent event = SaleEvent.builder().type(type).startsAt(start).endsAt(end).build();
        entityManager.persist(event);
        SaleEventItem item = SaleEventItem.builder().saleEvent(event).productId(productId)
                .listPrice(1000).discountRate(20)
                .limitedQuantity(type == SaleEventType.FLASH_DROP ? 10 : null).build();
        entityManager.persist(item);
        return item;
    }

    private void orderItem(SaleEventItem saleItem, String number, LocalDateTime paidAt,
                           LocalDateTime confirmedAt, int quantity) {
        Order order = Order.builder().orderNumber(number).memberId(1L).totalAmount(1000L).build();
        entityManager.persist(order);
        order.markPaid(paidAt);
        if (confirmedAt != null) {
            order.confirm(confirmedAt);
        }
        OrderItem item = OrderItem.builder().order(order).productId(saleItem.getProductId())
                .productName("상품").quantity(quantity).listPrice(1000).unitPrice(800)
                .saleType(SaleType.SPECIAL).saleEventItemId(saleItem.getId()).build();
        entityManager.persist(item);
        if (confirmedAt != null) {
            item.confirmQuantity(0);
        }
    }
}
