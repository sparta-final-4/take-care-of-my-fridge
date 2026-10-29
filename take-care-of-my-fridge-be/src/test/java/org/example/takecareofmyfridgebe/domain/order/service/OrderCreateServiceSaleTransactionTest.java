package org.example.takecareofmyfridgebe.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateItemRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateResult;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 진행 중인 특가·번개 드롭이 있을 때의 주문하기와 취소. 응답이 아니라 커밋된 주문·재고·남은 수량을 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, OrderCreateService.class, OrderCancelService.class,
    OrderNumberGenerator.class, OrderReader.class, SaleEventReader.class,
    SaleEventStockService.class, PaymentService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderCreateServiceSaleTransactionTest extends MySqlContainerSupport {

    private static final LocalDateTime NOW = LocalDateTime.now(SaleEvent.ZONE);

    private final OrderCreateService orderCreateService;
    private final OrderCancelService orderCancelService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    OrderCreateServiceSaleTransactionTest(
            OrderCreateService orderCreateService,
            OrderCancelService orderCancelService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository,
            DataSource dataSource) {
        this.orderCreateService = orderCreateService;
        this.orderCancelService = orderCancelService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.saleEventRepository = saleEventRepository;
        this.saleEventItemRepository = saleEventItemRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void cleanUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        saleEventItemRepository.deleteAll();
        saleEventRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("진행 중인 특가 상품을 주문하면 할인가로 합계가 계산되고 주문 상품에 할인 구분과 행사 상품 번호가 저장된다")
    void createOrder_specialSale_savesSalePrice() {
        Product tofu = saveProduct("두부", 3000, 10);
        Product greenOnion = saveProduct("대파", 3000, 10);
        SaleEventItem special = saveActiveSale(SaleEventType.SPECIAL, tofu, 20, null);
        Cart cart = saveCart(1L);
        CartItem tofuItem = saveCartItem(cart, tofu, 2);
        CartItem onionItem = saveCartItem(cart, greenOnion, 1);

        Long orderId = order(1L, UUID.randomUUID().toString(),
            line(tofuItem, 2400, SaleEventType.SPECIAL), line(onionItem, 3000, null)).response().orderId();

        assertThat(orderRepository.findById(orderId).orElseThrow().getTotalAmount()).isEqualTo(7800L);
        assertThat(orderItemRepository.findAll())
            .extracting(OrderItem::getProductName, OrderItem::getListPrice, OrderItem::getUnitPrice,
                OrderItem::getSaleType, OrderItem::getSaleEventItemId, OrderItem::getFlashDropMemberId)
            .containsExactlyInAnyOrder(
                tuple("두부", 3000, 2400, SaleType.SPECIAL, special.getId(), null),
                tuple("대파", 3000, 3000, SaleType.NONE, null, null));
        assertThat(stockOf(tofu)).isEqualTo(8);
    }

    @Test
    @DisplayName("장바구니에서 본 가격이나 할인 구분이 주문 시점과 다르면 409로 거절하고 주문·재고는 그대로다")
    void createOrder_priceChanged_rejectsWithoutSideEffects() {
        Product tofu = saveProduct("두부", 3000, 10);
        Product greenOnion = saveProduct("대파", 3000, 10);
        Product milk = saveProduct("우유", 3000, 10);
        saveActiveSale(SaleEventType.SPECIAL, tofu, 20, null);
        Cart cart = saveCart(1L);
        CartItem tofuItem = saveCartItem(cart, tofu, 1);
        CartItem onionItem = saveCartItem(cart, greenOnion, 1);
        CartItem milkItem = saveCartItem(cart, milk, 1);

        // 정가로 봤는데 그 사이 특가가 시작됨
        assertPriceChanged(() -> order(1L, "key-1", line(tofuItem, 3000, null)), "할인 시작 · 두부");
        // 특가로 봤는데 그 사이 특가가 끝남
        assertPriceChanged(() -> order(1L, "key-2", line(onionItem, 2400, SaleEventType.SPECIAL)), "할인 종료 · 대파");
        // 할인 구분은 같은데 정가가 바뀜(싸진 경우도 거절)
        assertPriceChanged(() -> order(1L, "key-3", line(milkItem, 3500, null)), "가격 변경 · 우유");

        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
        assertThat(stockOf(tofu)).isEqualTo(10);
        assertThat(stockOf(greenOnion)).isEqualTo(10);
        assertThat(stockOf(milk)).isEqualTo(10);
    }

    @Test
    @DisplayName("한 항목이라도 가격이 다르면 다른 항목의 재고도 빠지지 않는다")
    void createOrder_onePriceChanged_rejectsWholeOrder() {
        Product tofu = saveProduct("두부", 3000, 10);
        Product greenOnion = saveProduct("대파", 3000, 10);
        Cart cart = saveCart(1L);
        CartItem tofuItem = saveCartItem(cart, tofu, 1);
        CartItem onionItem = saveCartItem(cart, greenOnion, 1);

        assertPriceChanged(() -> order(1L, "key-1", line(tofuItem, 3000, null), line(onionItem, 2900, null)),
            "가격 변경 · 대파");

        assertThat(stockOf(tofu)).isEqualTo(10);
        assertThat(stockOf(greenOnion)).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("같은 멱등 키로 다시 보내면 그 사이 가격이 바뀌었어도 처음 주문을 그대로 돌려준다")
    void createOrder_replayAfterPriceChange_returnsFirstOrder() {
        Product tofu = saveProduct("두부", 3000, 10);
        CartItem item = saveCartItem(saveCart(1L), tofu, 1);
        OrderCreateResult first = order(1L, "same-key", line(item, 3000, null));
        saveActiveSale(SaleEventType.SPECIAL, tofu, 20, null);

        OrderCreateResult replay = order(1L, "same-key", line(item, 3000, null));

        assertThat(replay.created()).isFalse();
        assertThat(replay.response().orderId()).isEqualTo(first.response().orderId());
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(tofu)).isEqualTo(9);
    }

    @Test
    @DisplayName("번개 드롭을 주문하면 남은 수량이 줄고 주문 상품에 1인 1개 확인용 회원이 저장된다")
    void createOrder_flashDrop_decreasesRemaining() {
        Product milk = saveProduct("우유", 4000, 10);
        SaleEventItem flashDrop = saveActiveSale(SaleEventType.FLASH_DROP, milk, 50, 3);
        CartItem item = saveCartItem(saveCart(7L), milk, 1);

        order(7L, "key-1", line(item, 2000, SaleEventType.FLASH_DROP));

        assertThat(remainingOf(flashDrop)).isEqualTo(2);
        assertThat(stockOf(milk)).isEqualTo(9);
        OrderItem saved = orderItemRepository.findAll().get(0);
        assertThat(saved.getSaleType()).isEqualTo(SaleType.FLASH_DROP);
        assertThat(saved.getUnitPrice()).isEqualTo(2000);
        assertThat(saved.getSaleEventItemId()).isEqualTo(flashDrop.getId());
        assertThat(saved.getFlashDropMemberId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("이미 산 번개 드롭은 다시 주문할 수 없고, 취소하면 남은 수량이 돌아와 다시 주문할 수 있다")
    void createOrder_flashDropTwice_limitedUntilCancelled() {
        Product milk = saveProduct("우유", 4000, 10);
        SaleEventItem flashDrop = saveActiveSale(SaleEventType.FLASH_DROP, milk, 50, 3);
        CartItem item = saveCartItem(saveCart(7L), milk, 1);
        Long firstOrderId = order(7L, "key-1", line(item, 2000, SaleEventType.FLASH_DROP)).response().orderId();

        assertThatThrownBy(() -> order(7L, "key-2", line(item, 2000, SaleEventType.FLASH_DROP)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("구매 한도 초과 · 우유")
            .extracting("errorCode").isEqualTo(ErrorCode.PURCHASE_LIMIT_EXCEEDED);
        assertThat(remainingOf(flashDrop)).isEqualTo(2);
        assertThat(orderRepository.count()).isEqualTo(1);

        orderCancelService.cancel(7L, firstOrderId);

        assertThat(remainingOf(flashDrop)).isEqualTo(3);
        assertThat(stockOf(milk)).isEqualTo(10);
        assertThat(orderItemRepository.findAll()).isEmpty();
        order(7L, "key-3", line(item, 2000, SaleEventType.FLASH_DROP));
        assertThat(remainingOf(flashDrop)).isEqualTo(2);
    }

    @Test
    @DisplayName("번개 드롭 수량을 2개 주문하면 구매 한도 초과로 거절한다")
    void createOrder_flashDropQuantityOverOne_rejects() {
        Product milk = saveProduct("우유", 4000, 10);
        SaleEventItem flashDrop = saveActiveSale(SaleEventType.FLASH_DROP, milk, 50, 3);
        CartItem item = saveCartItem(saveCart(7L), milk, 2);

        assertThatThrownBy(() -> order(7L, "key-1", line(item, 2000, SaleEventType.FLASH_DROP)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.PURCHASE_LIMIT_EXCEEDED);

        assertThat(remainingOf(flashDrop)).isEqualTo(3);
        assertThat(stockOf(milk)).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("번개 드롭 남은 수량이 0이면 품절로 거절하고 다른 항목의 재고도 빠지지 않는다")
    void createOrder_flashDropSoldOut_rejectsWholeOrder() {
        Product milk = saveProduct("우유", 4000, 10);
        Product greenOnion = saveProduct("대파", 3000, 10);
        SaleEventItem flashDrop = saveActiveSale(SaleEventType.FLASH_DROP, milk, 50, 1);
        jdbcTemplate.update("update sale_event_items set remaining_quantity = 0 where id = ?", flashDrop.getId());
        Cart cart = saveCart(7L);
        CartItem milkItem = saveCartItem(cart, milk, 1);
        CartItem onionItem = saveCartItem(cart, greenOnion, 1);

        assertThatThrownBy(() -> order(7L, "key-1",
            line(onionItem, 3000, null), line(milkItem, 2000, SaleEventType.FLASH_DROP)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("품절 · 우유")
            .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_STOCK);

        assertThat(remainingOf(flashDrop)).isZero();
        assertThat(stockOf(milk)).isEqualTo(10);
        assertThat(stockOf(greenOnion)).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("한정 3개 번개 드롭을 10명이 동시에 주문하면 정확히 3명만 성공하고 남은 수량은 0이며 음수가 되지 않는다")
    void createOrder_flashDropConcurrently_neverOversells() throws Exception {
        int members = 10;
        Product milk = saveProduct("우유", 4000, 100);
        SaleEventItem flashDrop = saveActiveSale(SaleEventType.FLASH_DROP, milk, 50, 3);
        List<CartItem> items = IntStream.rangeClosed(1, members)
            .mapToObj(memberId -> saveCartItem(saveCart((long) memberId), milk, 1))
            .toList();

        List<Throwable> failures = runConcurrently(IntStream.range(0, members)
            .mapToObj(i -> (Callable<Void>) () -> {
                order((long) i + 1, UUID.randomUUID().toString(), line(items.get(i), 2000, SaleEventType.FLASH_DROP));
                return null;
            })
            .toList());

        assertThat(failures).hasSize(members - 3)
            .allSatisfy(failure -> assertThat(failure).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
        assertThat(remainingOf(flashDrop)).isZero();
        assertThat(orderRepository.findAll()).hasSize(3).allSatisfy(order -> assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING));
        assertThat(orderItemRepository.findAll()).hasSize(3)
            .extracting(OrderItem::getFlashDropMemberId).doesNotHaveDuplicates();
        assertThat(stockOf(milk)).isEqualTo(97);
    }

    private void assertPriceChanged(Runnable call, String message) {
        assertThatThrownBy(call::run)
            .isInstanceOf(BusinessException.class)
            .hasMessage(message)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_PRICE_CHANGED);
    }

    private OrderCreateResult order(Long memberId, String idempotencyKey, OrderCreateItemRequest... lines) {
        return orderCreateService.createOrder(memberId, idempotencyKey, new OrderCreateRequest(List.of(lines)));
    }

    private OrderCreateItemRequest line(CartItem item, int unitPrice, SaleEventType saleType) {
        return new OrderCreateItemRequest(item.getId(), unitPrice, saleType);
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

    // 행사는 항상 예정으로 만들어지므로 진행 중은 DB 값을 직접 바꿔 만든다
    private SaleEventItem saveActiveSale(SaleEventType type, Product product, int discountRate, Integer limitedQuantity) {
        SaleEvent event = saleEventRepository.save(SaleEvent.builder()
            .type(type).startsAt(NOW.minusHours(1)).endsAt(NOW.plusHours(1)).build());
        jdbcTemplate.update("update sale_events set status = 'ACTIVE' where id = ?", event.getId());
        return saleEventItemRepository.save(SaleEventItem.builder()
            .saleEvent(event).productId(product.getId()).listPrice(product.getPrice())
            .discountRate(discountRate).limitedQuantity(limitedQuantity).build());
    }

    private int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStock();
    }

    private int remainingOf(SaleEventItem item) {
        return saleEventItemRepository.findById(item.getId()).orElseThrow().getRemainingQuantity();
    }

    private Product saveProduct(String name, int price, int stock) {
        return productRepository.save(Product.builder()
            .productType(ProductType.INGREDIENT)
            .name(name)
            .imageUrl("https://img.test/" + name + ".png")
            .price(price)
            .stock(stock)
            .storage(StorageType.REFRIGERATED)
            .shelfDays(7)
            .build());
    }

    private Cart saveCart(Long memberId) {
        return cartRepository.save(Cart.builder().memberId(memberId).build());
    }

    private CartItem saveCartItem(Cart cart, Product product, int quantity) {
        return cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(quantity).build());
    }
}
