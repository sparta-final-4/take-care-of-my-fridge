package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private static final String PORTONE_PAYMENT_ID_PREFIX = "payment-";
    private static final long PAYMENT_EXPIRE_MINUTES = 10;

    private final PaymentRepository paymentRepository;

    // 주문 생성 흐름에서 호출한다. 금액은 클라이언트 값이 아닌 주문 금액을 사용한다.
    @Transactional
    public Payment createPayment(Order order) {
        Payment payment = Payment.builder()
                .order(order)
                .portonePaymentId(PORTONE_PAYMENT_ID_PREFIX + UUID.randomUUID())
                .amount(order.getTotalAmount())
                .expiresAt(LocalDateTime.now().plusMinutes(PAYMENT_EXPIRE_MINUTES))
                .build();
        return paymentRepository.save(payment);
    }

    public Payment getWithOrder(String portonePaymentId) {
        return paymentRepository.findWithOrderByPortonePaymentId(portonePaymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    // 스케줄러가 만료 처리할 결제 대기 건을 afterId 다음부터 id 순으로 조회한다
    public List<Payment> findExpiredReady(LocalDateTime now, long afterId, int limit) {
        return paymentRepository.findExpired(PaymentStatus.READY, now, afterId, PageRequest.of(0, limit));
    }

    // 웹훅처럼 결제가 없을 수도 있는 요청에서 쓴다
    public Optional<Payment> findWithOrder(String portonePaymentId) {
        return paymentRepository.findWithOrderByPortonePaymentId(portonePaymentId);
    }
}
