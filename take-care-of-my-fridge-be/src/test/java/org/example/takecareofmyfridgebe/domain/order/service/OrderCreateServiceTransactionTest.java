package org.example.takecareofmyfridgebe.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.Arrays;
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
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCheckoutResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateItemRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateResult;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 DB에 반영되는지 확인한다.
// 응답이 아니라 커밋된 재고·주문·장바구니 상태를 다시 읽어 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, OrderCreateService.class, OrderNumberGenerator.class, OrderReader.class,
    SaleEventReader.class, SaleEventStockService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderCreateServiceTransactionTest extends MySqlContainerSupport {

    private final OrderCreateService orderCreateService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Autowired
    OrderCreateServiceTransactionTest(
            OrderCreateService orderCreateService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository) {
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
    @DisplayName("주문하면 재고가 빠지고 주문·주문 상품이 저장되며 장바구니는 그대로다")
    void createOrder_decreasesStockAndSavesOrder() {
        Product greenOnion = saveProduct("대파", 3000, 10);
        Product tofu = saveProduct("두부", 2000, 5);
        Cart cart = saveCart(1L);
        CartItem item1 = saveCartItem(cart, greenOnion, 2);
        CartItem item2 = saveCartItem(cart, tofu, 1);

        OrderCheckoutResponse response = order(1L, request(item1, item2));

        assertThat(stockOf(greenOnion)).isEqualTo(8);
        assertThat(stockOf(tofu)).isEqualTo(4);
        Order order = orderRepository.findById(response.orderId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getMemberId()).isEqualTo(1L);
        assertThat(order.getTotalAmount()).isEqualTo(8000L);
        assertThat(order.getOrderNumber()).matches("\\d{20}");
        assertThat(orderItemRepository.findAll())
            .extracting(OrderItem::getProductName, OrderItem::getProductImageUrl, OrderItem::getQuantity,
                OrderItem::getListPrice, OrderItem::getUnitPrice, OrderItem::getSaleType)
            .containsExactlyInAnyOrder(
                tuple("대파", "https://img.test/대파.png", 2, 3000, 3000, SaleType.NONE),
                tuple("두부", "https://img.test/두부.png", 1, 2000, 2000, SaleType.NONE));
        assertThat(cartItemRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("한 상품이라도 재고가 모자라면 앞서 뺀 재고까지 롤백되고 주문은 없다")
    void createOrder_rollsBackAllStock_whenOneProductShort() {
        Product greenOnion = saveProduct("대파", 3000, 10);
        Product tofu = saveProduct("두부", 2000, 1);
        Cart cart = saveCart(1L);
        CartItem item1 = saveCartItem(cart, greenOnion, 2);
        CartItem item2 = saveCartItem(cart, tofu, 2);

        assertThatThrownBy(() -> order(1L, request(item1, item2)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("재고 부족 · 두부")
            .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_STOCK);

        assertThat(stockOf(greenOnion)).isEqualTo(10);
        assertThat(stockOf(tofu)).isEqualTo(1);
        assertThat(orderRepository.count()).isZero();
        assertThat(orderItemRepository.count()).isZero();
    }

    @Test
    @DisplayName("마지막 재고까지 주문하면 상품이 품절 상태로 바뀐다")
    void createOrder_lastStock_marksProductSoldOut() {
        Product greenOnion = saveProduct("대파", 3000, 2);
        CartItem item = saveCartItem(saveCart(1L), greenOnion, 2);

        order(1L, request(item));

        assertThat(stockOf(greenOnion)).isZero();
        assertThat(statusOf(greenOnion)).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    @DisplayName("재고가 0이면 품절로 거절한다")
    void createOrder_rejectsSoldOut() {
        Product greenOnion = saveProduct("대파", 3000, 0);
        CartItem item = saveCartItem(saveCart(1L), greenOnion, 1);

        assertThatThrownBy(() -> order(1L, request(item)))
            .isInstanceOf(BusinessException.class)
            .hasMessage("품절 · 대파");
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("장바구니 수량이 음수인 항목은 주문을 거절하고 재고와 주문은 그대로다")
    void createOrder_rejectsNegativeCartQuantity() {
        Product greenOnion = saveProduct("대파", 3000, 10);
        CartItem item = saveCartItem(saveCart(1L), greenOnion, -3);

        assertThatThrownBy(() -> order(1L, request(item)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(stockOf(greenOnion)).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("주문 뒤 장바구니 항목이 지워져도 같은 멱등 키 재전송에는 처음 주문을 돌려준다")
    void createOrder_sameKeyAfterCartItemRemoved_returnsFirstOrder() {
        Product greenOnion = saveProduct("대파", 3000, 10);
        CartItem item = saveCartItem(saveCart(1L), greenOnion, 2);
        OrderCreateResult first = orderCreateService.createOrder(1L, "key-1", request(item));
        cartItemRepository.deleteById(item.getId());

        OrderCreateResult retry = orderCreateService.createOrder(1L, "key-1", request(item));

        assertThat(retry.created()).isFalse();
        assertThat(retry.response().orderId()).isEqualTo(first.response().orderId());
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(greenOnion)).isEqualTo(8);
    }

    @Test
    @DisplayName("장바구니 항목의 상품이 없어졌으면 404로 거절하고 주문은 없다")
    void createOrder_rejectsMissingProduct() {
        Cart cart = saveCart(1L);
        CartItem orphan = cartItemRepository.save(CartItem.builder().cart(cart).productId(999_999L).quantity(1).build());

        assertThatThrownBy(() -> order(1L, request(orphan)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("판매 중지 상품은 거절하고 재고는 그대로다")
    void createOrder_rejectsStoppedProduct() {
        Product stopped = productRepository.save(product("대파", 3000, 10, ProductStatus.STOPPED));
        CartItem item = saveCartItem(saveCart(1L), stopped, 1);

        assertThatThrownBy(() -> order(1L, request(item)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_ON_SALE);
        assertThat(stockOf(stopped)).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("다른 회원의 장바구니 항목은 주문할 수 없고 재고는 그대로다")
    void createOrder_rejectsOthersCartItem() {
        Product greenOnion = saveProduct("대파", 3000, 10);
        saveCart(1L);
        CartItem itemOfOther = saveCartItem(saveCart(2L), greenOnion, 1);

        assertThatThrownBy(() -> order(1L, request(itemOfOther)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
        assertThat(stockOf(greenOnion)).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    @DisplayName("같은 멱등 키로 다시 주문하면 새로 만들지 않고 처음 주문을 돌려주며 재고는 한 번만 빠진다")
    void createOrder_sameIdempotencyKey_returnsFirstOrder() {
        Product greenOnion = saveProduct("대파", 3000, 10);
        CartItem item = saveCartItem(saveCart(1L), greenOnion, 2);

        OrderCreateResult first = orderCreateService.createOrder(1L, "key-1", request(item));
        OrderCreateResult second = orderCreateService.createOrder(1L, "key-1", request(item));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.response().orderId()).isEqualTo(first.response().orderId());
        assertThat(second.response().items()).hasSize(1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(greenOnion)).isEqualTo(8);
    }

    @Test
    @DisplayName("같은 멱등 키로 다른 상품을 주문하면 거절하고 처음 주문과 재고는 그대로다")
    void createOrder_sameKeyDifferentItems_rejects() {
        Product greenOnion = saveProduct("대파", 3000, 10);
        Product tofu = saveProduct("두부", 2000, 5);
        Cart cart = saveCart(1L);
        CartItem item1 = saveCartItem(cart, greenOnion, 1);
        CartItem item2 = saveCartItem(cart, tofu, 1);
        orderCreateService.createOrder(1L, "key-1", request(item1));

        assertThatThrownBy(() -> orderCreateService.createOrder(1L, "key-1", request(item2)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_IDEMPOTENCY_KEY_REUSED);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(greenOnion)).isEqualTo(9);
        assertThat(stockOf(tofu)).isEqualTo(5);
    }

    @Test
    @DisplayName("같은 상품이라도 멱등 키가 다르면 새 주문이 만들어진다")
    void createOrder_differentIdempotencyKey_createsNewOrder() {
        Product greenOnion = saveProduct("대파", 3000, 10);
        CartItem item = saveCartItem(saveCart(1L), greenOnion, 1);

        orderCreateService.createOrder(1L, "key-1", request(item));
        orderCreateService.createOrder(1L, "key-2", request(item));

        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(stockOf(greenOnion)).isEqualTo(8);
    }

    @Test
    @DisplayName("다른 회원은 같은 멱등 키를 써도 각자 주문된다")
    void createOrder_sameKeyOtherMember_createsOwnOrder() {
        Product greenOnion = saveProduct("대파", 3000, 10);
        CartItem itemOfMember1 = saveCartItem(saveCart(1L), greenOnion, 1);
        CartItem itemOfMember2 = saveCartItem(saveCart(2L), greenOnion, 1);

        orderCreateService.createOrder(1L, "key-1", request(itemOfMember1));
        orderCreateService.createOrder(2L, "key-1", request(itemOfMember2));

        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(stockOf(greenOnion)).isEqualTo(8);
    }

    @Test
    @DisplayName("멱등 키가 없거나 64자를 넘으면 거절하고 주문은 없다")
    void createOrder_rejectsMissingOrTooLongKey() {
        Product greenOnion = saveProduct("대파", 3000, 10);
        CartItem item = saveCartItem(saveCart(1L), greenOnion, 1);

        assertThatThrownBy(() -> orderCreateService.createOrder(1L, null, request(item)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> orderCreateService.createOrder(1L, " ", request(item)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThatThrownBy(() -> orderCreateService.createOrder(1L, "k".repeat(65), request(item)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
        assertThat(orderRepository.count()).isZero();
        assertThat(stockOf(greenOnion)).isEqualTo(10);
    }

    @Test
    @DisplayName("여러 회원이 동시에 주문하면 재고만큼만 주문되고 나머지는 재고 부족으로 거절된다")
    void createOrder_concurrentMembers_neverOversells() throws Exception {
        int initialStock = 5;
        int members = 20;
        Product greenOnion = saveProduct("대파", 3000, initialStock);
        List<CartItem> items = IntStream.rangeClosed(1, members)
            .mapToObj(memberId -> saveCartItem(saveCart((long) memberId), greenOnion, 1))
            .toList();

        List<Throwable> failures = runConcurrently(IntStream.range(0, members)
            .mapToObj(i -> (Callable<Void>) () -> {
                order((long) i + 1, request(items.get(i)));
                return null;
            })
            .toList());

        int orderedQuantity = orderItemRepository.findAll().stream().mapToInt(OrderItem::getQuantity).sum();
        assertThat(stockOf(greenOnion)).isZero();
        assertThat(statusOf(greenOnion)).isEqualTo(ProductStatus.SOLD_OUT);
        assertThat(orderedQuantity).isEqualTo(initialStock);
        assertThat(orderRepository.count()).isEqualTo(initialStock);
        assertThat(failures).hasSize(members - initialStock).allSatisfy(e -> assertThat(e)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_STOCK));
    }

    @Test
    @DisplayName("같은 멱등 키로 동시에 여러 번 주문해도 주문은 하나만 생기고 모두 그 주문을 받는다")
    void createOrder_sameKeyConcurrently_createsOnlyOneOrder() throws Exception {
        Product greenOnion = saveProduct("대파", 3000, 100);
        CartItem item = saveCartItem(saveCart(1L), greenOnion, 2);
        List<Long> orderIds = Collections.synchronizedList(new ArrayList<>());

        List<Throwable> failures = runConcurrently(Collections.nCopies(5, () -> {
            orderIds.add(orderCreateService.createOrder(1L, "same-click", request(item)).response().orderId());
            return null;
        }));

        assertThat(failures).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(greenOnion)).isEqualTo(98);
        assertThat(orderIds).hasSize(5).containsOnly(orderRepository.findAll().get(0).getId());
    }

    @Test
    @DisplayName("장바구니에 담은 순서가 엇갈린 주문이 동시에 와도 데드락 없이 처리된다")
    void createOrder_crossedProductOrder_noDeadlock() throws Exception {
        int members = 20;
        Product greenOnion = saveProduct("대파", 3000, 100);
        Product tofu = saveProduct("두부", 2000, 100);
        // 짝수 회원은 대파→두부, 홀수 회원은 두부→대파 순서로 담는다
        List<OrderCreateRequest> requests = IntStream.rangeClosed(1, members)
            .mapToObj(memberId -> {
                Cart cart = saveCart((long) memberId);
                Product first = memberId % 2 == 0 ? greenOnion : tofu;
                Product second = memberId % 2 == 0 ? tofu : greenOnion;
                return request(saveCartItem(cart, first, 1), saveCartItem(cart, second, 1));
            })
            .toList();

        List<Throwable> failures = runConcurrently(IntStream.range(0, members)
            .mapToObj(i -> (Callable<Void>) () -> {
                order((long) i + 1, requests.get(i));
                return null;
            })
            .toList());

        // 재고가 충분하므로 모두 성공해야 한다. 데드락이면 실패가 남는다.
        assertThat(failures).isEmpty();
        assertThat(orderRepository.count()).isEqualTo(members);
        assertThat(stockOf(greenOnion)).isEqualTo(100 - members);
        assertThat(stockOf(tofu)).isEqualTo(100 - members);
    }

    // 클릭마다 새 멱등 키를 만드는 프론트처럼 매번 다른 키로 주문한다
    private OrderCheckoutResponse order(Long memberId, OrderCreateRequest request) {
        return orderCreateService.createOrder(memberId, UUID.randomUUID().toString(), request).response();
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

    private OrderCreateRequest request(CartItem... items) {
        return new OrderCreateRequest(Arrays.stream(items)
            .map(item -> new OrderCreateItemRequest(item.getId(), priceOf(item), null))
            .toList());
    }

    private int priceOf(CartItem item) {
        // 상품이 없는 항목을 주문하는 테스트도 있어 가격은 없으면 0으로 둔다
        return productRepository.findById(item.getProductId()).map(Product::getPrice).orElse(0);
    }

    private int stockOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStock();
    }

    private ProductStatus statusOf(Product product) {
        return productRepository.findById(product.getId()).orElseThrow().getStatus();
    }

    private Product saveProduct(String name, int price, int stock) {
        return productRepository.save(product(name, price, stock, ProductStatus.ON_SALE));
    }

    private Product product(String name, int price, int stock, ProductStatus status) {
        return Product.builder()
            .productType(ProductType.INGREDIENT)
            .name(name)
            .imageUrl("https://img.test/" + name + ".png")
            .price(price)
            .stock(stock)
            .storage(StorageType.REFRIGERATED)
            .shelfDays(7)
            .status(status)
            .build();
    }

    private Cart saveCart(Long memberId) {
        return cartRepository.save(Cart.builder().memberId(memberId).build());
    }

    private CartItem saveCartItem(Cart cart, Product product, int quantity) {
        return cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(quantity).build());
    }
}
