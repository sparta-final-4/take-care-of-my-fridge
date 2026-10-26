package org.example.takecareofmyfridgebe.domain.refund.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.service.OrderReader;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentHistoryRecorder;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundHistoryItemResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundHistoryResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundSummaryResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundSummaryStatus;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundableItemResponse;
import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, RefundQueryService.class, OrderReader.class,
        PaymentHistoryRecorder.class})
class RefundQueryServiceTest extends MySqlContainerSupport {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    private final RefundQueryService refundQueryService;
    private final EntityManager entityManager;

    private Order order;
    private Payment payment;
    private OrderItem itemA; // 단가 3,000원, 5개
    private OrderItem itemB; // 단가 2,000원, 3개
    private OrderItem specialItem;
    private OrderItem flashDropItem;

    @Autowired
    RefundQueryServiceTest(RefundQueryService refundQueryService, EntityManager entityManager) {
        this.refundQueryService = refundQueryService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        order = order("20261023-000001");
        itemA = orderItem(order, 10L, 5, 3000, SaleType.NONE);
        itemB = orderItem(order, 20L, 3, 2000, SaleType.NONE);
        specialItem = orderItem(order, 30L, 1, 1000, SaleType.SPECIAL);
        flashDropItem = orderItem(order, 40L, 1, 1000, SaleType.FLASH_DROP);
        payment = pay(order);
        flushAndClear();
    }

    @Test
    @DisplayName("환불이 없으면 일반 상품은 주문 수량 전부, 특가·번개 드롭 상품은 0개를 주문 상품 순서대로 돌려준다")
    void noRefunds() {
        List<RefundableItemResponse> items = refundQueryService.getRefundableItems(MEMBER_ID, order.getId());

        assertThat(items)
                .extracting(RefundableItemResponse::orderItemId, RefundableItemResponse::orderedQuantity,
                        RefundableItemResponse::refundedQuantity, RefundableItemResponse::refundableQuantity,
                        RefundableItemResponse::refundable)
                .containsExactly(
                        tuple(itemA.getId(), 5, 0, 5, true),
                        tuple(itemB.getId(), 3, 0, 3, true),
                        tuple(specialItem.getId(), 1, 0, 0, false),
                        tuple(flashDropItem.getId(), 1, 0, 0, false));
        assertThat(items.get(0))
                .extracting(RefundableItemResponse::productId, RefundableItemResponse::productName,
                        RefundableItemResponse::unitPrice, RefundableItemResponse::saleType)
                .containsExactly(10L, "상품10", 3000, SaleType.NONE);
    }

    @Test
    @DisplayName("요청·완료된 환불 수량은 빼고, 실패한 환불 수량은 다시 환불할 수 있게 둔다")
    void subtractsCountedRefunds() {
        refund(itemA, 2);                    // 요청 상태
        refund(itemA, 1).complete("cancellation-1", LocalDateTime.now());
        refund(itemB, 3).fail("PG 거절");     // 실패 상태
        flushAndClear();

        List<RefundableItemResponse> items = refundQueryService.getRefundableItems(MEMBER_ID, order.getId());

        assertThat(items)
                .extracting(RefundableItemResponse::orderItemId, RefundableItemResponse::refundedQuantity,
                        RefundableItemResponse::refundableQuantity, RefundableItemResponse::refundable)
                .startsWith(
                        tuple(itemA.getId(), 3, 2, true),
                        tuple(itemB.getId(), 0, 3, true));
    }

    @Test
    @DisplayName("주문 수량을 모두 환불했으면 남은 수량 0개, 환불 불가로 돌려준다")
    void fullyRefunded() {
        refund(itemB, 3).complete("cancellation-1", LocalDateTime.now());
        flushAndClear();

        RefundableItemResponse itemBResponse = refundQueryService.getRefundableItems(MEMBER_ID, order.getId()).get(1);

        assertThat(itemBResponse)
                .extracting(RefundableItemResponse::refundedQuantity, RefundableItemResponse::refundableQuantity,
                        RefundableItemResponse::refundable)
                .containsExactly(3, 0, false);
    }

    @Test
    @DisplayName("구매 확정된 주문은 조회를 거절한다")
    void confirmedOrder() {
        entityManager.find(Order.class, order.getId()).confirm(LocalDateTime.now());
        flushAndClear();

        assertError(() -> refundQueryService.getRefundableItems(MEMBER_ID, order.getId()),
                ErrorCode.REFUND_ORDER_CONFIRMED);
    }

    @Test
    @DisplayName("결제 전 주문은 조회를 거절한다")
    void pendingOrder() {
        Order pending = order("20261023-000002");
        orderItem(pending, 10L, 1, 3000, SaleType.NONE);
        flushAndClear();

        assertError(() -> refundQueryService.getRefundableItems(MEMBER_ID, pending.getId()),
                ErrorCode.INVALID_REFUND_STATUS);
    }

    @Test
    @DisplayName("다른 회원의 주문은 없는 주문처럼 거절한다")
    void otherMembersOrder() {
        assertError(() -> refundQueryService.getRefundableItems(OTHER_MEMBER_ID, order.getId()),
                ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("환불 내역은 상태와 관계없이 최신순으로, 환불마다 자기 상품만 묶어 돌려준다")
    void history_latestFirstWithItems() {
        Refund first = refund(payment, itemA, 2);
        addItem(first, itemB, 1);
        first.complete("cancellation-1", LocalDateTime.now());
        Refund second = refund(payment, itemA, 1);
        second.fail("PG 거절");
        Refund third = refund(payment, itemB, 1);
        flushAndClear();

        List<RefundHistoryResponse> history = refundQueryService.getRefundHistory(MEMBER_ID, order.getId());

        assertThat(history)
                .extracting(RefundHistoryResponse::refundId, RefundHistoryResponse::status)
                .containsExactly(
                        tuple(third.getId(), RefundStatus.REQUESTED),
                        tuple(second.getId(), RefundStatus.FAILED),
                        tuple(first.getId(), RefundStatus.COMPLETED));
        RefundHistoryResponse completed = history.get(2);
        assertThat(completed.reasonType()).isEqualTo(RefundReasonType.CHANGE_OF_MIND);
        assertThat(completed.requestedAt()).isNotNull();
        assertThat(completed.refundedAt()).isNotNull();
        assertThat(completed.items())
                .extracting(RefundHistoryItemResponse::orderItemId, RefundHistoryItemResponse::productName,
                        RefundHistoryItemResponse::unitPrice, RefundHistoryItemResponse::quantity,
                        RefundHistoryItemResponse::amount)
                .containsExactly(
                        tuple(itemA.getId(), "상품10", 3000, 2, 6000L),
                        tuple(itemB.getId(), "상품20", 2000, 1, 2000L));
        assertThat(history.get(0).refundedAt()).isNull();
        assertThat(history.get(0).items()).extracting(RefundHistoryItemResponse::orderItemId)
                .containsExactly(itemB.getId());
    }

    @Test
    @DisplayName("다른 주문의 환불은 내역에 넣지 않는다")
    void history_onlyThisOrder() {
        Order other = order("20261023-000003");
        OrderItem otherItem = orderItem(other, 10L, 1, 3000, SaleType.NONE);
        Payment otherPayment = pay(other);
        Refund mine = refund(payment, itemA, 1);
        refund(otherPayment, otherItem, 1);
        flushAndClear();

        assertThat(refundQueryService.getRefundHistory(MEMBER_ID, order.getId()))
                .extracting(RefundHistoryResponse::refundId)
                .containsExactly(mine.getId());
    }

    @Test
    @DisplayName("환불이 없으면 빈 내역을 돌려준다")
    void history_empty() {
        assertThat(refundQueryService.getRefundHistory(MEMBER_ID, order.getId())).isEmpty();
    }

    @Test
    @DisplayName("구매 확정된 주문도 지난 환불 내역을 보여준다")
    void history_confirmedOrder() {
        refund(payment, itemA, 1).complete("cancellation-1", LocalDateTime.now());
        flushAndClear();
        entityManager.find(Order.class, order.getId()).confirm(LocalDateTime.now());
        flushAndClear();

        assertThat(refundQueryService.getRefundHistory(MEMBER_ID, order.getId())).hasSize(1);
    }

    @Test
    @DisplayName("다른 회원의 주문 환불 내역은 없는 주문처럼 거절한다")
    void history_otherMembersOrder() {
        assertError(() -> refundQueryService.getRefundHistory(OTHER_MEMBER_ID, order.getId()),
                ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("환불 요약: 완료된 환불이 없으면 NONE, 일부만 환불했으면 PARTIAL과 완료 금액을 요청한 주문 순서대로 돌려준다")
    void summaries_noneAndPartial() {
        Order other = order("20261023-000002");
        orderItem(other, 10L, 1, 3000, SaleType.NONE);
        pay(other);
        complete(refund(itemA, 2));
        flushAndClear();

        assertThat(refundQueryService.getRefundSummaries(MEMBER_ID, List.of(other.getId(), order.getId())))
                .extracting(RefundSummaryResponse::orderId, RefundSummaryResponse::refundStatus,
                        RefundSummaryResponse::refundedAmount)
                .containsExactly(
                        tuple(other.getId(), RefundSummaryStatus.NONE, 0L),
                        tuple(order.getId(), RefundSummaryStatus.PARTIAL, 6000L));
    }

    @Test
    @DisplayName("환불 요약: 특가·번개 드롭을 뺀 환불 가능한 상품을 모두 환불했으면 FULL이다")
    void summaries_full() {
        complete(refund(itemA, 5));
        complete(refund(itemB, 3));
        flushAndClear();

        assertThat(refundQueryService.getRefundSummaries(MEMBER_ID, List.of(order.getId())))
                .extracting(RefundSummaryResponse::refundStatus, RefundSummaryResponse::refundedAmount)
                .containsExactly(tuple(RefundSummaryStatus.FULL, 21000L));
    }

    @Test
    @DisplayName("환불 요약: 완료된 환불이 있어도 진행 중 환불이 있으면 PARTIAL보다 IN_PROGRESS가 먼저다. 금액은 완료된 환불만 세고 실패한 환불은 세지 않는다")
    void summaries_inProgressBeforePartial() {
        complete(refund(itemA, 1));
        refund(itemB, 1).fail("PG 거절");
        refund(itemA, 2);
        flushAndClear();

        assertThat(refundQueryService.getRefundSummaries(MEMBER_ID, List.of(order.getId())))
                .extracting(RefundSummaryResponse::refundStatus, RefundSummaryResponse::refundedAmount)
                .containsExactly(tuple(RefundSummaryStatus.IN_PROGRESS, 3000L));
    }

    @Test
    @DisplayName("환불 요약: 다른 회원의 주문과 없는 주문은 빼고 돌려준다")
    void summaries_otherMembersOrderExcluded() {
        assertThat(refundQueryService.getRefundSummaries(OTHER_MEMBER_ID, List.of(order.getId(), 999_999L))).isEmpty();
    }

    @Test
    @DisplayName("환불 요약: 고유 주문 ID가 없거나 50건을 넘으면 거절한다")
    void summaries_invalidSize() {
        assertError(() -> refundQueryService.getRefundSummaries(MEMBER_ID, List.of()),
                ErrorCode.INVALID_REFUND_SUMMARY_REQUEST);
        assertError(() -> refundQueryService.getRefundSummaries(MEMBER_ID, Arrays.asList(null, null)),
                ErrorCode.INVALID_REFUND_SUMMARY_REQUEST);
        assertError(() -> refundQueryService.getRefundSummaries(MEMBER_ID,
                LongStream.rangeClosed(1, 51).boxed().toList()), ErrorCode.INVALID_REFUND_SUMMARY_REQUEST);
    }

    @Test
    @DisplayName("환불 요약: 같은 주문 ID를 50번 넘게 보내도 고유 주문 기준으로 받고, 빈 값은 빼고 한 번만 돌려준다")
    void summaries_duplicatesAndNullsIgnored() {
        List<Long> requested = new ArrayList<>(Collections.nCopies(60, order.getId()));
        requested.add(null);

        assertThat(refundQueryService.getRefundSummaries(MEMBER_ID, requested))
                .extracting(RefundSummaryResponse::orderId)
                .containsExactly(order.getId());
    }

    private void complete(Refund target) {
        target.complete("cancellation-" + target.getId(), LocalDateTime.now());
    }

    private Order order(String orderNumber) {
        Order created = Order.builder().orderNumber(orderNumber).memberId(MEMBER_ID).totalAmount(23000L).build();
        entityManager.persist(created);
        return created;
    }

    private OrderItem orderItem(Order target, Long productId, int quantity, int unitPrice, SaleType saleType) {
        OrderItem item = OrderItem.builder().order(target).productId(productId).productName("상품" + productId)
                .quantity(quantity).listPrice(unitPrice).unitPrice(unitPrice).saleType(saleType).build();
        entityManager.persist(item);
        return item;
    }

    private Payment pay(Order target) {
        LocalDateTime paidAt = LocalDateTime.now();
        Payment created = Payment.builder().order(target).portonePaymentId("payment-" + target.getOrderNumber())
                .amount(target.getTotalAmount()).expiresAt(paidAt.plusMinutes(10)).build();
        created.complete(paidAt);
        target.markPaid(paidAt);
        entityManager.persist(created);
        return created;
    }

    private Refund refund(OrderItem orderItem, int quantity) {
        return refund(payment, orderItem, quantity);
    }

    // 요청 상태 환불 한 건을 상품 하나로 만든다. 환불 금액은 검증 대상이 아니라 첫 상품 금액만 넣는다
    private Refund refund(Payment target, OrderItem orderItem, int quantity) {
        Refund created = Refund.builder().payment(entityManager.find(Payment.class, target.getId()))
                .refundAmount((long) orderItem.getUnitPrice() * quantity)
                .reasonType(RefundReasonType.CHANGE_OF_MIND).build();
        entityManager.persist(created);
        addItem(created, orderItem, quantity);
        return created;
    }

    private void addItem(Refund target, OrderItem orderItem, int quantity) {
        entityManager.persist(RefundItem.builder().refund(target)
                .orderItem(entityManager.find(OrderItem.class, orderItem.getId())).quantity(quantity).build());
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
