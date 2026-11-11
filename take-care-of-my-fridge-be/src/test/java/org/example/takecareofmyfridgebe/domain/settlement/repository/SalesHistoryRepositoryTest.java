package org.example.takecareofmyfridgebe.domain.settlement.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.entity.BillingKey;
import org.example.takecareofmyfridgebe.domain.payment.entity.CardType;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentHistory;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.payment.service.BillingKeyCryptoFixture;
import org.example.takecareofmyfridgebe.domain.settlement.dto.DailySalesSum;
import org.example.takecareofmyfridgebe.domain.settlement.dto.PaymentMethodSalesSum;
import org.example.takecareofmyfridgebe.domain.settlement.dto.PaymentSalesRow;
import org.example.takecareofmyfridgebe.global.config.jpa.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.jpa.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class SalesHistoryRepositoryTest extends MySqlContainerSupport {

    private static final LocalDateTime FROM = LocalDateTime.of(2026, 10, 28, 0, 0);
    private static final LocalDateTime TO = LocalDateTime.of(2026, 10, 29, 0, 0);
    private static final String ORDER_NUMBER = "20261028-000001";
    private static final String BUYER = "구매자";

    private final SalesHistoryRepository salesHistoryRepository;
    private final EntityManager entityManager;

    private Member member;
    private Order order;
    private int sequence;

    @Autowired
    SalesHistoryRepositoryTest(SalesHistoryRepository salesHistoryRepository, EntityManager entityManager) {
        this.salesHistoryRepository = salesHistoryRepository;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        member = Member.builder().email("buyer@example.com").passwordHash("hash").nickname(BUYER)
                .phone("01012345678").build();
        entityManager.persist(member);
        order = Order.builder().orderNumber(ORDER_NUMBER).memberId(member.getId()).totalAmount(50000L).build();
        entityManager.persist(order);
    }

    @Test
    @DisplayName("시작 시각 이상 종료 시각 미만의 결제 이력만 합산한다")
    void sumSalesBetween_sumOnlyInRange() {
        history(paidPayment(10000L), PaymentEventType.PAID, 10000L, FROM);
        history(paidPayment(20000L), PaymentEventType.PAID, 20000L, TO.minusSeconds(1));
        history(paidPayment(40000L), PaymentEventType.PAID, 40000L, FROM.minusSeconds(1));
        history(paidPayment(80000L), PaymentEventType.PAID, 80000L, TO);
        flushAndClear();

        assertThat(sum()).isEqualTo(new DailySalesSum(30000L, 2L, 0L, 0L));
    }

    @Test
    @DisplayName("환불과 콘솔 취소의 부분·전액 취소를 모두 환불 금액·건수로 합산한다")
    void sumSalesBetween_sumAllCancellationsAsRefunds() {
        Payment refunded = paidPayment(30000L);
        history(refunded, PaymentEventType.PAID, 30000L, FROM.plusHours(1));
        history(refunded, PaymentEventType.PARTIAL_CANCELLED, 10000L, FROM.plusHours(2));
        history(refunded, PaymentEventType.CANCELLED, 20000L, FROM.plusHours(3));
        Payment paidYesterday = paidPayment(5000L);
        history(paidYesterday, PaymentEventType.PAID, 5000L, FROM.minusHours(1));
        history(paidYesterday, PaymentEventType.CANCELLED, 5000L, FROM.plusHours(4));
        flushAndClear();

        assertThat(sum()).isEqualTo(new DailySalesSum(30000L, 1L, 35000L, 3L));
    }

    @Test
    @DisplayName("받지 않은 결제의 PG 승인과 보상 취소, 금액 없는 이력은 합산하지 않는다")
    void sumSalesBetween_excludeUnacceptedAndNonMoneyEvents() {
        Payment unaccepted = payment(7000L);
        unaccepted.fail(FailReason.TIMEOUT);
        history(unaccepted, PaymentEventType.PAID, 7000L, FROM.plusHours(1));
        history(unaccepted, PaymentEventType.CANCELLED, 7000L, FROM.plusHours(2));
        Payment paid = paidPayment(9000L);
        history(paid, PaymentEventType.READY, 9000L, FROM.plusHours(3));
        history(paid, PaymentEventType.PAID, 9000L, FROM.plusHours(3));
        flushAndClear();

        assertThat(sum()).isEqualTo(new DailySalesSum(9000L, 1L, 0L, 0L));
    }

    @Test
    @DisplayName("이력이 없으면 모두 0이다")
    void sumSalesBetween_returnZero_whenNoHistory() {
        assertThat(sum()).isEqualTo(new DailySalesSum(0L, 0L, 0L, 0L));
    }

    @Test
    @DisplayName("결제 건별 목록은 기간 안의 결제·환불을 결제마다 묶어 마지막 사건이 최근인 순으로 주고, 모두 더하면 기간 합계와 같다")
    void findPaymentSalesBetween_groupByPaymentAndMatchSum() {
        Payment partlyRefunded = paidPayment(10000L, FROM.plusHours(1));
        history(partlyRefunded, PaymentEventType.PAID, 10000L, FROM.plusHours(1));
        history(partlyRefunded, PaymentEventType.PARTIAL_CANCELLED, 3000L, FROM.plusHours(2));
        Payment paidYesterday = paidPayment(5000L, FROM.minusHours(1));
        history(paidYesterday, PaymentEventType.PAID, 5000L, FROM.minusHours(1));
        history(paidYesterday, PaymentEventType.CANCELLED, 5000L, FROM.plusHours(3));
        Payment paidLater = paidPayment(7000L, FROM.plusHours(5));
        history(paidLater, PaymentEventType.READY, 7000L, FROM.plusHours(5));
        history(paidLater, PaymentEventType.PAID, 7000L, FROM.plusHours(5));
        Payment unaccepted = payment(9000L);
        unaccepted.fail(FailReason.TIMEOUT);
        history(unaccepted, PaymentEventType.PAID, 9000L, FROM.plusHours(6));
        flushAndClear();

        Page<PaymentSalesRow> page = salesHistoryRepository.findPaymentSalesBetween(FROM, TO, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).containsExactly(
                row(paidLater, FROM.plusHours(5), 7000L, 0L),
                row(paidYesterday, FROM.minusHours(1), 0L, 5000L),
                row(partlyRefunded, FROM.plusHours(1), 10000L, 3000L));
        assertThat(sum()).isEqualTo(new DailySalesSum(17000L, 2L, 8000L, 2L));
    }

    @Test
    @DisplayName("결제 건별 목록은 페이지 크기만큼 나누고 전체 결제 수를 센다")
    void findPaymentSalesBetween_paginate() {
        for (int hour = 1; hour <= 3; hour++) {
            Payment paid = paidPayment(1000L, FROM.plusHours(hour));
            history(paid, PaymentEventType.PAID, 1000L, FROM.plusHours(hour));
            history(paid, PaymentEventType.PARTIAL_CANCELLED, 100L, FROM.plusHours(hour + 1));
        }
        flushAndClear();

        Page<PaymentSalesRow> page = salesHistoryRepository.findPaymentSalesBetween(FROM, TO, PageRequest.of(1, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(PaymentSalesRow::paidAt).containsExactly(FROM.plusHours(1));
    }

    @Test
    @DisplayName("결제 건별 목록은 등록 카드 결제 여부와 실제로 승인한 결제대행사를 함께 준다")
    void findPaymentSalesBetween_includePaymentMethod() {
        Payment cardPayment = paidPayment(registeredCard(), PgProvider.TOSSPAYMENTS, 12000L);
        history(cardPayment, PaymentEventType.PAID, 12000L, FROM.plusHours(1));
        flushAndClear();

        Page<PaymentSalesRow> page = salesHistoryRepository.findPaymentSalesBetween(FROM, TO, PageRequest.of(0, 10));

        assertThat(page.getContent()).containsExactly(new PaymentSalesRow(cardPayment.getId(), FROM, ORDER_NUMBER,
                BUYER, true, PgProvider.TOSSPAYMENTS, 12000L, 0L));
    }

    @Test
    @DisplayName("결제 수단별 합계는 등록 카드 결제를 결제창 결제와 따로 묶고, 환불은 원래 결제의 수단으로 세며, 모두 더하면 기간 합계와 같다")
    void sumSalesByMethodBetween_groupByMethodAndMatchSum() {
        BillingKey card = registeredCard();
        Payment cardPaid = paidPayment(card, PgProvider.TOSSPAYMENTS, 30000L);
        history(cardPaid, PaymentEventType.PAID, 30000L, FROM.plusHours(1));
        history(cardPaid, PaymentEventType.PARTIAL_CANCELLED, 5000L, FROM.plusHours(2));
        Payment cardPaidAgain = paidPayment(card, PgProvider.TOSSPAYMENTS, 10000L);
        history(cardPaidAgain, PaymentEventType.PAID, 10000L, FROM.plusHours(3));
        Payment windowToss = paidPayment(null, PgProvider.TOSSPAYMENTS, 20000L);
        history(windowToss, PaymentEventType.PAID, 20000L, FROM.plusHours(4));
        Payment kakaoYesterday = paidPayment(null, PgProvider.KAKAOPAY, 8000L);
        history(kakaoYesterday, PaymentEventType.PAID, 8000L, FROM.minusHours(1));
        history(kakaoYesterday, PaymentEventType.CANCELLED, 8000L, FROM.plusHours(5));
        Payment unaccepted = payment(9000L);
        unaccepted.fail(FailReason.TIMEOUT);
        history(unaccepted, PaymentEventType.PAID, 9000L, FROM.plusHours(6));
        flushAndClear();

        List<PaymentMethodSalesSum> sums = salesHistoryRepository.sumSalesByMethodBetween(FROM, TO);

        assertThat(sums).containsExactlyInAnyOrder(
                new PaymentMethodSalesSum(true, PgProvider.TOSSPAYMENTS, 40000L, 2L, 5000L, 1L),
                new PaymentMethodSalesSum(false, PgProvider.TOSSPAYMENTS, 20000L, 1L, 0L, 0L),
                new PaymentMethodSalesSum(false, PgProvider.KAKAOPAY, 0L, 0L, 8000L, 1L));
        assertThat(sum()).isEqualTo(new DailySalesSum(60000L, 3L, 13000L, 2L));
    }

    @Test
    @DisplayName("등록 카드 결제는 만들 때의 결제대행사가 아니라 승인할 때 PG가 알려 준 결제대행사로 묶는다")
    void sumSalesByMethodBetween_groupByApprovedPgProvider() {
        Payment cardPaid = Payment.builder().order(order).billingKey(registeredCard())
                .portonePaymentId("payment-" + ++sequence).amount(15000L).pgProvider(PgProvider.TOSSPAYMENTS)
                .expiresAt(FROM.plusDays(1)).build();
        entityManager.persist(cardPaid);
        cardPaid.complete(FROM, PgProvider.KICC);
        history(cardPaid, PaymentEventType.PAID, 15000L, FROM.plusHours(1));
        flushAndClear();

        assertThat(salesHistoryRepository.sumSalesByMethodBetween(FROM, TO))
                .containsExactly(new PaymentMethodSalesSum(true, PgProvider.KICC, 15000L, 1L, 0L, 0L));
    }

    @Test
    @DisplayName("결제 수단별 합계는 기간 안에 결제·환불이 없으면 빈 목록이다")
    void sumSalesByMethodBetween_returnEmpty_whenNoHistory() {
        assertThat(salesHistoryRepository.sumSalesByMethodBetween(FROM, TO)).isEmpty();
    }

    private PaymentSalesRow row(Payment payment, LocalDateTime paidAt, long paidAmount, long refundedAmount) {
        return new PaymentSalesRow(payment.getId(), paidAt, ORDER_NUMBER, BUYER, false, PgProvider.KAKAOPAY,
                paidAmount, refundedAmount);
    }

    private BillingKey registeredCard() {
        BillingKey card = BillingKey.builder().memberId(member.getId())
                .encryptedBillingKey(BillingKeyCryptoFixture.encrypted(member.getId(), "billing-key-" + ++sequence))
                .cardName("농협카드").cardType(CardType.CREDIT).isDefault(true).build();
        entityManager.persist(card);
        return card;
    }

    private Payment paidPayment(BillingKey card, PgProvider pgProvider, long amount) {
        Payment payment = Payment.builder().order(order).billingKey(card).portonePaymentId("payment-" + ++sequence)
                .amount(amount).pgProvider(pgProvider).expiresAt(FROM.plusDays(1)).build();
        entityManager.persist(payment);
        payment.complete(FROM, pgProvider);
        return payment;
    }

    private DailySalesSum sum() {
        return salesHistoryRepository.sumSalesBetween(FROM, TO);
    }

    private Payment paidPayment(long amount) {
        return paidPayment(amount, FROM);
    }

    private Payment paidPayment(long amount, LocalDateTime paidAt) {
        Payment payment = payment(amount);
        payment.complete(paidAt, PgProvider.KAKAOPAY);
        return payment;
    }

    private Payment payment(long amount) {
        Payment payment = Payment.builder().order(order).portonePaymentId("payment-" + ++sequence).amount(amount)
                .pgProvider(PgProvider.KAKAOPAY).expiresAt(FROM.plusDays(1)).build();
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
