package org.example.takecareofmyfridgebe.domain.refund.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.service.OrderReader;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentChangedBy;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentHistoryRecorder;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundCreateRequest;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundRequested;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundedQuantity;
import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.example.takecareofmyfridgebe.domain.refund.repository.RefundItemRepository;
import org.example.takecareofmyfridgebe.domain.refund.repository.RefundRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 환불 상태를 바꾸고, 완료 시 재고를 되돌리고 결제 이력에 PG 취소를 남긴다. 외부 PG 호출은 하지 않는다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundCommandService {

    private final OrderReader orderReader;
    private final PaymentRepository paymentRepository;
    private final ProductRepository productRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;
    private final EntityManager entityManager;
    private final PaymentHistoryRecorder paymentHistoryRecorder;

    // 주문 행을 잠그고 검증하므로 같은 주문의 환불 신청과 구매 확정이 하나씩 처리돼 누적 수량이 주문 수량을 넘지 않는다.
    // 진행 중인 환불이 있으면 새 신청을 받지 않는다. 한 결제에 PG 취소가 한 건씩만 진행돼야 PG 누적 취소 금액으로 결과를 판단할 수 있다
    @Transactional
    public RefundRequested request(Long memberId, RefundCreateRequest request) {
        Order order = orderReader.getOwnedOrderForUpdate(memberId, request.orderId());
        RefundableOrderPolicy.validateRefundableOrder(order);
        if (refundRepository.existsByOrderIdAndStatus(order.getId(), RefundStatus.REQUESTED)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }
        Payment payment = getPaidPayment(order.getId());

        Map<Long, OrderItem> orderItemsById = orderReader.getOrderItems(order).stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        List<Long> requestedIds = request.items().stream().map(RefundCreateRequest.Item::orderItemId).toList();
        if (requestedIds.stream().distinct().count() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<Long, Long> refundedById = refundItemRepository
                .findRefundedQuantitiesByOrderItemIds(requestedIds, RefundStatus.COUNTED).stream()
                .collect(Collectors.toMap(RefundedQuantity::orderItemId, RefundedQuantity::quantity));

        for (RefundCreateRequest.Item item : request.items()) {
            OrderItem orderItem = orderItemsById.get(item.orderItemId());
            if (orderItem == null) {
                throw new BusinessException(ErrorCode.REFUND_ITEM_NOT_FOUND);
            }
            if (!RefundableOrderPolicy.isRefundableItem(orderItem)) {
                throw new BusinessException(ErrorCode.NON_REFUNDABLE_SALE_ITEM);
            }
            long refunded = refundedById.getOrDefault(orderItem.getId(), 0L);
            if (refunded + item.quantity() > orderItem.getQuantity()) {
                throw new BusinessException(ErrorCode.EXCEED_REFUNDABLE_QUANTITY);
            }
        }

        // RefundItem과 같은 방식(주문 당시 단가 × 수량)으로 합산한다
        long refundAmount = request.items().stream()
                .mapToLong(item -> (long) orderItemsById.get(item.orderItemId()).getUnitPrice() * item.quantity())
                .sum();

        Refund refund = refundRepository.save(Refund.builder()
                .payment(payment)
                .refundAmount(refundAmount)
                .reasonType(request.reasonType())
                .reasonDetail(request.reasonDetail())
                .build());
        refundItemRepository.saveAll(request.items().stream()
                .map(item -> RefundItem.builder()
                        .refund(refund)
                        .orderItem(orderItemsById.get(item.orderItemId()))
                        .quantity(item.quantity())
                        .build())
                .toList());
        return RefundRequested.from(refund);
    }

    // PG 취소가 끝난 환불을 완료하고 환불 수량만큼 재고를 되돌린 뒤 결제 이력에 PG 취소를 남긴다.
    // 신청 요청·웹훅·스케줄러가 동시에 마무리하려 해도 먼저 잠근 쪽만 처리하고, 나머지는 현재 상태를 돌려준다
    @Transactional
    public RefundResponse complete(Long refundId, String portoneCancellationId, PaymentChangedBy changedBy) {
        Refund refund = lockRefund(refundId);
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            log.info("이미 처리된 환불 완료 요청 무시: refundId={}, status={}", refundId, refund.getStatus());
            return RefundResponse.from(refund);
        }
        Payment payment = refund.getPayment();
        long cancelledBefore = knownCancelledAmount(payment.getId());

        refund.complete(portoneCancellationId, LocalDateTime.now());
        restoreStock(refundItemRepository.findWithOrderItemByRefundId(refundId));
        paymentHistoryRecorder.recordCancellation(payment, refund.getRefundAmount(),
                cancelledBefore + refund.getRefundAmount(), refund.getId(), portoneCancellationId, changedBy,
                "환불: " + refund.getReasonType().getDescription());
        return RefundResponse.from(refund);
    }

    // PG가 취소하지 않은 환불을 실패로 바꾼다. 실패한 수량은 다시 신청할 수 있다
    @Transactional
    public RefundResponse fail(Long refundId, String failReason) {
        Refund refund = lockRefund(refundId);
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            log.info("이미 처리된 환불 실패 요청 무시: refundId={}, status={}", refundId, refund.getStatus());
            return RefundResponse.from(refund);
        }
        refund.fail(failReason);
        return RefundResponse.from(refund);
    }

    // 우리 환불 기록에 없는 PG 취소(PortOne 콘솔에서 직접 취소 등)를 결제 이력에 남긴다. 재고는 건드리지 않는다
    @Transactional
    public void recordUnknownCancellation(Long paymentId, long amount, String pgCancellationId,
                                          PaymentChangedBy changedBy) {
        Payment payment = paymentRepository.getReferenceById(paymentId);
        long cancelledBefore = knownCancelledAmount(paymentId);
        paymentHistoryRecorder.recordCancellation(payment, amount, cancelledBefore + amount, null, pgCancellationId,
                changedBy, "환불 기록 없는 PG 취소");
    }

    // 완료된 환불과 환불이 아닌 PG 취소로 이미 기록한 금액의 합
    private long knownCancelledAmount(Long paymentId) {
        return refundRepository.sumAmountByPaymentIdAndStatus(paymentId, RefundStatus.COMPLETED)
                + paymentHistoryRecorder.sumCancelledWithoutRefund(paymentId);
    }

    private Refund lockRefund(Long refundId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        // OSIV로 신청 때 읽은 엔티티가 재사용되면 잠금 후에도 예전 상태가 남으므로 DB 값으로 다시 읽는다
        entityManager.refresh(refund);
        return refund;
    }

    // 주문하기와 같은 순서(상품 ID 순)로 상품 행을 잠가, 동시에 들어온 주문하기의 재고 차감과 섞이지 않게 한다
    private void restoreStock(List<RefundItem> refundItems) {
        Map<Long, Integer> quantitiesByProductId = refundItems.stream()
                .collect(Collectors.toMap(item -> item.getOrderItem().getProductId(), RefundItem::getQuantity,
                        Integer::sum));
        productRepository.findAllByIdInForUpdate(quantitiesByProductId.keySet())
                .forEach(product -> product.increaseStock(quantitiesByProductId.get(product.getId())));
    }

    // 주문 잠금으로 한 주문의 결제 완료는 한 건만 남는다
    private Payment getPaidPayment(Long orderId) {
        return paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.PAID).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFUND_STATUS));
    }
}
