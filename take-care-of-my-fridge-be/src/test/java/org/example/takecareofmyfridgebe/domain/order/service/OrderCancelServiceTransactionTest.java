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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCancelResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateRequest;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
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
// 응답이 아니라 커밋된 주문 상태와 재고를 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, OrderCancelService.class, OrderCreateService.class,
    OrderNumberGenerator.class, OrderReader.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderCancelServiceTransactionTest extends MySqlContainerSupport {

    private final OrderCancelService orderCancelService;
    private final OrderCreateService orderCreateService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Autowired
    OrderCancelServiceTransactionTest(
            OrderCancelService orderCancelService,
            OrderCreateService orderCreateService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository) {
        this.orderCancelService = orderCancelService;
        this.orderCreateService = orderCreateService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    @AfterEach
    void cleanUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("결제 대기 주문을 취소하면 주문은 취소되고 재고는 주문 수량만큼 돌아온다")
    void cancel_pendingOrder_cancelsAndRestoresStock() {
        Product greenOnion = saveProduct("대파", 3000, 8);
        Product tofu = saveProduct("두부", 2000, 4);
        Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2), item(tofu, 1));

        OrderCancelResponse response = orderCancelService.cancel(1L, order.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLED);
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
    @DisplayName("번개 드롭으로 산 주문 상품은 취소하면 1인 1개 기록이 비워진다")
    void cancel_releasesFlashDropMember() {
        Product greenOnion = saveProduct("대파", 3000, 0);
        Order order = saveOrder(1L, OrderStatus.PENDING, flashDropItem(greenOnion, 1L));

        orderCancelService.cancel(1L, order.getId());

        assertThat(orderItemRepository.findByOrderIdOrderByIdAsc(order.getId()))
            .extracting(OrderItem::getFlashDropMemberId)
            .containsOnlyNulls();
        assertThat(stockOf(greenOnion)).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 취소된 주문을 다시 취소하면 같은 응답을 주고 재고는 그대로다")
    void cancel_cancelledOrder_returnsSameAndKeepsStock() {
        Product greenOnion = saveProduct("대파", 3000, 8);
        Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2));
        orderCancelService.cancel(1L, order.getId());

        OrderCancelResponse response = orderCancelService.cancel(1L, order.getId());

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(greenOnion)).isEqualTo(10);
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
    @DisplayName("같은 주문을 동시에 5번 취소해도 모두 취소 응답을 받고 재고는 한 번만 돌아온다")
    void cancel_sameOrderConcurrently_restoresStockOnce() throws Exception {
        Product greenOnion = saveProduct("대파", 3000, 8);
        Order order = saveOrder(1L, OrderStatus.PENDING, item(greenOnion, 2));
        List<OrderStatus> responses = Collections.synchronizedList(new ArrayList<>());

        List<Throwable> failures = runConcurrently(Collections.nCopies(5, () -> {
            responses.add(orderCancelService.cancel(1L, order.getId()).status());
            return null;
        }));

        assertThat(failures).isEmpty();
        assertThat(responses).hasSize(5).containsOnly(OrderStatus.CANCELLED);
        assertThat(statusOf(order)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(greenOnion)).isEqualTo(10);
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
                new OrderCreateRequest(List.of(item.getId())));
            return null;
        });
        List<Throwable> failures = runConcurrently(Stream.concat(cancel, orders).toList());

        // 처음 10개에서 취소한 주문의 3개는 돌아오고, 새 주문 5개만큼 빠져야 한다
        assertThat(failures).isEmpty();
        assertThat(stockOf(greenOnion)).isEqualTo(10 - members);
        assertThat(orderRepository.findById(cancelledOrderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
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
            new OrderCreateRequest(List.of(item.getId()))).response().orderId();
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

    private OrderItem.OrderItemBuilder flashDropItem(Product product, Long memberId) {
        return item(product, 1).saleType(SaleType.FLASH_DROP).flashDropMemberId(memberId);
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
