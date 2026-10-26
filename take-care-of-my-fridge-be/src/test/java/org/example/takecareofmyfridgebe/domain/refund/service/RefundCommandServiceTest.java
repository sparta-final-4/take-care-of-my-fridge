package org.example.takecareofmyfridgebe.domain.refund.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.service.OrderReader;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, RefundCommandService.class, OrderReader.class})
class RefundCommandServiceTest extends MySqlContainerSupport {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;

    private final RefundCommandService refundCommandService;
    private final EntityManager entityManager;

    private Order order;
    private OrderItem itemA; // 단가 3,000원, 5개
    private OrderItem itemB; // 단가 2,000원, 3개
    private OrderItem specialItem;
    private OrderItem flashDropItem;

    @Autowired
    RefundCommandServiceTest(RefundCommandService refundCommandService, EntityManager entityManager) {
        this.refundCommandService = refundCommandService;
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
    void partialRefund() {
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
    void multipleItems() {
        Long refundId = refundCommandService.request(MEMBER_ID,
                request(order.getId(), item(itemA, 1), item(itemB, 3))).refundId();
        flushAndClear();

        assertThat(entityManager.find(Refund.class, refundId).getRefundAmount()).isEqualTo(9000L);
        assertThat(refundItems(refundId)).hasSize(2);
    }

    @Test
    @DisplayName("남은 수량까지는 다시 신청할 수 있고 넘으면 거절한다")
    void remainingQuantity() {
        refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 3)));
        refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 2)));

        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 1))),
                ErrorCode.EXCEED_REFUNDABLE_QUANTITY);
    }

    @Test
    @DisplayName("한 번에 주문 수량보다 많이 신청하면 거절한다")
    void exceedOrderQuantity() {
        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 6))),
                ErrorCode.EXCEED_REFUNDABLE_QUANTITY);
    }

    @Test
    @DisplayName("구매 확정된 주문은 환불할 수 없다")
    void confirmedOrder() {
        Order confirmed = entityManager.find(Order.class, order.getId());
        confirmed.confirm(LocalDateTime.now());
        flushAndClear();

        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(itemA, 1))),
                ErrorCode.REFUND_ORDER_CONFIRMED);
    }

    @Test
    @DisplayName("결제 전 주문은 환불할 수 없다")
    void pendingOrder() {
        Order pending = order("20261022-000002");
        OrderItem item = orderItem(pending, 10L, 1, 3000, SaleType.NONE);
        flushAndClear();

        assertError(() -> refundCommandService.request(MEMBER_ID, request(pending.getId(), item(item, 1))),
                ErrorCode.INVALID_REFUND_STATUS);
    }

    @Test
    @DisplayName("다른 회원의 주문은 없는 주문처럼 거절한다")
    void otherMembersOrder() {
        assertError(() -> refundCommandService.request(OTHER_MEMBER_ID, request(order.getId(), item(itemA, 1))),
                ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 주문의 상품은 환불할 수 없다")
    void otherOrdersItem() {
        Order other = order("20261022-000003");
        OrderItem otherItem = orderItem(other, 10L, 1, 3000, SaleType.NONE);
        flushAndClear();

        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(otherItem, 1))),
                ErrorCode.REFUND_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("특가·번개 드롭 상품은 환불할 수 없다")
    void saleItems() {
        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(specialItem, 1))),
                ErrorCode.NON_REFUNDABLE_SALE_ITEM);
        assertError(() -> refundCommandService.request(MEMBER_ID, request(order.getId(), item(flashDropItem, 1))),
                ErrorCode.NON_REFUNDABLE_SALE_ITEM);
    }

    @Test
    @DisplayName("같은 상품을 한 요청에 두 번 넣으면 거절한다")
    void duplicateItem() {
        assertError(() -> refundCommandService.request(MEMBER_ID,
                request(order.getId(), item(itemA, 1), item(itemA, 1))), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("환불을 완료하면 취소 ID와 환불 시각이 남고 환불 수량만큼 재고가 돌아온다")
    void complete_restoresStock() {
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

        RefundResponse response = refundCommandService.complete(refundId, "cancellation-1");
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
    @DisplayName("이미 완료된 환불을 다시 완료하면 거절하고 재고는 한 번만 돌아온다")
    void complete_twice_rejects() {
        Product greenOnion = product("대파", 8, ProductStatus.ON_SALE);
        Order paid = order("20261022-000005");
        OrderItem greenOnionItem = orderItem(paid, greenOnion.getId(), 3, 3000, SaleType.NONE);
        pay(paid);
        flushAndClear();
        Long refundId = refundCommandService.request(MEMBER_ID, request(paid.getId(), item(greenOnionItem, 2)))
                .refundId();
        refundCommandService.complete(refundId, "cancellation-1");
        flushAndClear();

        assertError(() -> refundCommandService.complete(refundId, "cancellation-2"), ErrorCode.INVALID_REFUND_STATE);
        flushAndClear();
        assertThat(entityManager.find(Product.class, greenOnion.getId()).getStock()).isEqualTo(10);
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
                .amount(target.getTotalAmount()).expiresAt(paidAt.plusMinutes(10)).build();
        payment.complete(paidAt);
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
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
