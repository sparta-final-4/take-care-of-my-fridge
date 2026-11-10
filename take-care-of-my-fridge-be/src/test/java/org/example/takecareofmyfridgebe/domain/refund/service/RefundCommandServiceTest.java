package org.example.takecareofmyfridgebe.domain.refund.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.service.OrderReader;
import org.example.takecareofmyfridgebe.domain.order.service.OrderStatusService;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentHistory;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentHistoryRecorder;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundCreateRequest;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundRequested;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundResponse;
import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.example.takecareofmyfridgebe.global.config.jpa.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.jpa.QueryDslConfig;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, RefundCommandService.class, RefundQueryService.class,
        OrderReader.class, PaymentHistoryRecorder.class, OrderStatusService.class})
class RefundCommandServiceTest extends MySqlContainerSupport {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    private final RefundCommandService refundCommandService;
    private final RefundQueryService refundQueryService;
    private final EntityManager entityManager;

    private Order order;
    private OrderItem itemA; // 단가 3,000원, 5개
    private OrderItem itemB; // 단가 2,000원, 3개
    private OrderItem specialItem;
    private OrderItem flashDropItem;

    @Autowired
    RefundCommandServiceTest(RefundCommandService refundCommandService, RefundQueryService refundQueryService,
                             EntityManager entityManager) {
        this.refundCommandService = refundCommandService;
        this.refundQueryService = refundQueryService;
        this.entityManager = entityManager;
    }

    @BeforeEach
    void setUp() {
        order = order("20261022-000001");
        itemA = orderItem(order, 10L, 5, 3000, SaleType.NONE);
        itemB = orderItem(order, 20L, 3, 2000, SaleType.NONE);
        specialItem = orderItem(order, 30L, 1, 1000, SaleType.SPECIAL);
        flashDropItem = orderItem(order, 40L, 1, 1000, SaleType.FLASH_DROP);
        pay(order);
        flushAndClear();
    }

    @Test
    @DisplayName("일부 수량을 환불 신청하면 단가 × 수량 금액으로 요청 상태 환불이 저장된다")
    void request_saveRequestedRefund_whenPartialQuantity() {
        RefundRequested requested = refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 2)));
        Long refundId = requested.refundId();
        flushAndClear();

        assertThat(requested.portonePaymentId()).isEqualTo("payment-" + order.getOrderNumber());
        assertThat(requested.refundAmount()).isEqualTo(6000L);
        assertThat(requested.reason()).isEqualTo("단순 변심");
        Refund refund = entityManager.find(Refund.class, refundId);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.getRefundAmount()).isEqualTo(6000L);
        assertThat(refund.getReasonType()).isEqualTo(RefundReasonType.CHANGE_OF_MIND);
        assertThat(refundItems(refundId))
                .extracting(ri -> ri.getOrderItem().getId(), RefundItem::getQuantity, RefundItem::getAmount)
                .containsExactly(tuple(itemA.getId(), 2, 6000L));
    }

    @Test
    @DisplayName("여러 상품을 한 번에 환불하면 금액을 합산한다")
    void request_sumAmount_whenMultipleItems() {
        Long refundId = refundCommandService.request(MEMBER_ID,
                request(order.getId(), item(itemA, 1), item(itemB, 3))).refundId();
        flushAndClear();

        assertThat(entityManager.find(Refund.class, refundId).getRefundAmount()).isEqualTo(9000L);
        assertThat(refundItems(refundId)).hasSize(2);
    }

    @Test
    @DisplayName("남은 수량까지는 다시 신청할 수 있고 넘으면 거절한다")
    void request_throwException_whenExceedRemainingQuantity() {
        Long first = refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 3))).refundId();
        refundCommandService.complete(first, "cancellation-1", PaymentChangedBy.USER);
        Long second = refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 2))).refundId();
        refundCommandService.complete(second, "cancellation-2", PaymentChangedBy.USER);

        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 1))),
                ErrorCode.EXCEED_REFUNDABLE_QUANTITY);
    }

    @Test
    @DisplayName("한 번에 주문 수량보다 많이 신청하면 거절한다")
    void request_throwException_whenExceedOrderQuantity() {
        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 6))),
                ErrorCode.EXCEED_REFUNDABLE_QUANTITY);
    }

    @Test
    @DisplayName("구매 확정된 주문은 환불할 수 없다")
    void request_throwException_whenOrderConfirmed() {
        Order confirmed = entityManager.find(Order.class, order.getId());
        confirmed.confirm(LocalDateTime.now());
        flushAndClear();

        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 1))),
                ErrorCode.REFUND_ORDER_CONFIRMED);
    }

    @Test
    @DisplayName("결제 전 주문은 환불할 수 없다")
    void request_throwException_whenOrderPending() {
        Order pending = order("20261022-000002");
        OrderItem item = orderItem(pending, 10L, 1, 3000, SaleType.NONE);
        flushAndClear();

        assertError(() -> refundCommandService.request(MEMBER_ID, request(pending.getId(), item(item, 1))),
                ErrorCode.INVALID_REFUND_STATUS);
    }

    @Test
    @DisplayName("다른 회원의 주문은 없는 주문처럼 거절한다")
    void request_throwException_whenOtherMembersOrder() {
        assertError(() -> refundCommandService.request(OTHER_MEMBER_ID, request(order.getId(), item(itemA, 1))),
                ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 주문의 상품은 환불할 수 없다")
    void request_throwException_whenOtherOrdersItem() {
        Order other = order("20261022-000003");
        OrderItem otherItem = orderItem(other, 10L, 1, 3000, SaleType.NONE);
        flushAndClear();

        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(otherItem, 1))),
                ErrorCode.REFUND_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("특가·번개 드롭 상품은 환불할 수 없다")
    void request_throwException_whenSaleItem() {
        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(specialItem, 1))),
                ErrorCode.NON_REFUNDABLE_SALE_ITEM);
        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(flashDropItem, 1))),
                ErrorCode.NON_REFUNDABLE_SALE_ITEM);
    }

    @Test
    @DisplayName("같은 상품을 한 요청에 두 번 넣으면 거절한다")
    void request_throwException_whenDuplicateItem() {
        assertError(() -> refundCommandService.request(MEMBER_ID,
                request(order.getId(), item(itemA, 1), item(itemA, 1))), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("환불을 완료하면 취소 ID와 환불 시각이 남고 환불 수량만큼 재고가 돌아온다")
    void complete_restoreStock() {
        Product greenOnion = product("대파", 8, ProductStatus.ON_SALE);
        Product tofu = product("두부", 0, ProductStatus.SOLD_OUT);
        Order paid = order("20261022-000004");
        OrderItem greenOnionItem = orderItem(paid, greenOnion.getId(), 3, 3000, SaleType.NONE);
        OrderItem tofuItem = orderItem(paid, tofu.getId(), 2, 2000, SaleType.NONE);
        pay(paid);
        flushAndClear();
        Long refundId = refundCommandService.request(MEMBER_ID,
                request(paid.getId(), item(greenOnionItem, 2), item(tofuItem, 1))).refundId();
        flushAndClear();

        RefundResponse response = refundCommandService.complete(refundId, "cancellation-1", PaymentChangedBy.USER);
        flushAndClear();

        assertThat(response.refundId()).isEqualTo(refundId);
        assertThat(response.status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(response.refundAmount()).isEqualTo(8000L);
        assertThat(response.refundedAt()).isNotNull();
        Refund refund = entityManager.find(Refund.class, refundId);
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.getPortoneCancellationId()).isEqualTo("cancellation-1");
        assertThat(refund.getRefundedAt()).isNotNull();
        assertThat(entityManager.find(Product.class, greenOnion.getId()).getStock()).isEqualTo(10);
        Product restoredTofu = entityManager.find(Product.class, tofu.getId());
        assertThat(restoredTofu.getStock()).isEqualTo(1);
        assertThat(restoredTofu.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("이미 완료된 환불을 다시 완료하면 바꾸지 않고 현재 상태를 돌려주며 재고는 한 번만 돌아온다")
    void complete_keepFirstResult_whenAlreadyCompleted() {
        Product greenOnion = product("대파", 8, ProductStatus.ON_SALE);
        Order paid = order("20261022-000005");
        OrderItem greenOnionItem = orderItem(paid, greenOnion.getId(), 3, 3000, SaleType.NONE);
        pay(paid);
        flushAndClear();
        Long refundId = refundCommandService.request(MEMBER_ID, request(paid.getId(), item(greenOnionItem, 2)))
                .refundId();
        refundCommandService.complete(refundId, "cancellation-1", PaymentChangedBy.USER);
        flushAndClear();

        RefundResponse second = refundCommandService.complete(refundId, "cancellation-2", PaymentChangedBy.WEBHOOK);
        flushAndClear();

        assertThat(second.status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(entityManager.find(Refund.class, refundId).getPortoneCancellationId()).isEqualTo("cancellation-1");
        assertThat(entityManager.find(Product.class, greenOnion.getId()).getStock()).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 주문에 진행 중인 환불이 있으면 새 신청을 거절한다")
    void request_throwException_whenRequestedRefundExists() {
        refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 1)));

        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemB, 1))),
                ErrorCode.DUPLICATE_REFUND_REQUEST);
    }

    @Test
    @DisplayName("실패한 환불의 수량은 다시 신청할 수 있다")
    void request_success_whenPreviousRefundFailed() {
        Long refundId = refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 5))).refundId();
        RefundResponse failed = refundCommandService.fail(refundId, "PG 취소 거절");
        flushAndClear();

        assertThat(failed.status()).isEqualTo(RefundStatus.FAILED);
        assertThat(entityManager.find(Refund.class, refundId).getFailReason()).isEqualTo("PG 취소 거절");
        Long retried = refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 5))).refundId();
        assertThat(retried).isNotEqualTo(refundId);
    }

    @Test
    @DisplayName("이미 완료된 환불을 실패로 바꾸려 하면 바꾸지 않는다")
    void fail_keepCompleted_whenAlreadyCompleted() {
        Long refundId = refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 1))).refundId();
        refundCommandService.complete(refundId, "cancellation-1", PaymentChangedBy.USER);
        flushAndClear();

        assertThat(refundCommandService.fail(refundId, "늦게 도착한 실패").status()).isEqualTo(RefundStatus.COMPLETED);
    }

    @Test
    @DisplayName("환불을 완료하면 결제 이력에 부분 취소를, 누적 취소가 결제 금액에 닿으면 전액 취소를 남긴다")
    void complete_recordPaymentHistory() {
        Long first = refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 5))).refundId();
        refundCommandService.complete(first, "cancellation-1", PaymentChangedBy.USER);
        Long second = refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemB, 3))).refundId();
        refundCommandService.complete(second, "cancellation-2", PaymentChangedBy.WEBHOOK);
        // 남은 2,000원(특가·번개 드롭)은 콘솔에서 직접 취소됐다
        refundCommandService.recordUnknownCancellation(paymentId(), 2000L, "console-1", PaymentChangedBy.WEBHOOK);
        flushAndClear();

        assertThat(histories())
                .extracting(PaymentHistory::getEventType, PaymentHistory::getAmount, PaymentHistory::getCancelledAmount,
                        PaymentHistory::getRefundId, PaymentHistory::getPgCancellationId, PaymentHistory::getChangedBy)
                .containsExactly(
                        tuple(PaymentEventType.PARTIAL_CANCELLED, 15000L, 15000L, first, "cancellation-1",
                                PaymentChangedBy.USER),
                        tuple(PaymentEventType.PARTIAL_CANCELLED, 6000L, 21000L, second, "cancellation-2",
                                PaymentChangedBy.WEBHOOK),
                        tuple(PaymentEventType.CANCELLED, 2000L, 23000L, null, "console-1", PaymentChangedBy.WEBHOOK));
    }

    @Test
    @DisplayName("일반 상품만 있는 주문은 부분 환불이면 결제 완료로 두고, 모든 상품이 환불되면 전액 환불로 바꾼다")
    void complete_markOrderRefunded_whenAllItemsRefunded() {
        Order normal = order("20261022-000006");
        OrderItem first = orderItem(normal, 10L, 2, 3000, SaleType.NONE);
        OrderItem second = orderItem(normal, 20L, 1, 2000, SaleType.NONE);
        pay(normal);
        flushAndClear();

        Long partial = refundCommandService.request(MEMBER_ID, request(normal.getId(), item(first, 2))).refundId();
        refundCommandService.complete(partial, "cancellation-1", PaymentChangedBy.USER);
        flushAndClear();
        assertThat(entityManager.find(Order.class, normal.getId()).getStatus()).isEqualTo(OrderStatus.PAID);

        Long rest = refundCommandService.request(MEMBER_ID, request(normal.getId(), item(second, 1))).refundId();
        refundCommandService.complete(rest, "cancellation-2", PaymentChangedBy.WEBHOOK);
        flushAndClear();

        assertThat(entityManager.find(Order.class, normal.getId()).getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    @DisplayName("일반 상품을 모두 환불하고 특가·번개 드롭 상품만 남으면 바로 구매 확정하고 확정 수량을 남긴다")
    void complete_confirmOrder_whenOnlySaleItemsLeft() {
        Long refundId = refundCommandService.request(MEMBER_ID,
                request(order.getId(), item(itemA, 5), item(itemB, 3))).refundId();
        refundCommandService.complete(refundId, "cancellation-1", PaymentChangedBy.USER);
        flushAndClear();

        Order confirmed = entityManager.find(Order.class, order.getId());
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
        assertThat(List.of(itemA, itemB, specialItem, flashDropItem))
                .extracting(item -> entityManager.find(OrderItem.class, item.getId()).getConfirmedQuantity())
                .containsExactly(0, 0, 1, 1);
    }

    @Test
    @DisplayName("일반 상품이 남아 있으면 특가·번개 드롭이 있는 주문도 결제 완료로 둔다")
    void complete_keepOrderPaid_whenNormalItemsLeft() {
        Long refundId = refundCommandService.request(MEMBER_ID,
                request(order.getId(), item(itemA, 5), item(itemB, 2))).refundId();
        refundCommandService.complete(refundId, "cancellation-1", PaymentChangedBy.USER);
        flushAndClear();

        assertThat(entityManager.find(Order.class, order.getId()).getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("환불 완료 때 주문이 결제 완료가 아니면 주문 쪽 예외로 환불 완료를 되돌린다")
    void complete_throwException_whenOrderNotPaid() {
        Order normal = order("20261022-000007");
        OrderItem only = orderItem(normal, 10L, 1, 3000, SaleType.NONE);
        pay(normal);
        flushAndClear();
        Long refundId = refundCommandService.request(MEMBER_ID, request(normal.getId(), item(only, 1))).refundId();
        // 정상 흐름에서는 진행 중 환불이 있으면 구매 확정이 막히지만, 상태가 어긋난 경우를 만든다
        entityManager.find(Order.class, normal.getId()).confirm(LocalDateTime.now());
        flushAndClear();

        assertError(() -> refundCommandService.complete(refundId, "cancellation-1", PaymentChangedBy.USER),
                ErrorCode.INVALID_ORDER_STATUS);
    }

    @Test
    @DisplayName("환불만 완료하면 재고·결제 이력은 남기고 주문은 결제 완료로 두며, 정리 스케줄러 판단 대상이 된다")
    void completeWithoutOrderStatus_keepOrderPaid() {
        Product greenOnion = product("대파", 8, ProductStatus.ON_SALE);
        Order normal = order("20261022-000008");
        OrderItem only = orderItem(normal, greenOnion.getId(), 2, 3000, SaleType.NONE);
        pay(normal);
        Order partial = order("20261022-000009");
        OrderItem partialItem = orderItem(partial, 10L, 2, 3000, SaleType.NONE);
        pay(partial);
        flushAndClear();
        Long refundId = refundCommandService.request(MEMBER_ID, request(normal.getId(), item(only, 2))).refundId();
        Long partialRefundId = refundCommandService.request(MEMBER_ID, request(partial.getId(), item(partialItem, 1)))
                .refundId();
        flushAndClear();

        RefundResponse response = refundCommandService.completeWithoutOrderStatus(refundId, "cancellation-1",
                PaymentChangedBy.USER);
        refundCommandService.completeWithoutOrderStatus(partialRefundId, "cancellation-2", PaymentChangedBy.USER);
        flushAndClear();

        assertThat(response.status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(entityManager.find(Product.class, greenOnion.getId()).getStock()).isEqualTo(10);
        assertThat(entityManager.createQuery(
                        "select count(h) from PaymentHistory h where h.refundId = :refundId", Long.class)
                .setParameter("refundId", refundId).getSingleResult()).isEqualTo(1L);
        assertThat(entityManager.find(Order.class, normal.getId()).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(refundQueryService.findPaidOrderIdsFullyRefunded())
                .contains(normal.getId())
                .doesNotContain(partial.getId());
        assertThat(refundQueryService.findOrderStatusAfterRefund(normal.getId())).contains(OrderStatus.REFUNDED);
        assertThat(refundQueryService.findOrderStatusAfterRefund(partial.getId())).isEmpty();
    }

    private Long paymentId() {
        return entityManager.createQuery("select p.id from Payment p where p.order.id = :orderId", Long.class)
                .setParameter("orderId", order.getId())
                .getSingleResult();
    }

    private List<PaymentHistory> histories() {
        return entityManager.createQuery(
                        "select h from PaymentHistory h where h.payment.id = :paymentId order by h.id", PaymentHistory.class)
                .setParameter("paymentId", paymentId())
                .getResultList();
    }

    private Product product(String name, int stock, ProductStatus status) {
        Product product = Product.builder().productType(ProductType.INGREDIENT).name(name)
                .imageUrl("https://img.test/" + name + ".png").price(3000).stock(stock)
                .storage(StorageType.REFRIGERATED).shelfDays(7).status(status).build();
        entityManager.persist(product);
        return product;
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

    private void pay(Order target) {
        LocalDateTime paidAt = LocalDateTime.now();
        Payment payment = Payment.builder().order(target).portonePaymentId("payment-" + target.getOrderNumber())
                .amount(target.getTotalAmount()).pgProvider(PgProvider.KAKAOPAY)
                .expiresAt(paidAt.plusMinutes(10)).build();
        payment.complete(paidAt, PgProvider.KAKAOPAY);
        target.markPaid(paidAt);
        entityManager.persist(payment);
    }

    private RefundCreateRequest request(Long orderId, RefundCreateRequest.Item... items) {
        return new RefundCreateRequest(orderId, List.of(items), RefundReasonType.CHANGE_OF_MIND, null);
    }

    private RefundCreateRequest.Item item(OrderItem orderItem, int quantity) {
        return new RefundCreateRequest.Item(orderItem.getId(), quantity);
    }

    private List<RefundItem> refundItems(Long refundId) {
        return entityManager.createQuery(
                        "select ri from RefundItem ri join fetch ri.orderItem where ri.refund.id = :refundId order by ri.id",
                        RefundItem.class)
                .setParameter("refundId", refundId)
                .getResultList();
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
