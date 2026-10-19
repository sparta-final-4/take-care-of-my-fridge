package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private static final String PORTONE_PAYMENT_ID_PREFIX = "payment-";

    private final PaymentRepository paymentRepository;

    // 주문 생성 흐름에서 호출한다. 금액은 클라이언트 값이 아닌 주문 금액을 사용한다.
    @Transactional
    public Payment createPayment(Order order) {
        Payment payment = Payment.builder()
                .order(order)
                .portonePaymentId(PORTONE_PAYMENT_ID_PREFIX + UUID.randomUUID())
                .amount(order.getTotalAmount())
                .build();
        return paymentRepository.save(payment);
    }

    public Payment getWithOrder(String portonePaymentId) {
        return paymentRepository.findWithOrderByPortonePaymentId(portonePaymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }
}
