package org.example.takecareofmyfridgebe.domain.payment.service;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.payment.dto.PaymentConfirmResponse;
import org.example.takecareofmyfridgebe.domain.payment.entity.FailReason;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    private static final String PORTONE_PAYMENT_ID = "payment-1";

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PaymentCommandService paymentCommandService;

    private Payment newPayment() {
        Order order = Order.builder().orderNumber("20261019-000001").memberId(1L).totalAmount(10000L).build();
        return Payment.builder().order(order).portonePaymentId(PORTONE_PAYMENT_ID).amount(10000L).build();
    }

    private void givenLockedPayment(Payment payment) {
        given(paymentRepository.findWithOrderByPortonePaymentIdForUpdate(PORTONE_PAYMENT_ID))
                .willReturn(Optional.of(payment));
    }

    @Test
    @DisplayName("승인하면 결제와 주문이 함께 PAID가 된다")
    void approve_marksPaymentAndOrderPaid() {
        Payment payment = newPayment();
        givenLockedPayment(payment);

        PaymentConfirmResponse response = paymentCommandService.approve(PORTONE_PAYMENT_ID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(payment.getOrder().getPaidAt()).isEqualTo(payment.getPaidAt());
        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("이미 PAID면 상태를 바꾸지 않고 같은 결과를 돌려준다")
    void approve_alreadyPaid_returnsSameResult() {
        Payment payment = newPayment();
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 19, 10, 0);
        payment.complete(paidAt);
        payment.getOrder().markPaid(paidAt);
        givenLockedPayment(payment);

        PaymentConfirmResponse response = paymentCommandService.approve(PORTONE_PAYMENT_ID);

        assertThat(response.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.paidAt()).isEqualTo(paidAt);
    }

    @Test
    @DisplayName("이미 FAILED면 ALREADY_PROCESSED_PAYMENT")
    void approve_alreadyFailed_throws() {
        Payment payment = newPayment();
        payment.fail(FailReason.TIMEOUT);
        givenLockedPayment(payment);

        assertThatThrownBy(() -> paymentCommandService.approve(PORTONE_PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ALREADY_PROCESSED_PAYMENT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("결제가 없으면 PAYMENT_NOT_FOUND")
    void approve_notFound_throws() {
        given(paymentRepository.findWithOrderByPortonePaymentIdForUpdate(PORTONE_PAYMENT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentCommandService.approve(PORTONE_PAYMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("실패 처리하면 결제는 FAILED, 주문은 CANCELLED가 된다")
    void fail_marksPaymentFailedAndCancelsOrder() {
        Payment payment = newPayment();
        givenLockedPayment(payment);

        boolean failed = paymentCommandService.fail(PORTONE_PAYMENT_ID, FailReason.USER_CANCELLED);

        assertThat(failed).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailReason()).isEqualTo(FailReason.USER_CANCELLED);
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 처리된 결제는 실패 처리 요청을 무시한다")
    void fail_alreadyProcessed_ignored() {
        Payment payment = newPayment();
        LocalDateTime paidAt = LocalDateTime.of(2026, 10, 19, 10, 0);
        payment.complete(paidAt);
        payment.getOrder().markPaid(paidAt);
        givenLockedPayment(payment);

        boolean failed = paymentCommandService.fail(PORTONE_PAYMENT_ID, FailReason.TIMEOUT);

        assertThat(failed).isFalse();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getFailReason()).isNull();
        assertThat(payment.getOrder().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("잠금 조회 뒤 미반영 변경을 flush하고 결제와 주문을 DB 값으로 다시 읽는다")
    void lockedPayment_isRefreshedAfterFlush() {
        Payment payment = newPayment();
        givenLockedPayment(payment);

        paymentCommandService.fail(PORTONE_PAYMENT_ID, FailReason.USER_CANCELLED);

        InOrder inOrder = inOrder(paymentRepository, entityManager);
        inOrder.verify(paymentRepository).findWithOrderByPortonePaymentIdForUpdate(PORTONE_PAYMENT_ID);
        inOrder.verify(entityManager).flush();
        inOrder.verify(entityManager).refresh(payment);
        inOrder.verify(entityManager).refresh(payment.getOrder());
    }
}
