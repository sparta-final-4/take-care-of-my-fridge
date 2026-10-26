package org.example.takecareofmyfridgebe.domain.payment.service;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentApproveResult;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentApproveResult.Rejection;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentHistoryRepository;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// 한 주문에 결제가 여러 건일 때 주문 잠금으로 같은 주문의 승인·생성이 하나씩 처리되는지 실제 MySQL에서 확인한다.
// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 커밋된 상태를 다시 읽어 확인한다.
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, PaymentCommandService.class, PaymentHistoryRecorder.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentCommandServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    private final PaymentCommandService paymentCommandService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    PaymentCommandServiceConcurrencyTest(
            PaymentCommandService paymentCommandService,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentHistoryRepository paymentHistoryRepository,
            PlatformTransactionManager transactionManager) {
        this.paymentCommandService = paymentCommandService;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        paymentHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 주문의 결제 두 건을 동시에 승인하면 하나만 승인되고 다른 하나는 ORDER_NOT_PAYABLE로 실패한다")
    void approveTwoPaymentsOfSameOrder_onlyOneApproved() throws Exception {
        Order order = saveOrder();
        Payment first = savePayment(order, "payment-a");
        Payment second = savePayment(order, "payment-b");

        List<PaymentApproveResult> results = runConcurrently(List.of(
                () -> paymentCommandService.approve(first, PaymentChangedBy.USER),
                () -> paymentCommandService.approve(second, PaymentChangedBy.USER)));

        assertThat(results).filteredOn(PaymentApproveResult::isApproved).hasSize(1);
        assertThat(results).filteredOn(result -> !result.isApproved())
                .extracting(PaymentApproveResult::rejection)
                .containsExactly(Rejection.ORDER_NOT_PAYABLE);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentRepository.findAll())
                .extracting(Payment::getStatus, Payment::getFailReason)
                .containsExactlyInAnyOrder(
                        tuple(PaymentStatus.PAID, null),
                        tuple(PaymentStatus.FAILED, FailReason.ORDER_NOT_PAYABLE));
    }

    @Test
    @DisplayName("결제 생성을 동시에 여러 번 요청하면 한 건만 만들어지고 나머지는 PAYMENT_IN_PROGRESS로 거절된다")
    void createConcurrently_onlyOneReadyPayment() throws Exception {
        Order order = saveOrder();

        List<Object> results = runConcurrently(IntStream.range(0, 5)
                .<Callable<Object>>mapToObj(i -> () -> {
                    try {
                        return paymentCommandService.create(order.getId());
                    } catch (BusinessException e) {
                        return e.getErrorCode();
                    }
                })
                .toList());

        assertThat(results).filteredOn(Payment.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(ErrorCode.class::isInstance)
                .containsOnly(ErrorCode.PAYMENT_IN_PROGRESS)
                .hasSize(4);
        assertThat(paymentRepository.findByOrderIdAndStatus(order.getId(), PaymentStatus.READY)).hasSize(1);
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("결제를 읽은 뒤 주문이 취소됐으면 승인하지 않고 ORDER_NOT_PAYABLE로 실패 처리한다")
    void approveAfterOrderCancelled_rejected() {
        Order order = saveOrder();
        Payment payment = savePayment(order, "payment-a");
        transactionTemplate.executeWithoutResult(status ->
                orderRepository.findById(order.getId()).orElseThrow().cancel());

        PaymentApproveResult result = paymentCommandService.approve(payment, PaymentChangedBy.USER);

        assertThat(result.rejection()).isEqualTo(Rejection.ORDER_NOT_PAYABLE);
        Payment saved = paymentRepository.findById(payment.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getFailReason()).isEqualTo(FailReason.ORDER_NOT_PAYABLE);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("결제를 실패 처리해도 주문은 PENDING으로 남아 같은 주문에 새 결제를 만들 수 있다")
    void failThenCreateAgain_newPaymentForSameOrder() {
        Order order = saveOrder();
        Payment first = paymentCommandService.create(order.getId());

        paymentCommandService.fail(first, FailReason.USER_CANCELLED, PaymentChangedBy.USER);
        Payment second = paymentCommandService.create(order.getId());

        assertThat(second.getPortonePaymentId()).isNotEqualTo(first.getPortonePaymentId());
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(paymentRepository.findAll())
                .extracting(Payment::getStatus)
                .containsExactlyInAnyOrder(PaymentStatus.FAILED, PaymentStatus.READY);
    }

    private Order saveOrder() {
        return orderRepository.save(Order.builder()
                .orderNumber("20261021000000000001")
                .memberId(1L)
                .totalAmount(10000L)
                .build());
    }

    private Payment savePayment(Order order, String portonePaymentId) {
        return paymentRepository.save(Payment.builder()
                .order(order)
                .portonePaymentId(portonePaymentId)
                .amount(10000L)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build());
    }

    // 모든 작업이 준비된 뒤 한꺼번에 시작해 같은 주문에 동시에 들어오게 한다
    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }
}
