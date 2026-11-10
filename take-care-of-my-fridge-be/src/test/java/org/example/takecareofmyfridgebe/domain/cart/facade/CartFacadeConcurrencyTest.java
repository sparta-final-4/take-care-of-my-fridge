package org.example.takecareofmyfridgebe.domain.cart.facade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemRequest;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.cart.service.CartService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventItemRepository;
import org.example.takecareofmyfridgebe.domain.sale.repository.SaleEventRepository;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.global.config.jpa.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.jpa.QueryDslConfig;
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

// 같은 회원이 같은 상품을 동시에 담을 때 DB에 남은 최종 상태로 확인한다. 응답이 성공이라는 것만으로는 통과시키지 않는다.
// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 스레드마다 실제 트랜잭션이 커밋되게 한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, CartService.class, CartFacade.class, ProductService.class, SaleEventReader.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CartFacadeConcurrencyTest extends MySqlContainerSupport {

    private static final long MEMBER_ID = 1L;
    private static final int THREADS = 5;

    private final CartFacade cartFacade;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final SaleEventRepository saleEventRepository;
    private final SaleEventItemRepository saleEventItemRepository;

    @Autowired
    CartFacadeConcurrencyTest(
            CartFacade cartFacade,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            SaleEventRepository saleEventRepository,
            SaleEventItemRepository saleEventItemRepository) {
        this.cartFacade = cartFacade;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.saleEventRepository = saleEventRepository;
        this.saleEventItemRepository = saleEventItemRepository;
    }

    @AfterEach
    void cleanUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        saleEventItemRepository.deleteAll();
        saleEventRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("장바구니가 없는 회원이 같은 상품을 동시에 담아도 오류 없이 장바구니와 항목이 하나씩만 남는다")
    void keepsOneCartAndOneItem_whenFirstAddsAreConcurrent() throws Exception {
        Product product = saveProduct();

        List<Throwable> failures = addConcurrently(product.getId());

        assertThat(failures).isEmpty();
        assertThat(cartRepository.findAll()).extracting(Cart::getMemberId).containsExactly(MEMBER_ID);
        List<CartItem> items = cartItemRepository.findByCartMemberIdOrderByIdAsc(MEMBER_ID);
        assertThat(items).extracting(CartItem::getProductId).containsExactly(product.getId());
        assertThat(items.get(0).getQuantity()).isBetween(1, THREADS);
    }

    @Test
    @DisplayName("장바구니만 있고 그 상품 항목이 없을 때 동시에 담아도 오류 없이 항목이 하나만 남는다")
    void keepsOneItem_whenCartExistsAndAddsAreConcurrent() throws Exception {
        Product product = saveProduct();
        cartRepository.save(Cart.builder().memberId(MEMBER_ID).build());

        List<Throwable> failures = addConcurrently(product.getId());

        assertThat(failures).isEmpty();
        assertThat(cartRepository.findAll()).hasSize(1);
        List<CartItem> items = cartItemRepository.findByCartMemberIdOrderByIdAsc(MEMBER_ID);
        assertThat(items).extracting(CartItem::getProductId).containsExactly(product.getId());
        assertThat(items.get(0).getQuantity()).isBetween(1, THREADS);
    }

    @Test
    @DisplayName("이미 항목이 있을 때 동시에 담아도 오류 없이 항목이 하나만 남고 수량은 담은 요청 수를 넘지 않는다")
    void keepsOneItemWithinRequested_whenItemExistsAndAddsAreConcurrent() throws Exception {
        Product product = saveProduct();
        Cart cart = cartRepository.save(Cart.builder().memberId(MEMBER_ID).build());
        cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(2).build());

        List<Throwable> failures = addConcurrently(product.getId());

        assertThat(failures).isEmpty();
        assertThat(cartRepository.findAll()).hasSize(1);
        List<CartItem> items = cartItemRepository.findByCartMemberIdOrderByIdAsc(MEMBER_ID);
        assertThat(items).extracting(CartItem::getProductId).containsExactly(product.getId());
        assertThat(items.get(0).getQuantity()).isBetween(3, 2 + THREADS);
    }

    // 모든 스레드가 준비된 뒤 한꺼번에 시작해 각자 1개씩 담고, 밖으로 새어 나온 예외를 모아 돌려준다.
    private List<Throwable> addConcurrently(Long productId) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            Callable<Object> task = () -> {
                ready.countDown();
                start.await();
                return cartFacade.addItem(MEMBER_ID, new AddCartItemRequest(productId, 1));
            };
            futures.add(executor.submit(task));
        }

        ready.await(5, TimeUnit.SECONDS);
        start.countDown();

        List<Throwable> failures = new ArrayList<>();
        for (Future<Object> future : futures) {
            try {
                future.get(20, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            }
        }
        executor.shutdown();
        return failures;
    }

    private Product saveProduct() {
        return productRepository.save(Product.builder()
                .productType(ProductType.INGREDIENT)
                .name("대파")
                .imageUrl("https://example.com/green-onion.png")
                .price(1000)
                .stock(100)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .status(ProductStatus.ON_SALE)
                .build());
    }
}
