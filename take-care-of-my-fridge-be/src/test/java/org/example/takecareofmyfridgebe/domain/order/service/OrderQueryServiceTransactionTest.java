package org.example.takecareofmyfridgebe.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCheckoutResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderDetailItemResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderDetailResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderItemResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderListRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderSummaryResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 실제 DB에 저장된 주문을 서비스로 읽어, 내 주문만 보이는지와 화면마다 보이는 상태가 맞는지 확인한다.
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, OrderQueryService.class, OrderReader.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderQueryServiceTransactionTest extends MySqlContainerSupport {

    private final OrderQueryService orderQueryService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final Statistics statistics;

    @Autowired
    OrderQueryServiceTransactionTest(
            OrderQueryService orderQueryService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            EntityManagerFactory entityManagerFactory) {
        this.orderQueryService = orderQueryService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }

    @AfterEach
    void cleanUp() {
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("내 결제 대기 주문은 주문 상품과 금액까지 돌려준다")
    void getCheckout_returnsPendingOrder() {
        Order order = saveOrder(1L, "20261021143000123000");

        OrderCheckoutResponse response = orderQueryService.getCheckout(1L, order.getId());

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.payAmount()).isEqualTo(8000L);
        assertThat(response.items())
            .extracting(OrderItemResponse::productName, OrderItemResponse::quantity, OrderItemResponse::lineAmount)
            .containsExactly(tuple("대파", 2, 6000L), tuple("두부", 1, 2000L));
    }

    @Test
    @DisplayName("남의 주문은 없는 주문과 똑같이 404다")
    void getCheckout_othersOrder_returnsNotFound() {
        Order othersOrder = saveOrder(2L, "20261021143000123001");

        assertThatThrownBy(() -> orderQueryService.getCheckout(1L, othersOrder.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThatThrownBy(() -> orderQueryService.getCheckout(1L, othersOrder.getId() + 1000))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("결제가 끝난 주문은 이미 결제가 완료된 주문으로 거절한다")
    void getCheckout_paidOrder_rejects() {
        Order order = saveOrder(1L, "20261021143000123002");
        Order paid = orderRepository.findById(order.getId()).orElseThrow();
        paid.markPaid(LocalDateTime.now());
        orderRepository.save(paid);

        assertThatThrownBy(() -> orderQueryService.getCheckout(1L, order.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ALREADY_PAID);
    }

    @Test
    @DisplayName("취소된 주문은 취소된 주문으로 거절한다")
    void getCheckout_cancelledOrder_rejects() {
        Order order = saveOrder(1L, "20261021143000123003");
        Order cancelled = orderRepository.findById(order.getId()).orElseThrow();
        cancelled.cancel();
        orderRepository.save(cancelled);

        assertThatThrownBy(() -> orderQueryService.getCheckout(1L, order.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_CANCELLED);
    }

    @Test
    @DisplayName("주문 상세는 주문할 때 저장한 정가·단가로 상품 금액과 할인 금액을 돌려준다")
    void getDetail_returnsSavedPricesAndAmounts() {
        Order order = orderRepository.save(Order.builder()
            .orderNumber("20261022143000123030")
            .memberId(1L)
            .idempotencyKey("key-20261022143000123030")
            .totalAmount(7800L)
            .build());
        orderItemRepository.save(item(order, 1L, "대파", 2, 2900, 2900, SaleType.NONE));
        orderItemRepository.save(item(order, 2L, "두부", 1, 3000, 2000, SaleType.SPECIAL));
        markPaid(order);

        OrderDetailResponse response = orderQueryService.getDetail(1L, order.getId());

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.orderNumber()).isEqualTo("20261022143000123030");
        assertThat(response.status()).isEqualTo(OrderStatus.PAID);
        assertThat(response.orderedAt()).isNotNull();
        assertThat(response.confirmedAt()).isNull();
        assertThat(response.listPriceAmount()).isEqualTo(8800L);
        assertThat(response.discountAmount()).isEqualTo(1000L);
        assertThat(response.payAmount()).isEqualTo(7800L);
        assertThat(response.items())
            .extracting(OrderDetailItemResponse::productName, OrderDetailItemResponse::listPrice,
                OrderDetailItemResponse::unitPrice, OrderDetailItemResponse::lineAmount,
                OrderDetailItemResponse::saleType, OrderDetailItemResponse::confirmedQuantity)
            .containsExactly(
                tuple("대파", 2900, 2900, 5800L, SaleType.NONE, null),
                tuple("두부", 3000, 2000, 2000L, SaleType.SPECIAL, null));
    }

    @Test
    @DisplayName("구매 확정된 주문은 구매 확정 시각과 주문 상품별 확정 수량을 같이 돌려준다")
    void getDetail_confirmedOrder_returnsConfirmedAtAndQuantities() {
        Order order = saveOrder(1L, "20261022143000123031");
        markPaid(order);
        confirm(order);
        // 대파 2개 중 1개는 확정 전에 환불이 완료된 것으로 둔다
        confirmQuantities(order, 1L);
        LocalDateTime savedConfirmedAt = orderRepository.findById(order.getId()).orElseThrow().getConfirmedAt();

        OrderDetailResponse response = orderQueryService.getDetail(1L, order.getId());

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.confirmedAt()).isEqualTo(savedConfirmedAt);
        assertThat(response.items())
            .extracting(OrderDetailItemResponse::productName, OrderDetailItemResponse::quantity,
                OrderDetailItemResponse::confirmedQuantity)
            .containsExactly(tuple("대파", 2, 1), tuple("두부", 1, 1));
    }

    @Test
    @DisplayName("결제 대기와 취소된 주문도 상세로 보여 준다")
    void getDetail_pendingAndCancelledOrders_returnsDetail() {
        Order pending = saveOrder(1L, "20261022143000123032");
        Order cancelled = saveOrder(1L, "20261022143000123033");
        cancel(cancelled);

        assertThat(orderQueryService.getDetail(1L, pending.getId()).status()).isEqualTo(OrderStatus.PENDING);
        OrderDetailResponse cancelledDetail = orderQueryService.getDetail(1L, cancelled.getId());
        assertThat(cancelledDetail.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelledDetail.items()).hasSize(2);
    }

    @Test
    @DisplayName("주문 상세에서 남의 주문은 없는 주문과 똑같이 404다")
    void getDetail_othersOrder_returnsNotFound() {
        Order othersOrder = saveOrder(2L, "20261022143000123034");

        assertThatThrownBy(() -> orderQueryService.getDetail(1L, othersOrder.getId()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        assertThatThrownBy(() -> orderQueryService.getDetail(1L, othersOrder.getId() + 1000))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("주문 상세는 주문 상품 수와 상관없이 주문과 주문 상품을 한 번씩 읽는다")
    void getDetail_readsOrderAndItemsOnce() {
        Order order = saveOrder(1L, "20261022143000123035");
        orderItemRepository.save(item(order, 3L, "계란", 1, 6900));
        statistics.clear();

        orderQueryService.getDetail(1L, order.getId());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("주문 목록은 내 결제 대기·결제 완료·구매 확정 주문만 돌려주고 취소된 주문과 남의 주문은 뺀다")
    void getMyOrders_returnsOnlyMyListedStatuses() {
        Order pending = saveOrder(1L, "20261021143000123010");
        Order paid = saveOrder(1L, "20261021143000123011");
        markPaid(paid);
        Order confirmed = saveOrder(1L, "20261021143000123012");
        markPaid(confirmed);
        confirm(confirmed);
        Order cancelled = saveOrder(1L, "20261021143000123013");
        cancel(cancelled);
        saveOrder(2L, "20261021143000123014");

        PageResponse<OrderSummaryResponse> response = orderQueryService.getMyOrders(1L, new OrderListRequest(1, 10));

        assertThat(response.totalElements()).isEqualTo(3L);
        assertThat(response.content())
            .extracting(OrderSummaryResponse::orderId, OrderSummaryResponse::status, OrderSummaryResponse::payAmount)
            .containsExactly(
                tuple(confirmed.getId(), OrderStatus.CONFIRMED, 8000L),
                tuple(paid.getId(), OrderStatus.PAID, 8000L),
                tuple(pending.getId(), OrderStatus.PENDING, 8000L));
    }

    @Test
    @DisplayName("주문 목록은 최신 주문부터 페이지 크기만큼 나누고 1부터 센 페이지 번호로 돌려준다")
    void getMyOrders_pagesNewestFirst() {
        Order first = saveOrder(1L, "20261021143000123020");
        Order second = saveOrder(1L, "20261021143000123021");
        Order third = saveOrder(1L, "20261021143000123022");

        PageResponse<OrderSummaryResponse> firstPage = orderQueryService.getMyOrders(1L, new OrderListRequest(1, 2));
        PageResponse<OrderSummaryResponse> secondPage = orderQueryService.getMyOrders(1L, new OrderListRequest(2, 2));

        assertThat(firstPage.content()).extracting(OrderSummaryResponse::orderId)
            .containsExactly(third.getId(), second.getId());
        assertThat(firstPage.pageNumber()).isEqualTo(1);
        assertThat(firstPage.totalElements()).isEqualTo(3L);
        assertThat(firstPage.totalPages()).isEqualTo(2);
        assertThat(firstPage.isLast()).isFalse();
        assertThat(secondPage.content()).extracting(OrderSummaryResponse::orderId)
            .containsExactly(first.getId());
        assertThat(secondPage.isLast()).isTrue();
    }

    @Test
    @DisplayName("주문이 없으면 빈 목록이다")
    void getMyOrders_noOrders_returnsEmpty() {
        PageResponse<OrderSummaryResponse> response = orderQueryService.getMyOrders(1L, new OrderListRequest(1, 10));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    private void markPaid(Order order) {
        Order found = orderRepository.findById(order.getId()).orElseThrow();
        found.markPaid(LocalDateTime.now());
        orderRepository.save(found);
    }

    private void confirm(Order order) {
        Order found = orderRepository.findById(order.getId()).orElseThrow();
        found.confirm(LocalDateTime.now());
        orderRepository.save(found);
    }

    // 대파에만 완료 환불 수량을 빼고 나머지 주문 상품은 주문 수량 그대로 확정 수량을 저장한다
    private void confirmQuantities(Order order, long completedRefundOfFirstItem) {
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(order.getId());
        items.get(0).confirmQuantity(completedRefundOfFirstItem);
        items.subList(1, items.size()).forEach(item -> item.confirmQuantity(0));
        orderItemRepository.saveAll(items);
    }

    private void cancel(Order order) {
        Order found = orderRepository.findById(order.getId()).orElseThrow();
        found.cancel();
        orderRepository.save(found);
    }

    private Order saveOrder(Long memberId, String orderNumber) {
        Order order = orderRepository.save(Order.builder()
            .orderNumber(orderNumber)
            .memberId(memberId)
            .idempotencyKey("key-" + orderNumber)
            .totalAmount(8000L)
            .build());
        orderItemRepository.save(item(order, 1L, "대파", 2, 3000));
        orderItemRepository.save(item(order, 2L, "두부", 1, 2000));
        return order;
    }

    private OrderItem item(Order order, Long productId, String name, int quantity, int price) {
        return item(order, productId, name, quantity, price, price, SaleType.NONE);
    }

    private OrderItem item(Order order, Long productId, String name, int quantity, int listPrice, int unitPrice,
                           SaleType saleType) {
        return OrderItem.builder()
            .order(order)
            .productId(productId)
            .productName(name)
            .quantity(quantity)
            .listPrice(listPrice)
            .unitPrice(unitPrice)
            .saleType(saleType)
            .build();
    }
}
