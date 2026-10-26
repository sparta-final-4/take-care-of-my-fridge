package org.example.takecareofmyfridgebe.domain.payment.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentApproveResult;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentApproveResult.Rejection;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    private static final Long ORDER_ID = 100L;
    private static final String PORTONE_PAYMENT_ID = "payment-1";
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 19, 10, 0);

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private PaymentHistoryRecorder paymentHistoryRecorder;

    @InjectMocks
    private PaymentCommandService paymentCommandService;

    private Order newOrder() {
        Order order = Order.builder().orderNumber("20261019-000001").memberId(1L).totalAmount(10000L).build();
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        return order;
    }

    private Payment newPayment(Order order) {
        return Payment.builder().order(order).portonePaymentId(PORTONE_PAYMENT_ID).amount(10000L)
                .expiresAt(LocalDateTime.now().plusMinutes(10)).build();
    }

    private Payment givenLockedPayment() {
        Order order = newOrder();
        Payment payment = newPayment(order);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.findWithOrderByPortonePaymentIdForUpdate(PORTONE_PAYMENT_ID))
                .willReturn(Optional.of(payment));
        return payment;
    }

    // approve

    @Test
    @DisplayName("승인하면 결제와 주문이 함께 PAID가 된다")
    void approve_marksPaymentAndOrderPaid() {
        Payment payment = givenLockedPayment();

        PaymentApproveResult result = paymentCommandService.approve(payment, PaymentChangedBy.USER);

        assertThat(result.isApproved()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getOrder().getPaidAt()).isEqualTo(payment.getPaidAt());
        assertThat(result.response().status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("이미 PAID면 상태를 바꾸지 않고 같은 결과를 돌려준다")
    void approve_alreadyPaid_returnsSameResult() {
        Payment payment = givenLockedPayment();
        payment.complete(PAID_AT);
        payment.getOrder().markPaid(PAID_AT);

        PaymentApproveResult result = paymentCommandService.approve(payment, PaymentChangedBy.USER);

        assertThat(result.isApproved()).isTrue();
        assertThat(result.response().status()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.response().paidAt()).isEqualTo(PAID_AT);
    }

    @Test
    @DisplayName("이미 FAILED면 상태를 바꾸지 않고 PAYMENT_ALREADY_FAILED로 거절한다")
    void approve_alreadyFailed_rejected() {
        Payment payment = givenLockedPayment();
        payment.fail(FailReason.TIMEOUT);

        PaymentApproveResult result = paymentCommandService.approve(payment, PaymentChangedBy.USER);

        assertThat(result.isApproved()).isFalse();
        assertThat(result.rejection()).isEqualTo(Rejection.PAYMENT_ALREADY_FAILED);
        assertThat(payment.getFailReason()).isEqualTo(FailReason.TIMEOUT);
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("같은 주문의 다른 결제가 먼저 승인됐으면 이 결제를 ORDER_NOT_PAYABLE로 실패 처리하고 거절한다")
    void approve_orderAlreadyPaid_rejectedAsOrderNotPayable() {
        Payment payment = givenLockedPayment();
        payment.getOrder().markPaid(PAID_AT);

        PaymentApproveResult result = paymentCommandService.approve(payment, PaymentChangedBy.USER);

        assertThat(result.rejection()).isEqualTo(Rejection.ORDER_NOT_PAYABLE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailReason()).isEqualTo(FailReason.ORDER_NOT_PAYABLE);
        assertThat(payment.getOrder().getPaidAt()).isEqualTo(PAID_AT);
    }

    @Test
    @DisplayName("주문이 취소됐으면 이 결제를 ORDER_NOT_PAYABLE로 실패 처리하고 거절한다")
    void approve_orderCancelled_rejectedAsOrderNotPayable() {
        Payment payment = givenLockedPayment();
        payment.getOrder().cancel();

        PaymentApproveResult result = paymentCommandService.approve(payment, PaymentChangedBy.USER);

        assertThat(result.rejection()).isEqualTo(Rejection.ORDER_NOT_PAYABLE);
        assertThat(payment.getFailReason()).isEqualTo(FailReason.ORDER_NOT_PAYABLE);
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("결제가 없으면 PAYMENT_NOT_FOUND")
    void approve_notFound_throws() {
        Payment payment = newPayment(newOrder());
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment.getOrder()));
        given(paymentRepository.findWithOrderByPortonePaymentIdForUpdate(PORTONE_PAYMENT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentCommandService.approve(payment, PaymentChangedBy.USER))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    // fail

    @Test
    @DisplayName("실패 처리하면 결제만 FAILED가 되고 주문은 다시 결제할 수 있게 PENDING으로 남는다")
    void fail_marksPaymentFailedAndKeepsOrderPending() {
        Payment payment = givenLockedPayment();

        boolean failed = paymentCommandService.fail(payment, FailReason.USER_CANCELLED, PaymentChangedBy.USER);

        assertThat(failed).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailReason()).isEqualTo(FailReason.USER_CANCELLED);
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("이미 처리된 결제는 실패 처리 요청을 무시한다")
    void fail_alreadyProcessed_ignored() {
        Payment payment = givenLockedPayment();
        payment.complete(PAID_AT);
        payment.getOrder().markPaid(PAID_AT);

        boolean failed = paymentCommandService.fail(payment, FailReason.TIMEOUT, PaymentChangedBy.USER);

        assertThat(failed).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getFailReason()).isNull();
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("주문을 먼저 잠그고 flush·refresh한 뒤 결제를 잠그고 refresh한다")
    void locksOrderBeforePayment() {
        Payment payment = givenLockedPayment();

        paymentCommandService.fail(payment, FailReason.USER_CANCELLED, PaymentChangedBy.USER);

        InOrder inOrder = inOrder(orderRepository, paymentRepository, entityManager);
        inOrder.verify(orderRepository).findByIdForUpdate(ORDER_ID);
        inOrder.verify(entityManager).flush();
        inOrder.verify(entityManager).refresh(payment.getOrder());
        inOrder.verify(paymentRepository).findWithOrderByPortonePaymentIdForUpdate(PORTONE_PAYMENT_ID);
        inOrder.verify(entityManager).refresh(payment);
    }

    // create

    @Test
    @DisplayName("주문 금액과 10분 뒤 만료 시각으로 READY 결제를 만든다")
    void create_usesOrderAmount() {
        Order order = newOrder();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndStatus(ORDER_ID, PaymentStatus.READY)).willReturn(List.of());
        given(paymentRepository.save(any(Payment.class))).willAnswer(returnsFirstArg());

        LocalDateTime before = LocalDateTime.now();
        Payment payment = paymentCommandService.create(ORDER_ID);
        LocalDateTime after = LocalDateTime.now();

        assertThat(payment.getOrder()).isSameAs(order);
        assertThat(payment.getAmount()).isEqualTo(10000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getPortonePaymentId()).startsWith("payment-");
        // KG이니시스 주문번호(oid) 최대 길이
        assertThat(payment.getPortonePaymentId()).hasSizeLessThanOrEqualTo(40);
        assertThat(payment.getExpiresAt()).isBetween(before.plusMinutes(10), after.plusMinutes(10));
    }

    @Test
    @DisplayName("결제마다 다른 PortOne 결제 ID를 만든다")
    void create_generatesUniqueId() {
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(newOrder()));
        given(paymentRepository.findByOrderIdAndStatus(ORDER_ID, PaymentStatus.READY)).willReturn(List.of());
        given(paymentRepository.save(any(Payment.class))).willAnswer(returnsFirstArg());

        Payment first = paymentCommandService.create(ORDER_ID);
        Payment second = paymentCommandService.create(ORDER_ID);

        assertThat(first.getPortonePaymentId()).isNotEqualTo(second.getPortonePaymentId());
    }

    @Test
    @DisplayName("동시에 들어온 요청이 먼저 만든 결제 대기 건이 있으면 재사용하지 않고 PAYMENT_IN_PROGRESS로 거절한다")
    void create_readyExists_rejected() {
        Order order = newOrder();
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));
        given(paymentRepository.findByOrderIdAndStatus(ORDER_ID, PaymentStatus.READY))
                .willReturn(List.of(newPayment(order)));

        assertThatThrownBy(() -> paymentCommandService.create(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_IN_PROGRESS);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문이 PENDING이 아니면 PAYMENT_ORDER_NOT_PAYABLE, 결제를 만들지 않는다")
    void create_orderNotPending_rejected() {
        Order order = newOrder();
        order.markPaid(PAID_AT);
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentCommandService.create(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문이 없으면 ORDER_NOT_FOUND")
    void create_orderNotFound() {
        given(orderRepository.findByIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentCommandService.create(ORDER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }
}
