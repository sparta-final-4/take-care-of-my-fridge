package org.example.takecareofmyfridgebe.domain.forecast.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
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
class ForecastSalesRepositoryTest extends MySqlContainerSupport {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ForecastSalesRepository repository;

    @Test
    void groupsConfirmedSalesAndOnlyCompletedRefundQuantities() {
        LocalDate day = LocalDate.of(2026, 10, 19);
        Order order = Order.builder().orderNumber("20261019-900001").memberId(1L)
                .totalAmount(5000).build();
        order.markPaid(day.atTime(9, 0));
        order.confirm(day.atTime(12, 0));
        entityManager.persist(order);
        OrderItem item = OrderItem.builder().order(order).productId(10L).productName("두부")
                .quantity(5).listPrice(1000).unitPrice(1000).saleType(SaleType.NONE).build();
        entityManager.persist(item);
        Payment payment = Payment.builder().order(order).portonePaymentId("forecast-repository-payment")
                .amount(5000L).expiresAt(day.atTime(10, 0)).build();
        payment.complete(day.atTime(9, 0));
        entityManager.persist(payment);
        Refund completed = Refund.builder().payment(payment).refundAmount(2000L)
                .reasonType(RefundReasonType.CHANGE_OF_MIND).build();
        completed.complete("forecast-cancellation", day.atTime(11, 0));
        entityManager.persist(completed);
        entityManager.persist(RefundItem.builder().refund(completed).orderItem(item).quantity(2).build());
        Refund failed = Refund.builder().payment(payment).refundAmount(1000L)
                .reasonType(RefundReasonType.ETC).build();
        failed.fail("PG rejected");
        entityManager.persist(failed);
        entityManager.persist(RefundItem.builder().refund(failed).orderItem(item).quantity(1).build());
        entityManager.flush();
        entityManager.clear();

        LocalDateTime from = day.atStartOfDay();
        LocalDateTime to = day.plusDays(1).atStartOfDay();
        LocalDateTime asOf = day.atTime(13, 0);
        assertThat(repository.findConfirmedSales(from, to, asOf)).singleElement()
                .satisfies(row -> assertThat(row.quantity()).isEqualTo(5));
        assertThat(repository.findCompletedRefunds(from, to, asOf, RefundStatus.COMPLETED))
                .singleElement().satisfies(row -> assertThat(row.quantity()).isEqualTo(2));
        assertThat(repository.findConfirmedSales(from, to, day.atTime(11, 0))).isEmpty();
    }
}
