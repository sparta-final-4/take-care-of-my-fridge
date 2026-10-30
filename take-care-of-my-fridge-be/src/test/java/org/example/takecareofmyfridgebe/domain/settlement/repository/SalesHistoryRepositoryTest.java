package org.example.takecareofmyfridgebe.domain.settlement.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentHistory;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class SalesHistoryRepositoryTest extends MySqlContainerSupport {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 28, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 29, 0, 0);
    private static final List<PaymentEventType> CANCEL_EVENTS =
            List.of(PaymentEventType.PARTIAL_CANCELLED, PaymentEventType.CANCELLED);

    private final SalesHistoryRepository salesHistoryRepository;
    private final EntityManager entityManager;

    private Order order;
    private int sequence;

    @Autowired
    SalesHistoryRepositoryTest(SalesHistoryRepository salesHistoryRepository, EntityManager entityManager) {
        this.salesHistoryRepository = salesHistoryRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        order = Order.builder().orderNumber("20261028-000001").memberId(1L).totalAmount(50000L).build();
        entityManager.persist(order);
    }

    @Test
    @DisplayName("시작 시각 이상 종료 시각 미만의 결제 이력만 합산한다")
    void sumBetween_onlyInRange() {
        history(paidPayment(10000L), PaymentEventType.PAID, 10000L, FROM);
        history(paidPayment(20000L), PaymentEventType.PAID, 20000L, TO.minusSeconds(1));
        history(paidPayment(40000L), PaymentEventType.PAID, 40000L, FROM.minusSeconds(1));
        history(paidPayment(80000L), PaymentEventType.PAID, 80000L, TO);
        flushAndClear();

        assertThat(sum()).isEqualTo(new DailySalesSum(30000L, 2L, 0L));
    }

    @Test
    @DisplayName("환불과 콘솔 취소의 부분·전액 취소 금액을 모두 환불 금액으로 합산한다")
    void sumBetween_sumsAllCancellations() {
        Payment refunded = paidPayment(30000L);
        history(refunded, PaymentEventType.PAID, 30000L, FROM.plusHours(1));
        history(refunded, PaymentEventType.PARTIAL_CANCELLED, 10000L, FROM.plusHours(2));
        history(refunded, PaymentEventType.CANCELLED, 20000L, FROM.plusHours(3));
        Payment paidYesterday = paidPayment(5000L);
        history(paidYesterday, PaymentEventType.PAID, 5000L, FROM.minusHours(1));
        history(paidYesterday, PaymentEventType.CANCELLED, 5000L, FROM.plusHours(4));
        flushAndClear();

        assertThat(sum()).isEqualTo(new DailySalesSum(30000L, 1L, 35000L));
    }

    @Test
    @DisplayName("받지 않은 결제의 PG 승인과 보상 취소, 금액 없는 이력은 합산하지 않는다")
    void sumBetween_excludesUnacceptedAndNonMoneyEvents() {
        Payment unaccepted = payment(7000L);
        unaccepted.fail(FailReason.TIMEOUT);
        history(unaccepted, PaymentEventType.PAID, 7000L, FROM.plusHours(1));
        history(unaccepted, PaymentEventType.CANCELLED, 7000L, FROM.plusHours(2));
        Payment paid = paidPayment(9000L);
        history(paid, PaymentEventType.READY, 9000L, FROM.plusHours(3));
        history(paid, PaymentEventType.PAID, 9000L, FROM.plusHours(3));
        flushAndClear();

        assertThat(sum()).isEqualTo(new DailySalesSum(9000L, 1L, 0L));
    }

    @Test
    @DisplayName("이력이 없으면 모두 0이다")
    void sumBetween_noHistory_isZero() {
        assertThat(sum()).isEqualTo(new DailySalesSum(0L, 0L, 0L));
    }

    private DailySalesSum sum() {
        return salesHistoryRepository.sumBetween(PaymentStatus.PAID, PaymentEventType.PAID, CANCEL_EVENTS, FROM, TO);
    }

    private Payment paidPayment(long amount) {
        Payment payment = payment(amount);
        payment.complete(FROM);
        return payment;
    }

    private Payment payment(long amount) {
        Payment payment = Payment.builder().order(order).portonePaymentId("payment-" + ++sequence).amount(amount)
                .expiresAt(FROM.plusDays(1)).build();
        entityManager.persist(payment);
        return payment;
    }

    // created_at은 감사 기능이 저장 시각으로 채우므로, 저장한 뒤 원하는 시각으로 바꾼다
    private void history(Payment payment, PaymentEventType eventType, long amount, LocalDateTime createdAt) {
        PaymentHistory history = PaymentHistory.builder().payment(payment).eventType(eventType).amount(amount)
                .changedBy(PaymentChangedBy.USER).build();
        entityManager.persist(history);
        entityManager.flush();
        entityManager.createNativeQuery("update payment_histories set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", history.getId())
                .executeUpdate();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
