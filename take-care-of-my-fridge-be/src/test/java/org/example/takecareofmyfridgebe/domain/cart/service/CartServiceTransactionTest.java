package org.example.takecareofmyfridgebe.domain.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartItemResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.RemoveCartItemsRequest;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

// 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 서비스의 트랜잭션 경계로 DB에 반영되는지 확인한다.
// 테스트 트랜잭션이 켜져 있으면 서비스에서 @Transactional이 빠져도 가려진다.
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, CartService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CartServiceTransactionTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    private final CartService cartService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Autowired
    CartServiceTransactionTest(
            CartService cartService,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository) {
        this.cartService = cartService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
    }

    @AfterEach
    void cleanUp() {
        cartItemRepository.deleteAll();
        cartRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("수량 변경은 DB에 반영된다")
    void changeQuantity_persistsQuantity() {
        Product product = saveProduct(10, ProductStatus.ON_SALE);
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(1).build());

        ChangeCartItemQuantityResponse response = cartService.changeQuantity(1L, item.getId(), new ChangeCartItemQuantityRequest(3));

        assertThat(response.quantity()).isEqualTo(3);
        assertThat(cartItemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("다른 회원의 줄은 바꿀 수 없고 수량은 그대로다")
    void changeQuantity_rejectsOthersItem() {
        Product product = saveProduct(10, ProductStatus.ON_SALE);
        Cart cartA = cartRepository.save(Cart.builder().memberId(1L).build());
        cartRepository.save(Cart.builder().memberId(2L).build());
        CartItem itemOfA = cartItemRepository.save(CartItem.builder().cart(cartA).productId(product.getId()).quantity(1).build());

        assertThatThrownBy(() -> cartService.changeQuantity(2L, itemOfA.getId(), new ChangeCartItemQuantityRequest(5)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
        assertThat(cartItemRepository.findById(itemOfA.getId()).orElseThrow().getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("없는 줄 번호는 찾을 수 없다")
    void changeQuantity_rejectsMissingItem() {
        assertThatThrownBy(() -> cartService.changeQuantity(1L, 999L, new ChangeCartItemQuantityRequest(3)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CART_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("재고와 같은 수량까지는 담긴다")
    void addItem_acceptsQuantityEqualToStock() {
        Product product = saveProduct(3, ProductStatus.ON_SALE);

        cartService.addItem(1L, new AddCartItemRequest(product.getId(), 3));

        assertThat(cartItemRepository.findByCartMemberIdOrderByIdAsc(1L))
                .extracting(CartItem::getQuantity)
                .containsExactly(3);
    }

    @Test
    @DisplayName("기존 수량에 더해 재고를 넘으면 거절되고 수량은 그대로다")
    void addItem_rejectsWhenTotalExceedsStock() {
        Product product = saveProduct(5, ProductStatus.ON_SALE);
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(3).build());

        assertErrorCode(() -> cartService.addItem(1L, new AddCartItemRequest(product.getId(), 3)), ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        assertThat(cartItemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("기존 수량과 더해 int를 넘는 수량도 재고 초과로 거절된다")
    void addItem_rejectsOverflowingQuantity() {
        Product product = saveProduct(10, ProductStatus.ON_SALE);
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(5).build());

        assertErrorCode(() -> cartService.addItem(1L, new AddCartItemRequest(product.getId(), Integer.MAX_VALUE)), ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        assertThat(cartItemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("재고 초과로 거절되면 처음 담을 때 만든 장바구니도 남지 않는다")
    void addItem_rollsBackCartWhenStockInsufficient() {
        Product product = saveProduct(1, ProductStatus.ON_SALE);

        assertErrorCode(() -> cartService.addItem(1L, new AddCartItemRequest(product.getId(), 2)), ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        assertThat(cartRepository.findByMemberId(1L)).isEmpty();
    }

    @Test
    @DisplayName("판매 중지 상품은 담을 수 없다")
    void addItem_rejectsStoppedProduct() {
        Product product = saveProduct(10, ProductStatus.STOPPED);

        assertErrorCode(() -> cartService.addItem(1L, new AddCartItemRequest(product.getId(), 1)), ErrorCode.PRODUCT_NOT_ON_SALE);
        assertThat(cartItemRepository.findByCartMemberIdOrderByIdAsc(1L)).isEmpty();
    }

    @Test
    @DisplayName("재고가 0이어도 상태가 아니라 재고로 판단해 재고 초과로 거절한다")
    void addItem_rejectsZeroStockEvenIfOnSale() {
        Product product = saveProduct(0, ProductStatus.ON_SALE);

        assertErrorCode(() -> cartService.addItem(1L, new AddCartItemRequest(product.getId(), 1)), ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        assertThat(cartItemRepository.findByCartMemberIdOrderByIdAsc(1L)).isEmpty();
    }

    @Test
    @DisplayName("없는 상품은 담을 수 없다")
    void addItem_rejectsMissingProduct() {
        assertErrorCode(() -> cartService.addItem(1L, new AddCartItemRequest(999L, 1)), ErrorCode.PRODUCT_NOT_FOUND);
        assertThat(cartRepository.findByMemberId(1L)).isEmpty();
    }

    @Test
    @DisplayName("재고보다 많이 올리면 거절되고 수량은 그대로다")
    void changeQuantity_rejectsIncreaseOverStock() {
        Product product = saveProduct(3, ProductStatus.ON_SALE);
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(1).build());

        assertErrorCode(() -> cartService.changeQuantity(1L, item.getId(), new ChangeCartItemQuantityRequest(4)), ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        assertThat(cartItemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("담아 둔 뒤 재고가 줄었어도 수량을 내리는 것은 반영된다")
    void changeQuantity_allowsDecreaseBelowCurrentEvenOverStock() {
        Product product = saveProduct(2, ProductStatus.ON_SALE);
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(5).build());

        cartService.changeQuantity(1L, item.getId(), new ChangeCartItemQuantityRequest(4));

        assertThat(cartItemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("재고가 줄어든 줄은 현재 수량보다 올릴 수 없다")
    void changeQuantity_rejectsIncreaseWhenAlreadyOverStock() {
        Product product = saveProduct(2, ProductStatus.ON_SALE);
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(5).build());

        assertErrorCode(() -> cartService.changeQuantity(1L, item.getId(), new ChangeCartItemQuantityRequest(6)), ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        assertThat(cartItemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("판매 중지된 상품의 줄은 수량을 바꿀 수 없다")
    void changeQuantity_rejectsStoppedProduct() {
        Product product = saveProduct(10, ProductStatus.STOPPED);
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(1).build());

        assertErrorCode(() -> cartService.changeQuantity(1L, item.getId(), new ChangeCartItemQuantityRequest(2)), ErrorCode.PRODUCT_NOT_ON_SALE);
        assertThat(cartItemRepository.findById(item.getId()).orElseThrow().getQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("조회는 상품 이름·이미지·정가를 붙이고, 재고 0이나 판매 중지 상품은 품절로 표시한다")
    void getItems_includesProductInfoAndSoldOut() {
        Product onSale = saveProduct(5, ProductStatus.ON_SALE);
        Product noStock = saveProduct(0, ProductStatus.ON_SALE);
        Product stopped = saveProduct(5, ProductStatus.STOPPED);
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        cartItemRepository.save(CartItem.builder().cart(cart).productId(onSale.getId()).quantity(1).build());
        cartItemRepository.save(CartItem.builder().cart(cart).productId(noStock.getId()).quantity(2).build());
        cartItemRepository.save(CartItem.builder().cart(cart).productId(stopped.getId()).quantity(3).build());

        var items = cartService.getItems(1L).items();

        assertThat(items).extracting(CartItemResponse::productId)
                .containsExactly(onSale.getId(), noStock.getId(), stopped.getId());
        assertThat(items).extracting(CartItemResponse::soldOut).containsExactly(false, true, true);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.productName()).isEqualTo("대파");
            assertThat(item.imageUrl()).isEqualTo("https://example.com/green-onion.png");
            assertThat(item.listPrice()).isEqualTo(1000);
            assertThat(item.unitPrice()).isEqualTo(1000);
            assertThat(item.saleType()).isNull();
            assertThat(item.discountRate()).isNull();
            assertThat(item.flashDropRemaining()).isNull();
        });
    }

    @Test
    @DisplayName("목록에 든 내 줄은 DB에서 지워지고 나머지는 남는다")
    void removeItems_deletesListedOwnItems() {
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem first = cartItemRepository.save(CartItem.builder().cart(cart).productId(10L).quantity(1).build());
        CartItem second = cartItemRepository.save(CartItem.builder().cart(cart).productId(20L).quantity(1).build());

        cartService.removeItems(1L, new RemoveCartItemsRequest(List.of(first.getId())));

        assertThat(cartItemRepository.findAll()).extracting(CartItem::getId).containsExactly(second.getId());
    }

    @Test
    @DisplayName("다른 회원의 줄과 없는 번호가 섞여도 내 줄만 지우고 오류는 없다")
    void removeItems_ignoresOthersAndMissingIds() {
        Cart cartA = cartRepository.save(Cart.builder().memberId(1L).build());
        Cart cartB = cartRepository.save(Cart.builder().memberId(2L).build());
        CartItem itemOfA = cartItemRepository.save(CartItem.builder().cart(cartA).productId(10L).quantity(1).build());
        CartItem itemOfB = cartItemRepository.save(CartItem.builder().cart(cartB).productId(10L).quantity(1).build());

        cartService.removeItems(1L, new RemoveCartItemsRequest(List.of(itemOfA.getId(), itemOfB.getId(), 999L)));

        assertThat(cartItemRepository.findAll()).extracting(CartItem::getId).containsExactly(itemOfB.getId());
    }

    @Test
    @DisplayName("같은 요청을 두 번 보내도 같은 결과다")
    void removeItems_isIdempotent() {
        Cart cart = cartRepository.save(Cart.builder().memberId(1L).build());
        CartItem item = cartItemRepository.save(CartItem.builder().cart(cart).productId(10L).quantity(1).build());
        RemoveCartItemsRequest request = new RemoveCartItemsRequest(List.of(item.getId()));

        cartService.removeItems(1L, request);
        cartService.removeItems(1L, request);

        assertThat(cartItemRepository.findAll()).isEmpty();
    }

    private Product saveProduct(int stock, ProductStatus status) {
        return productRepository.save(Product.builder()
                .productType(ProductType.INGREDIENT)
                .name("대파")
                .imageUrl("https://example.com/green-onion.png")
                .price(1000)
                .stock(stock)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(7)
                .status(status)
                .build());
    }

    private void assertErrorCode(ThrowingCallable call, ErrorCode errorCode) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
