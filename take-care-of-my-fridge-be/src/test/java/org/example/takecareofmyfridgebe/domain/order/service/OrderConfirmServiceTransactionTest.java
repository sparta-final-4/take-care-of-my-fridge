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
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
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
// 응답이 아니라 커밋된 주문 상태와 확정 시각을 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, OrderConfirmService.class, OrderReader.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderConfirmServiceTransactionTest extends MySqlContainerSupport {

    private final OrderConfirmService orderConfirmService;
    private final OrderRepository orderRepository;

    @Autowired
    OrderConfirmServiceTransactionTest(OrderConfirmService orderConfirmService, OrderRepository orderRepository) {
        this.orderConfirmService = orderConfirmService;
        this.orderRepository = orderRepository;
    }

    @AfterEach
    void cleanUp() {
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

    private Order reload(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow();
    }
}
