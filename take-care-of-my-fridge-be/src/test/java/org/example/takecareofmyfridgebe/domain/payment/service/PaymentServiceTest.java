package org.example.takecareofmyfridgebe.domain.payment.service;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long ORDER_ID = 100L;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private PaymentService paymentService;

    private Order newOrder() {
        Order order = Order.builder().orderNumber("20261019-000001").memberId(MEMBER_ID).totalAmount(15000L).build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private OrderItem item(Order order, String productName) {
        return OrderItem.builder().order(order).productId(1L).productName(productName).quantity(1)
                .listPrice(1000).unitPrice(1000).saleType(SaleType.NONE).build();
    }

    @Test
    @DisplayName("본인의 결제 대기 주문이면 통과한다")
    void validatePayableOrder_pending_passes() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(newOrder()));

        paymentService.validatePayableOrder(MEMBER_ID, ORDER_ID);
    }

    @Test
    @DisplayName("주문이 없으면 ORDER_NOT_FOUND")
    void validatePayableOrder_notFound() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.validatePayableOrder(MEMBER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 회원의 주문이면 ORDER_ACCESS_DENIED")
    void validatePayableOrder_otherMember_denied() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(newOrder()));

        assertThatThrownBy(() -> paymentService.validatePayableOrder(2L, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("이미 결제됐거나 취소된 주문이면 PAYMENT_ORDER_NOT_PAYABLE")
    void validatePayableOrder_notPending_rejected() {
        Order order = newOrder();
        order.cancel();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.validatePayableOrder(MEMBER_ID, ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
    }

    @Test
    @DisplayName("주문 상품이 하나면 상품명을, 여러 개면 첫 상품명 외 N건을 주문명으로 쓴다")
    void getOrderName() {
        Order order = newOrder();
        given(orderItemRepository.findByOrderIdOrderByIdAsc(ORDER_ID))
                .willReturn(List.of(item(order, "대파")))
                .willReturn(List.of(item(order, "대파"), item(order, "두부"), item(order, "양파")));

        assertThat(paymentService.getOrderName(ORDER_ID)).isEqualTo("대파");
        assertThat(paymentService.getOrderName(ORDER_ID)).isEqualTo("대파 외 2건");
    }

    @Test
    @DisplayName("결제가 없으면 PAYMENT_NOT_FOUND")
    void getWithOrder_notFound() {
        given(paymentRepository.findWithOrderByPortonePaymentId("payment-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getWithOrder("payment-x"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }
}
