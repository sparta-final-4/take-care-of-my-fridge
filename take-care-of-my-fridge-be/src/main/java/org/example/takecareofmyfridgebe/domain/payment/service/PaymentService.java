package org.example.takecareofmyfridgebe.domain.payment.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentEventType;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    // 결제를 시작할 수 있는 주문인지 먼저 확인한다. 상태는 결제를 만들 때 주문을 잠그고 다시 확인한다
    public void validatePayableOrder(Long memberId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.getMemberId().equals(memberId)) {
            throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }
    }

    // 주문에 남아 있는 결제 대기 건. 정상이면 최대 한 건이다
    public List<Payment> findReady(Long orderId) {
        return paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.READY);
    }

    // PG 결제창에 보일 주문명 (예: "대파 외 2건")
    public String getOrderName(Long orderId) {
        List<OrderItem> items = orderItemRepository.findByOrderIdOrderByIdAsc(orderId);
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        String first = items.get(0).getProductName();
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1) + "건";
    }

    public Payment getWithOrder(String portonePaymentId) {
        return paymentRepository.findWithOrderByPortonePaymentId(portonePaymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    // 결제 시도가 한 번이라도 있었는지 본다. 상태와 상관없이 결제 행이 있으면 true다
    public boolean hasPayment(Long orderId) {
        return paymentRepository.existsByOrderId(orderId);
    }

    // 결제를 시도했지만 끝내지 않은 주문인지 본다. 결제가 있고, 진행 중 결제가 없고, 마지막 결제가 기준보다 이르면 true다
    public boolean isAbandoned(Long orderId, LocalDateTime lastPaymentBefore) {
        return paymentRepository.existsByOrderId(orderId)
                && !paymentRepository.existsByOrderIdAndStatus(orderId, PaymentStatus.READY)
                && !paymentRepository.existsByOrderIdAndCreatedAtGreaterThanEqual(orderId, lastPaymentBefore);
    }

    // 스케줄러가 취소할 결제 대기 주문을 afterId 다음부터 주문 id 순으로 조회한다
    public List<Long> findAbandonedOrderIds(LocalDateTime lastPaymentBefore, long afterId, int limit) {
        return paymentRepository.findAbandonedOrderIds(OrderStatus.PENDING, PaymentStatus.READY, lastPaymentBefore,
                afterId, PageRequest.of(0, limit));
    }

    // 스케줄러가 만료 처리할 결제 대기 건을 afterId 다음부터 id 순으로 조회한다
    public List<Payment> findExpiredReady(LocalDateTime now, long afterId, int limit) {
        return paymentRepository.findExpired(PaymentStatus.READY, now, afterId, PageRequest.of(0, limit));
    }

    // 스케줄러가 보상 취소를 다시 시도할 결제를 afterId 다음부터 id 순으로 조회한다
    public List<Payment> findCompensationPending(long afterId, int limit) {
        return paymentRepository.findCompensationPending(PaymentStatus.FAILED, PaymentEventType.PAID,
                PaymentEventType.CANCELLED, afterId, PageRequest.of(0, limit));
    }

    // 웹훅처럼 결제가 없을 수도 있는 요청에서 쓴다
    public Optional<Payment> findWithOrder(String portonePaymentId) {
        return paymentRepository.findWithOrderByPortonePaymentId(portonePaymentId);
    }
}
