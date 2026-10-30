package org.example.takecareofmyfridgebe.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

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
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateItemRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateRequest;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.order.scheduler.OrderExpireScheduler;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentHistoryRepository;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentCommandService;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentHistoryRecorder;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventItem;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventStockService;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 DB에 반영되는지 확인한다.
// 응답이 아니라 커밋된 주문 상태와 재고를 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, OrderCancelService.class, OrderCreateService.class,
    OrderReader.class, SaleEventReader.class,
    SaleEventStockService.class, PaymentService.class, PaymentCommandService.class, PaymentHistoryRecorder.class,
    OrderExpireScheduler.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderCancelServiceTransactionTest extends MySqlContainerSupport {

    // 이 테스트는 재고와 롤백을 본다. 번호 생성은 OrderNumberGeneratorTest가 맡으므로 매번 다른 번호를 돌려주는 가짜로 바꾼다.
    private static final AtomicLong ORDER_NUMBER_SEQUENCE = new AtomicLong();

    @MockitoBean
    private OrderNumberGenerator orderNumberGenerator;

    private final OrderCancelService orderCancelService;
    private final OrderCreateService orderCreateService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final OrderExpireScheduler orderExpireScheduler;
    private final PaymentCommandService paymentCommandService;
    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    OrderCancelServiceTransactionTest(
            OrderCancelService orderCancelService,
            OrderCreateService orderCreateService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            OrderExpireScheduler orderExpireScheduler,
            PaymentCommandService paymentCommandService,
            PaymentService paymentService,
            PaymentRepository paymentRepository,
            PaymentHistoryRepository paymentHistoryRepository,
            DataSource dataSource) {
        this.orderCancelService = orderCancelService;
        this.orderCreateService = orderCreateService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.saleEventRepository = saleEventRepository;
        this.saleEventItemRepository = saleEventItemRepository;
        this.orderExpireScheduler = orderExpireScheduler;
        this.paymentCommandService = paymentCommandService;
        this.paymentService = paymentService;
        this.paymentRepository = paymentRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void stubOrderNumberGenerator() {
        given(orderNumberGenerator.next()).willAnswer(invocation -> String.format("%020d", ORDER_NUMBER_SEQUENCE.incrementAndGet()));
    }

    @AfterEach
    void cleanUp() {
        paymentHistoryRepository.deleteAll();
        paymentRepository.deleteAll();
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        saleEventItemRepository.deleteAll();
        saleEventRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("결제를 시도하지 않은 결제 대기 주문을 취소하면 주문과 주문 상품이 지워지고 재고는 주문 수량만큼 돌아온다")
    void cancel_pendingOrderWithoutPayment_deletesAndRestoresStock() {
        Product greenOnion = saveProduct("대파", 3000, 8);
        Product tofu = saveProduct("두부", 2000, 4);
        Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2), item(tofu, 1));

        orderCancelService.cancel(1L, order.getId());

        assertDeleted(order);
        assertThat(stockOf(greenOnion)).isEqualTo(10);
        assertThat(stockOf(tofu)).isEqualTo(5);
    }

    @Test
    @DisplayName("품절된 상품의 주문을 취소하면 재고가 돌아오고 판매 중으로 바뀐다")
    void cancel_soldOutProduct_marksOnSale() {
        Product greenOnion = saveProduct("대파", 3000, 0, ProductStatus.SOLD_OUT);
        Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2));

        orderCancelService.cancel(1L, order.getId());

        Product restored = productRepository.findById(greenOnion.getId()).orElseThrow();
        assertThat(restored.getStock()).isEqualTo(2);
        assertThat(restored.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("번개 드롭으로 산 주문을 취소하면 1인 1개 기록이 담긴 주문 상품이 지워지고 번개 드롭 남은 수량이 돌아온다")
    void cancel_flashDrop_deletesItemAndRestoresRemaining() {
        Product greenOnion = saveProduct("대파", 3000, 0);
        SaleEventItem saleEventItem = saveFlashDrop(greenOnion, 5);
        Order order = saveOrder(1L, OrderStatus.PENDING, flashDropItem(greenOnion, 1L).saleEventItemId(saleEventItem.getId()));

        orderCancelService.cancel(1L, order.getId());

        assertDeleted(order);
        assertThat(orderItemRepository.existsBySaleEventItemIdAndFlashDropMemberId(saleEventItem.getId(), 1L)).isFalse();
        assertThat(stockOf(greenOnion)).isEqualTo(1);
        assertThat(saleEventItemRepository.findById(saleEventItem.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("지운 주문을 다시 취소하면 404이고 재고는 한 번만 돌아온다")
    void cancel_deletedOrder_returnsNotFound() {
        Product greenOnion = saveProduct("대파", 3000, 8);
        Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2));
        orderCancelService.cancel(1L, order.getId());

        assertThatThrownBy(() -> orderCancelService.cancel(1L, order.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThat(stockOf(greenOnion)).isEqualTo(10);
    }

    @Test
    @DisplayName("결제 시도가 있는 결제 대기 주문은 실패한 결제여도 거절하고 주문과 재고는 그대로다")
    void cancel_pendingOrderWithPayment_rejects() {
        Product greenOnion = saveProduct("대파", 3000, 8);
        Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2));
        saveFailedPayment(order);

        assertThatThrownBy(() -> orderCancelService.cancel(1L, order.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_PAYMENT_ATTEMPTED);
        assertThat(statusOf(order)).isEqualTo(OrderStatus.PENDING);
        assertThat(orderItemRepository.findByOrderIdOrderByIdAsc(order.getId())).hasSize(1);
        assertThat(stockOf(greenOnion)).isEqualTo(8);
    }

    @Test
    @DisplayName("결제가 끝난 주문은 이미 결제가 완료된 주문으로 거절하고 상태와 재고는 그대로다")
    void cancel_paidOrder_rejects() {
        Product greenOnion = saveProduct("대파", 3000, 8);
        Order order = saveOrder(1L, OrderStatus.PAID, item(greenOnion, 2));

        assertThatThrownBy(() -> orderCancelService.cancel(1L, order.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ALREADY_PAID);
        assertThat(statusOf(order)).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(greenOnion)).isEqualTo(8);
    }

    @Test
    @DisplayName("남의 주문은 없는 주문과 똑같이 404이고 상태와 재고는 그대로다")
    void cancel_othersOrder_returnsNotFound() {
        Product greenOnion = saveProduct("대파", 3000, 8);
        Order othersOrder = saveOrder(2L, OrderStatus.PENDING, item(greenOnion, 2));

        assertThatThrownBy(() -> orderCancelService.cancel(1L, othersOrder.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThatThrownBy(() -> orderCancelService.cancel(1L, othersOrder.getId() + 1000))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThat(statusOf(othersOrder)).isEqualTo(OrderStatus.PENDING);
        assertThat(stockOf(greenOnion)).isEqualTo(8);
    }

    @Test
    @DisplayName("같은 주문을 동시에 5번 취소하면 하나만 지우고 나머지는 404이며 재고는 한 번만 돌아온다")
    void cancel_sameOrderConcurrently_restoresStockOnce() throws Exception {
        Product greenOnion = saveProduct("대파", 3000, 8);
        Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2));

        List<Throwable> failures = runConcurrently(Collections.nCopies(5, () -> {
            orderCancelService.cancel(1L, order.getId());
            return null;
        }));

        assertThat(failures).hasSize(4).allSatisfy(failure -> assertThat(failure)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND));
        assertDeleted(order);
        assertThat(stockOf(greenOnion)).isEqualTo(10);
    }

    @Test
    @DisplayName("취소하기와 결제하기가 동시에 오면 주문이 지워지고 결제가 없거나, 결제가 붙고 주문과 재고가 그대로다")
    void cancel_withConcurrentPaymentCreate_neverDeletesPaidAttempt() throws Exception {
        for (int round = 0; round < 10; round++) {
            Product greenOnion = saveProduct("대파" + round, 3000, 8);
            Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2));

            List<Throwable> failures = runConcurrently(List.of(
                () -> {
                    orderCancelService.cancel(1L, order.getId());
                    return null;
                },
                () -> {
                    paymentCommandService.create(order.getId());
                    return null;
                }));

            // 먼저 잠근 쪽만 성공한다. 취소가 이기면 결제하기는 주문을 찾지 못하고, 결제하기가 이기면 취소는 거절된다
            assertThat(failures).hasSize(1);
            if (orderRepository.existsById(order.getId())) {
                assertThat(failures.get(0)).extracting("errorCode").isEqualTo(ErrorCode.ORDER_PAYMENT_ATTEMPTED);
                assertThat(paymentRepository.existsByOrderId(order.getId())).isTrue();
                assertThat(orderItemRepository.findByOrderIdOrderByIdAsc(order.getId())).hasSize(1);
                assertThat(stockOf(greenOnion)).isEqualTo(8);
                continue;
            }
            assertThat(failures.get(0)).extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
            assertThat(paymentRepository.existsByOrderId(order.getId())).isFalse();
            assertDeleted(order);
            assertThat(stockOf(greenOnion)).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("취소하기와 주문 만료가 동시에 와도 주문은 한 번만 지워지고 재고는 한 번만 돌아온다")
    void cancel_withConcurrentExpire_restoresStockOnce() throws Exception {
        for (int round = 0; round < 10; round++) {
            Product greenOnion = saveProduct("대파" + round, 3000, 8);
            Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2));
            makeOld(order);

            List<Throwable> failures = runConcurrently(List.of(
                () -> {
                    orderCancelService.cancel(1L, order.getId());
                    return null;
                },
                () -> {
                    orderCancelService.expire(order.getId());
                    return null;
                }));

            // 만료가 먼저 지우면 취소는 404를 받고, 취소가 먼저 지우면 만료는 조용히 건너뛴다
            assertThat(failures).allSatisfy(failure -> assertThat(failure)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND));
            assertDeleted(order);
            assertThat(stockOf(greenOnion)).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("주문 만료는 생성 5분이 지나고 결제 시도가 없는 결제 대기 주문만 지운다")
    void expireScheduler_deletesOnlyOldPendingOrdersWithoutPayment() {
        Product greenOnion = saveProduct("대파", 3000, 0);
        Order abandoned = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 1));
        Order fresh = saveOrder(2L, OrderStatus.PENDING, item(greenOnion, 1));
        Order paymentAttempted = saveOrder(3L, OrderStatus.PENDING, item(greenOnion, 1));
        Order paid = saveOrder(4L, OrderStatus.PAID, item(greenOnion, 1));
        Stream.of(abandoned, paymentAttempted, paid).forEach(this::makeOld);
        saveFailedPayment(paymentAttempted);

        orderExpireScheduler.expireAbandonedOrders();

        assertDeleted(abandoned);
        assertThat(statusOf(fresh)).isEqualTo(OrderStatus.PENDING);
        assertThat(statusOf(paymentAttempted)).isEqualTo(OrderStatus.PENDING);
        assertThat(statusOf(paid)).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(greenOnion)).isEqualTo(1);
    }

    @Test
    @DisplayName("결제가 모두 끝났고 마지막 결제가 10분 전보다 이른 결제 대기 주문만 골라 취소 상태로 남기고 재고를 되돌린다")
    void cancelAbandoned_cancelsOnlyAbandonedOrders() {
        Product greenOnion = saveProduct("대파", 3000, 0);
        SaleEventItem saleEventItem = saveFlashDrop(greenOnion, 5);
        Order abandoned = saveOrder(1L, OrderStatus.PENDING,
            item(greenOnion, 2), flashDropItem(greenOnion, 1L).saleEventItemId(saleEventItem.getId()));
        makeOld(saveFailedPayment(abandoned));
        Order inProgress = saveOrder(2L, OrderStatus.PENDING, item(greenOnion, 1));
        makeOld(saveReadyPayment(inProgress));
        Order retried = saveOrder(3L, OrderStatus.PENDING, item(greenOnion, 1));
        makeOld(saveFailedPayment(retried));
        saveFailedPayment(retried);
        Order withoutPayment = saveOrder(4L, OrderStatus.PENDING, item(greenOnion, 1));
        makeOld(withoutPayment);
        LocalDateTime lastPaymentBefore = LocalDateTime.now().minusMinutes(10);

        List<Long> orderIds = paymentService.findAbandonedOrderIds(lastPaymentBefore, 0, 100);
        orderIds.forEach(orderId -> orderCancelService.cancelAbandoned(orderId, lastPaymentBefore));
        Stream.of(inProgress, retried, withoutPayment)
            .forEach(order -> orderCancelService.cancelAbandoned(order.getId(), lastPaymentBefore));

        assertThat(orderIds).containsExactly(abandoned.getId());
        assertThat(statusOf(abandoned)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(orderItemRepository.findByOrderIdOrderByIdAsc(abandoned.getId()))
            .hasSize(2)
            .extracting(OrderItem::getFlashDropMemberId)
            .containsOnlyNulls();
        assertThat(paymentRepository.existsByOrderId(abandoned.getId())).isTrue();
        assertThat(saleEventItemRepository.findById(saleEventItem.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(5);
        // 건너뛴 세 주문은 결제 대기 그대로이고, 취소한 주문의 3개만 돌아온다
        assertThat(Stream.of(inProgress, retried, withoutPayment).map(this::statusOf)).containsOnly(OrderStatus.PENDING);
        assertThat(stockOf(greenOnion)).isEqualTo(3);
    }

    @Test
    @DisplayName("결제 정리와 결제하기가 동시에 오면 주문이 취소되고 새 결제가 없거나, 새 결제가 붙고 주문과 재고가 그대로다")
    void cancelAbandoned_withConcurrentPaymentCreate_keepsStockConsistent() throws Exception {
        for (int round = 0; round < 10; round++) {
            Product greenOnion = saveProduct("대파" + round, 3000, 8);
            Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2));
            makeOld(saveFailedPayment(order));
            LocalDateTime lastPaymentBefore = LocalDateTime.now().minusMinutes(10);

            List<Throwable> failures = runConcurrently(List.of(
                () -> {
                    orderCancelService.cancelAbandoned(order.getId(), lastPaymentBefore);
                    return null;
                },
                () -> {
                    paymentCommandService.create(order.getId());
                    return null;
                }));

            // 정리가 먼저 잠그면 결제하기는 결제할 수 없는 주문으로 거절되고, 결제하기가 먼저면 정리는 건너뛴다
            boolean hasReady = paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.READY);
            if (hasReady) {
                assertThat(failures).isEmpty();
                assertThat(statusOf(order)).isEqualTo(OrderStatus.PENDING);
                assertThat(stockOf(greenOnion)).isEqualTo(8);
                continue;
            }
            assertThat(failures).singleElement()
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
            assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLED);
            assertThat(stockOf(greenOnion)).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("결제 정리와 주문 취소가 동시에 와도 주문은 취소 상태로 남고 재고는 한 번만 돌아온다")
    void cancelAbandoned_withConcurrentCancel_restoresStockOnce() throws Exception {
        for (int round = 0; round < 10; round++) {
            Product greenOnion = saveProduct("대파" + round, 3000, 8);
            Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2));
            makeOld(saveFailedPayment(order));
            LocalDateTime lastPaymentBefore = LocalDateTime.now().minusMinutes(10);

            List<Throwable> failures = runConcurrently(List.of(
                () -> {
                    orderCancelService.cancelAbandoned(order.getId(), lastPaymentBefore);
                    return null;
                },
                () -> {
                    orderCancelService.cancel(1L, order.getId());
                    return null;
                }));

            // 주문 취소는 정리 전이면 결제 시도로, 정리 뒤면 취소된 주문으로 거절된다
            assertThat(failures).singleElement()
                .extracting("errorCode").isIn(ErrorCode.ORDER_PAYMENT_ATTEMPTED, ErrorCode.ORDER_CANCELLED);
            assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLED);
            assertThat(stockOf(greenOnion)).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("취소와 다른 회원들의 주문하기가 같은 상품에 동시에 와도 재고가 어긋나지 않는다")
    void cancel_withConcurrentOrders_keepsStockConsistent() throws Exception {
        int members = 5;
        Product greenOnion = saveProduct("대파", 3000, 10);
        Long cancelledOrderId = createOrder(1L, greenOnion, 3);
        List<CartItem> items = IntStream.rangeClosed(2, members + 1)
            .mapToObj(memberId -> saveCartItem(saveCart((long) memberId), greenOnion, 1))
            .toList();

        Stream<Callable<Void>> cancel = Stream.of(() -> {
            orderCancelService.cancel(1L, cancelledOrderId);
            return null;
        });
        Stream<Callable<Void>> orders = items.stream().map(item -> () -> {
            orderCreateService.createOrder(item.getCart().getMemberId(), UUID.randomUUID().toString(),
                new OrderCreateRequest(List.of(new OrderCreateItemRequest(item.getId(), 3000, null))));
            return null;
        });
        List<Throwable> failures = runConcurrently(Stream.concat(cancel, orders).toList());

        // 처음 10개에서 취소한 주문의 3개는 돌아오고, 새 주문 5개만큼 빠져야 한다
        assertThat(failures).isEmpty();
        assertThat(stockOf(greenOnion)).isEqualTo(10 - members);
        assertThat(orderRepository.existsById(cancelledOrderId)).isFalse();
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

    private Long createOrder(Long memberId, Product product, int quantity) {
        CartItem item = saveCartItem(saveCart(memberId), product, quantity);
        return orderCreateService.createOrder(memberId, UUID.randomUUID().toString(),
            new OrderCreateRequest(List.of(new OrderCreateItemRequest(item.getId(), product.getPrice(), null)))).response().orderId();
    }

    // 재고는 이미 빠진 상태로 두고 주문만 저장한다. 결제 완료 주문은 저장 전에 상태를 바꾼다.
    private Order saveOrder(Long memberId, OrderStatus status, OrderItem.OrderItemBuilder... items) {
        Order order = Order.builder()
            .orderNumber(UUID.randomUUID().toString().substring(0, 20))
            .memberId(memberId)
            .idempotencyKey(UUID.randomUUID().toString())
            .totalAmount(0L)
            .build();
        if (status == OrderStatus.PAID) {
            order.markPaid(LocalDateTime.now());
        }
        Order saved = orderRepository.save(order);
        orderItemRepository.saveAll(Stream.of(items).map(item -> item.order(saved).build()).toList());
        return saved;
    }

    private OrderItem.OrderItemBuilder item(Product product, int quantity) {
        return OrderItem.builder()
            .productId(product.getId())
            .productName(product.getName())
            .quantity(quantity)
            .listPrice(product.getPrice())
            .unitPrice(product.getPrice())
            .saleType(SaleType.NONE);
    }

    // 한정 수량에서 하나가 팔린 상태로 만든다
    private SaleEventItem saveFlashDrop(Product product, int limitedQuantity) {
        SaleEvent event = saleEventRepository.save(SaleEvent.builder().type(SaleEventType.FLASH_DROP)
            .startsAt(LocalDateTime.now().minusHours(1)).endsAt(LocalDateTime.now().plusHours(1)).build());
        SaleEventItem item = saleEventItemRepository.save(SaleEventItem.builder().saleEvent(event)
            .productId(product.getId()).listPrice(product.getPrice()).discountRate(50)
            .limitedQuantity(limitedQuantity).build());
        item.decreaseRemaining(1);
        return saleEventItemRepository.save(item);
    }

    private OrderItem.OrderItemBuilder flashDropItem(Product product, Long memberId) {
        return item(product, 1).saleType(SaleType.FLASH_DROP).flashDropMemberId(memberId);
    }

    // 주문 만료 대상이 되도록 생성 시각을 5분보다 이전으로 돌린다
    private void makeOld(Order order) {
        jdbcTemplate.update("update orders set created_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(10), order.getId());
    }

    // 결제를 만든 시각을 결제 만료 시간보다 이전으로 돌린다
    private void makeOld(Payment payment) {
        jdbcTemplate.update("update payments set created_at = ? where id = ?",
            LocalDateTime.now().minusMinutes(20), payment.getId());
    }

    private Payment saveReadyPayment(Order order) {
        return paymentRepository.save(Payment.builder()
            .order(order)
            .portonePaymentId("payment-" + UUID.randomUUID().toString().replace("-", ""))
            .amount(order.getTotalAmount())
            .expiresAt(LocalDateTime.now().plusMinutes(10))
            .build());
    }

    private Payment saveFailedPayment(Order order) {
        Payment payment = Payment.builder()
            .order(order)
            .portonePaymentId("payment-" + UUID.randomUUID().toString().replace("-", ""))
            .amount(order.getTotalAmount())
            .expiresAt(LocalDateTime.now().plusMinutes(10))
            .build();
        payment.fail(FailReason.USER_CANCELLED);
        return paymentRepository.save(payment);
    }

    private void assertDeleted(Order order) {
        assertThat(orderRepository.existsById(order.getId())).isFalse();
        assertThat(orderItemRepository.findByOrderIdOrderByIdAsc(order.getId())).isEmpty();
    }

    private OrderStatus statusOf(Order order) {
        return orderRepository.findById(order.getId()).orElseThrow().getStatus();
    }

    private int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStock();
    }

    private Product saveProduct(String name, int price, int stock) {
        return saveProduct(name, price, stock, ProductStatus.ON_SALE);
    }

    private Product saveProduct(String name, int price, int stock, ProductStatus status) {
        return productRepository.save(Product.builder()
            .productType(ProductType.INGREDIENT)
            .name(name)
            .imageUrl("https://img.test/" + name + ".png")
            .price(price)
            .stock(stock)
            .storage(StorageType.REFRIGERATED)
            .shelfDays(7)
            .status(status)
            .build());
    }

    private Cart saveCart(Long memberId) {
        return cartRepository.save(Cart.builder().memberId(memberId).build());
    }

    private CartItem saveCartItem(Cart cart, Product product, int quantity) {
        return cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(quantity).build());
    }
}
