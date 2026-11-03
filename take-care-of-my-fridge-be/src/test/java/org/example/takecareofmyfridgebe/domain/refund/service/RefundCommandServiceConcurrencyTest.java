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
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PgProvider;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentHistoryRecorder;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundReasonType;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundResponse;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

// 같은 환불을 신청 요청·웹훅·스케줄러가 동시에 완료하려 할 때 한 번만 완료되는지 실제 MySQL에서 확인한다.
// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 커밋된 상태를 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, RefundCommandService.class, RefundQueryService.class,
        OrderReader.class, PaymentHistoryRecorder.class, OrderStatusService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefundCommandServiceConcurrencyTest extends MySqlContainerSupport {

    private static final int THREADS = 4;

    private final RefundCommandService refundCommandService;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    RefundCommandServiceConcurrencyTest(RefundCommandService refundCommandService, EntityManager entityManager,
                                        PlatformTransactionManager transactionManager) {
        this.refundCommandService = refundCommandService;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> List.of(
                "delete from PaymentHistory", "delete from RefundItem", "delete from Refund", "delete from Payment",
                "delete from OrderItem", "delete from Order", "delete from Product"
        ).forEach(jpql -> entityManager.createQuery(jpql).executeUpdate()));
    }

    @Test
    @DisplayName("같은 환불을 동시에 여러 번 완료해도 한 번만 완료돼 재고·결제 이력이 한 번만 남고 주문은 전액 환불이 된다")
    void completeConcurrently_completesOnce() throws Exception {
        Fixture fixture = saveRequestedRefund();

        List<Callable<RefundResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            PaymentChangedBy changedBy = i % 2 == 0 ? PaymentChangedBy.USER : PaymentChangedBy.WEBHOOK;
            tasks.add(() -> refundCommandService.complete(fixture.refundId(), "cancellation-1", changedBy));
        }
        List<RefundResponse> responses = runConcurrently(tasks);

        assertThat(responses).extracting(RefundResponse::status).containsOnly(RefundStatus.COMPLETED);
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(entityManager.find(Product.class, fixture.productId()).getStock()).isEqualTo(10);
            assertThat(entityManager.createQuery(
                            "select count(h) from PaymentHistory h where h.refundId = :refundId", Long.class)
                    .setParameter("refundId", fixture.refundId()).getSingleResult()).isEqualTo(1L);
            assertThat(entityManager.find(Order.class, fixture.orderId()).getStatus())
                    .isEqualTo(OrderStatus.REFUNDED);
        });
    }

    @Test
    @DisplayName("환불만 완료하는 처리와 주문 상태까지 묶은 완료가 겹쳐도 한 번만 완료된다")
    void completeAndCompleteWithoutOrderStatus_completesOnce() throws Exception {
        Fixture fixture = saveRequestedRefund();

        List<RefundResponse> responses = runConcurrently(List.of(
                () -> refundCommandService.complete(fixture.refundId(), "cancellation-1", PaymentChangedBy.USER),
                () -> refundCommandService.completeWithoutOrderStatus(fixture.refundId(), "cancellation-1",
                        PaymentChangedBy.WEBHOOK)));

        assertThat(responses).extracting(RefundResponse::status).containsOnly(RefundStatus.COMPLETED);
        transactionTemplate.executeWithoutResult(status -> {
            assertThat(entityManager.find(Product.class, fixture.productId()).getStock()).isEqualTo(10);
            assertThat(entityManager.createQuery(
                            "select count(h) from PaymentHistory h where h.refundId = :refundId", Long.class)
                    .setParameter("refundId", fixture.refundId()).getSingleResult()).isEqualTo(1L);
        });
    }

    // 재고 8개인 상품을 2개 산 결제 완료 주문과, 그 2개를 모두 환불하는 요청 상태 환불
    private Fixture saveRequestedRefund() {
        return transactionTemplate.execute(status -> {
            Product product = Product.builder().productType(ProductType.INGREDIENT).name("대파")
                    .imageUrl("https://img.test/대파.png").price(3000).stock(8)
                    .storage(StorageType.REFRIGERATED).shelfDays(7).status(ProductStatus.ON_SALE).build();
            entityManager.persist(product);
            Order order = Order.builder().orderNumber("20261103-000001").memberId(1L).totalAmount(6000L).build();
            entityManager.persist(order);
            OrderItem item = OrderItem.builder().order(order).productId(product.getId()).productName("대파")
                    .quantity(2).listPrice(3000).unitPrice(3000).saleType(SaleType.NONE).build();
            entityManager.persist(item);
            LocalDateTime paidAt = LocalDateTime.now();
            Payment payment = Payment.builder().order(order).portonePaymentId("payment-concurrency")
                    .amount(6000L).pgProvider(PgProvider.KAKAOPAY).expiresAt(paidAt.plusMinutes(10)).build();
            payment.complete(paidAt, PgProvider.KAKAOPAY);
            order.markPaid(paidAt);
            entityManager.persist(payment);
            Refund refund = Refund.builder().payment(payment).refundAmount(6000L)
                    .reasonType(RefundReasonType.CHANGE_OF_MIND).build();
            entityManager.persist(refund);
            entityManager.persist(RefundItem.builder().refund(refund).orderItem(item).quantity(2).build());
            return new Fixture(product.getId(), order.getId(), refund.getId());
        });
    }

    private <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = tasks.stream()
                    .map(task -> executor.submit(() -> {
                        start.await();
                        return task.call();
                    }))
                    .toList();
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

    private record Fixture(Long productId, Long orderId, Long refundId) {
    }
}
