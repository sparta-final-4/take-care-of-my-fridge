package org.example.takecareofmyfridgebe.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.List;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateItemRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateRequest;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 주문번호 생성기를 대신해 같은 번호를 두 번 내게 하고, 유니크 위반이 409로 바뀌며 롤백되는지 DB 상태로 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, OrderCreateService.class, OrderReader.class,
    SaleEventReader.class, SaleEventStockService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderCreateServiceConstraintTest extends MySqlContainerSupport {

    @MockitoBean
    private OrderNumberGenerator orderNumberGenerator;

    private final OrderCreateService orderCreateService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Autowired
    OrderCreateServiceConstraintTest(
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
    @DisplayName("주문번호가 겹치면 409로 거절하고 두 번째 주문의 재고 차감까지 롤백된다")
    void createOrder_duplicateOrderNumber_rejectsAndRollsBack() {
        given(orderNumberGenerator.next()).willReturn("20261021143000123000");
        Product greenOnion = productRepository.save(Product.builder()
            .productType(ProductType.INGREDIENT).name("대파").price(3000).stock(10)
            .storage(StorageType.REFRIGERATED).shelfDays(7).build());
        CartItem itemOfMember1 = saveCartItem(1L, greenOnion);
        CartItem itemOfMember2 = saveCartItem(2L, greenOnion);
        orderCreateService.createOrder(1L, "key-1", new OrderCreateRequest(List.of(new OrderCreateItemRequest(itemOfMember1.getId(), 3000, null))));

        assertThatThrownBy(() -> orderCreateService.createOrder(2L, "key-2", new OrderCreateRequest(List.of(new OrderCreateItemRequest(itemOfMember2.getId(), 3000, null)))))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NUMBER_CONFLICT);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(greenOnion.getId()).orElseThrow().getStock()).isEqualTo(9);
    }

    private CartItem saveCartItem(Long memberId, Product product) {
        Cart cart = cartRepository.save(Cart.builder().memberId(memberId).build());
        return cartItemRepository.save(CartItem.builder().cart(cart).productId(product.getId()).quantity(1).build());
    }
}
