package org.example.takecareofmyfridgebe.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderConfirmResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentHistoryRecorder;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundCreateRequest;
import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.example.takecareofmyfridgebe.domain.refund.repository.RefundItemRepository;
import org.example.takecareofmyfridgebe.domain.refund.repository.RefundRepository;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundCommandService;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundQueryService;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 DB에 반영되는지 확인한다.
// 응답이 아니라 커밋된 주문 상태, 확정 시각, 주문 상품의 확정 수량을 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, OrderConfirmService.class, OrderReader.class,
    RefundQueryService.class, RefundCommandService.class, PaymentHistoryRecorder.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderConfirmServiceTransactionTest extends MySqlContainerSupport {

    private final OrderConfirmService orderConfirmService;
    private final RefundCommandService refundCommandService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;

    @Autowired
    OrderConfirmServiceTransactionTest(
        OrderConfirmService orderConfirmService,
        RefundCommandService refundCommandService,
        OrderRepository orderRepository,
        OrderItemRepository orderItemRepository,
        PaymentRepository paymentRepository,
        RefundRepository refundRepository,
        RefundItemRepository refundItemRepository
    ) {
        this.orderConfirmService = orderConfirmService;
        this.refundCommandService = refundCommandService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.refundItemRepository = refundItemRepository;
    }

    @AfterEach
    void cleanUp() {
        refundItemRepository.deleteAll();
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("결제 완료 주문을 구매 확정하면 상태가 구매 확정으로 바뀌고 확정 시각이 남는다")
    void confirm_paidOrder_confirms() {
        Order order = saveOrder(1L, OrderStatus.PAID);

        OrderConfirmResponse response = orderConfirmService.confirm(1L, order.getId());

        Order confirmed = reload(order);
        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("구매 확정하면 주문 수량에서 완료된 환불 수량만 빼서 확정 수량으로 남기고 실패한 환불은 빼지 않는다")
    void confirm_withCompletedAndFailedRefunds_savesQuantityMinusCompleted() {
        Order order = saveOrder(1L, OrderStatus.PAID);
        OrderItem normal = saveItem(order, 5, SaleType.NONE);
        OrderItem special = saveItem(order, 1, SaleType.SPECIAL);
        Payment payment = savePayment(order);
        saveRefund(payment, normal, 2, RefundStatus.COMPLETED);
        saveRefund(payment, normal, 1, RefundStatus.FAILED);

        orderConfirmService.confirm(1L, order.getId());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(reload(normal).getConfirmedQuantity()).isEqualTo(3);
        assertThat(reload(special).getConfirmedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("환불이 없는 주문을 구매 확정하면 주문 수량이 그대로 확정 수량이다")
    void confirm_withoutRefund_savesOrderQuantity() {
        Order order = saveOrder(1L, OrderStatus.PAID);
        OrderItem item = saveItem(order, 2, SaleType.NONE);

        orderConfirmService.confirm(1L, order.getId());

        assertThat(reload(item).getConfirmedQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("요청 상태 환불이 있는 주문은 환불이 진행 중이라고 거절하고 주문과 확정 수량은 그대로다")
    void confirm_withRequestedRefund_rejects() {
        Order order = saveOrder(1L, OrderStatus.PAID);
        OrderItem item = saveItem(order, 3, SaleType.NONE);
        saveRefund(savePayment(order), item, 1, RefundStatus.REQUESTED);

        assertThatThrownBy(() -> orderConfirmService.confirm(1L, order.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_REFUND_IN_PROGRESS);
        Order unchanged = reload(order);
        assertThat(unchanged.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(unchanged.getConfirmedAt()).isNull();
        assertThat(reload(item).getConfirmedQuantity()).isNull();
    }

    @Test
    @DisplayName("이미 구매 확정된 주문을 다시 확정하면 같은 응답을 주고 확정 시각은 그대로다")
    void confirm_confirmedOrder_returnsSameAndKeepsConfirmedAt() {
        Order order = saveOrder(1L, OrderStatus.PAID);
        orderConfirmService.confirm(1L, order.getId());
        LocalDateTime firstConfirmedAt = reload(order).getConfirmedAt();

        OrderConfirmResponse response = orderConfirmService.confirm(1L, order.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(reload(order).getConfirmedAt()).isEqualTo(firstConfirmedAt);
    }

    @Test
    @DisplayName("결제 대기 주문은 결제 완료된 주문만 확정할 수 있다고 거절하고 상태는 그대로다")
    void confirm_pendingOrder_rejects() {
        Order order = saveOrder(1L, OrderStatus.PENDING);

        assertThatThrownBy(() -> orderConfirmService.confirm(1L, order.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_PAID);
        Order unchanged = reload(order);
        assertThat(unchanged.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(unchanged.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("취소된 주문은 취소된 주문으로 거절하고 상태는 그대로다")
    void confirm_cancelledOrder_rejects() {
        Order order = saveOrder(1L, OrderStatus.CANCELLED);

        assertThatThrownBy(() -> orderConfirmService.confirm(1L, order.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_CANCELLED);
        Order unchanged = reload(order);
        assertThat(unchanged.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(unchanged.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("남의 주문은 없는 주문과 똑같이 404이고 상태는 그대로다")
    void confirm_othersOrder_returnsNotFound() {
        Order othersOrder = saveOrder(2L, OrderStatus.PAID);

        assertThatThrownBy(() -> orderConfirmService.confirm(1L, othersOrder.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThatThrownBy(() -> orderConfirmService.confirm(1L, othersOrder.getId() + 1000))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        Order unchanged = reload(othersOrder);
        assertThat(unchanged.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(unchanged.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("같은 주문을 동시에 10번 확정해도 모두 구매 확정 응답을 받고 주문은 구매 확정이다")
    void confirm_sameOrderConcurrently_allConfirmed() throws Exception {
        Order order = saveOrder(1L, OrderStatus.PAID);
        List<OrderStatus> responses = Collections.synchronizedList(new ArrayList<>());

        List<Throwable> failures = runConcurrently(Collections.nCopies(10, () -> {
            responses.add(orderConfirmService.confirm(1L, order.getId()).status());
            return null;
        }));

        Order confirmed = reload(order);
        assertThat(failures).isEmpty();
        assertThat(responses).hasSize(10).containsOnly(OrderStatus.CONFIRMED);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedAt()).isNotNull();
    }

    @Test
    @DisplayName("구매 확정과 환불 신청이 동시에 와도 주문마다 하나만 성공하고 확정된 주문에는 요청·완료 환불이 없다")
    void confirmAndRefundRequestConcurrently_onlyOneSucceedsPerOrder() throws Exception {
        List<Order> orders = new ArrayList<>();
        List<OrderItem> items = new ArrayList<>();
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Order order = saveOrder(1L, OrderStatus.PAID);
            OrderItem item = saveItem(order, 2, SaleType.NONE);
            savePayment(order);
            orders.add(order);
            items.add(item);
            tasks.add(() -> {
                orderConfirmService.confirm(1L, order.getId());
                return null;
            });
            tasks.add(() -> {
                refundCommandService.request(1L, new RefundCreateRequest(order.getId(),
                    List.of(new RefundCreateRequest.Item(item.getId(), 1)), RefundReasonType.CHANGE_OF_MIND, null));
                return null;
            });
        }

        List<Throwable> failures = runConcurrently(tasks);

        assertThat(failures).hasSize(10)
            .allSatisfy(failure -> assertThat(failure).isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isIn(ErrorCode.ORDER_REFUND_IN_PROGRESS, ErrorCode.REFUND_ORDER_CONFIRMED));
        for (int i = 0; i < orders.size(); i++) {
            Order order = reload(orders.get(i));
            List<RefundStatus> refundStatuses = refundRepository.findByOrderIdOrderByIdDesc(order.getId()).stream()
                .map(Refund::getStatus)
                .toList();
            Integer confirmedQuantity = reload(items.get(i)).getConfirmedQuantity();
            if (order.getStatus() == OrderStatus.CONFIRMED) {
                assertThat(refundStatuses).isEmpty();
                assertThat(confirmedQuantity).isEqualTo(2);
                continue;
            }
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(refundStatuses).containsExactly(RefundStatus.REQUESTED);
            assertThat(confirmedQuantity).isNull();
        }
    }

    private List<Throwable> runConcurrently(List<Callable<Void>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = tasks.stream()
                .map(task -> executor.submit(() -> {
                    start.await();
                    return task.call();
                }))
                .toList();
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        } finally {
            executor.shutdownNow();
        }
    }

    // 주문 상품 없이 주문만 저장한다. 결제 완료·취소 주문은 저장 전에 상태를 바꾼다.
    private Order saveOrder(Long memberId, OrderStatus status) {
        Order order = Order.builder()
            .orderNumber(UUID.randomUUID().toString().substring(0, 20))
            .memberId(memberId)
            .idempotencyKey(UUID.randomUUID().toString())
            .totalAmount(0L)
            .build();
        if (status == OrderStatus.PAID) {
            order.markPaid(LocalDateTime.now());
        }
        if (status == OrderStatus.CANCELLED) {
            order.cancel();
        }
        return orderRepository.save(order);
    }

    private OrderItem saveItem(Order order, int quantity, SaleType saleType) {
        return orderItemRepository.save(OrderItem.builder()
            .order(order)
            .productId(1L)
            .productName("대파")
            .quantity(quantity)
            .listPrice(1000)
            .unitPrice(1000)
            .saleType(saleType)
            .build());
    }

    // 환불 신청이 결제 완료 결제를 찾으므로 주문마다 결제 완료 결제를 하나 둔다
    private Payment savePayment(Order order) {
        LocalDateTime paidAt = LocalDateTime.now();
        Payment payment = Payment.builder()
            .order(order)
            .portonePaymentId("payment-" + order.getOrderNumber())
            .amount(order.getTotalAmount())
            .expiresAt(paidAt.plusMinutes(10))
            .build();
        payment.complete(paidAt);
        return paymentRepository.save(payment);
    }

    private void saveRefund(Payment payment, OrderItem item, int quantity, RefundStatus status) {
        Refund refund = Refund.builder()
            .payment(payment)
            .refundAmount((long) item.getUnitPrice() * quantity)
            .reasonType(RefundReasonType.CHANGE_OF_MIND)
            .build();
        if (status == RefundStatus.COMPLETED) {
            refund.complete("cancellation-" + UUID.randomUUID(), LocalDateTime.now());
        }
        if (status == RefundStatus.FAILED) {
            refund.fail("PG 거절");
        }
        refundRepository.save(refund);
        refundItemRepository.save(RefundItem.builder().refund(refund).orderItem(item).quantity(quantity).build());
    }

    private Order reload(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    private OrderItem reload(OrderItem item) {
        return orderItemRepository.findById(item.getId()).orElseThrow();
    }
}
