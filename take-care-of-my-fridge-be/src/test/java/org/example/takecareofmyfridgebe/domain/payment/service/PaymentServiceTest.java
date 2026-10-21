package org.example.takecareofmyfridgebe.domain.payment.service;

import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("주문 금액으로 READY 결제를 만든다")
    void createPayment_usesOrderAmount() {
        Order order = Order.builder().orderNumber("20261019-000001").memberId(1L).totalAmount(15000L).build();
        given(paymentRepository.save(any(Payment.class))).willAnswer(returnsFirstArg());

        LocalDateTime before = LocalDateTime.now();
        Payment payment = paymentService.createPayment(order);
        LocalDateTime after = LocalDateTime.now();

        assertThat(payment.getExpiresAt()).isBetween(before.plusMinutes(10), after.plusMinutes(10));
        assertThat(payment.getOrder()).isSameAs(order);
        assertThat(payment.getAmount()).isEqualTo(15000L);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getPortonePaymentId()).startsWith("payment-");
    }

    @Test
    @DisplayName("결제마다 다른 PortOne 결제 ID를 만든다")
    void createPayment_generatesUniqueId() {
        Order order = Order.builder().orderNumber("20261019-000001").memberId(1L).totalAmount(15000L).build();
        given(paymentRepository.save(any(Payment.class))).willAnswer(returnsFirstArg());

        Payment first = paymentService.createPayment(order);
        Payment second = paymentService.createPayment(order);

        assertThat(first.getPortonePaymentId()).isNotEqualTo(second.getPortonePaymentId());
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
