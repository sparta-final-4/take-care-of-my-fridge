package org.example.takecareofmyfridgebe.domain.refund.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.service.OrderReader;
import org.example.takecareofmyfridgebe.domain.order.service.OrderStatusService;
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

// 환불 상태를 바꾸고, 완료 시 재고를 되돌리고 결제 이력에 PG 취소를 남긴 뒤 주문 상태를 맞춘다. 외부 PG 호출은 하지 않는다.
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
    private final RefundQueryService refundQueryService;
    private final OrderStatusService orderStatusService;

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
    // 신청 요청·웹훅·스케줄러가 동시에 마무리하려 해도 먼저 잠근 쪽만 처리하고, 나머지는 현재 상태를 돌려준다.
    // 환불 행 → 주문 행 순서로 잠근다. 주문 행을 먼저 잠그는 환불 신청·구매 확정은 이미 있는 환불 행을 잠그지 않아 서로 기다리지 않는다.
    // 이미 처리된 환불이면 주문 행은 잠그지 않는다
    @Transactional
    public RefundResponse complete(Long refundId, String portoneCancellationId, PaymentChangedBy changedBy) {
        Refund refund = lockRefund(refundId);
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            log.info("이미 처리된 환불 완료 요청 무시: refundId={}, status={}", refundId, refund.getStatus());
            return RefundResponse.from(refund);
        }
        Order order = lockOrder(refund);
        completeRefund(refund, portoneCancellationId, changedBy);
        changeOrderStatus(order);
        return RefundResponse.from(refund);
    }

    // complete가 실패했을 때 RefundFacade가 새 트랜잭션으로 부른다. PG에서 이미 돈이 돌아갔으므로
    // 주문 상태와 관계없이 환불 완료·재고·결제 이력부터 남긴다. 주문 상태는 환불 정리 스케줄러가 맞춘다
    @Transactional
    public RefundResponse completeWithoutOrderStatus(Long refundId, String portoneCancellationId,
                                                     PaymentChangedBy changedBy) {
        Refund refund = lockRefund(refundId);
        if (refund.getStatus() != RefundStatus.REQUESTED) {
            log.info("이미 처리된 환불 완료 요청 무시: refundId={}, status={}", refundId, refund.getStatus());
            return RefundResponse.from(refund);
        }
        completeRefund(refund, portoneCancellationId, changedBy);
        return RefundResponse.from(refund);
    }

    // 요청 상태 환불을 완료하고 재고와 결제 이력을 남긴다. 부르는 쪽이 환불 행을 잠그고 요청 상태인지 확인한 뒤 부른다
    private void completeRefund(Refund refund, String portoneCancellationId, PaymentChangedBy changedBy) {
        Payment payment = refund.getPayment();
        long cancelledBefore = knownCancelledAmount(payment.getId());

        refund.complete(portoneCancellationId, LocalDateTime.now());
        restoreStock(refundItemRepository.findWithOrderItemByRefundId(refund.getId()));
        paymentHistoryRecorder.recordCancellation(payment, refund.getRefundAmount(),
                cancelledBefore + refund.getRefundAmount(), refund.getId(), portoneCancellationId, changedBy,
                "환불: " + refund.getReasonType().getDescription());
    }

    // 환불할 수 있는 상품을 모두 환불했으면 주문을 전액 환불이나 구매 확정으로 바꾼다. 부분 환불이면 그대로 둔다.
    // 환불 완료와 한 트랜잭션이라, 전부 환불됐는데 주문이 결제 완료로 남는 순간이 없다.
    // 주문이 결제 완료가 아니거나 DB가 주문 상태를 받지 않으면 환불 완료까지 되돌려진다.
    // 그때는 RefundFacade가 completeWithoutOrderStatus로 환불만 다시 완료한다.
    // 주문 행은 이미 잠갔으므로 주문 상태 서비스에 잠근 주문과 계산한 환불 수량을 그대로 넘긴다
    private void changeOrderStatus(Order order) {
        List<OrderItem> orderItems = orderReader.getOrderItems(order);
        Map<Long, Long> completedById = refundQueryService.getCompletedRefundQuantities(
                orderItems.stream().map(OrderItem::getId).toList());
        RefundableOrderPolicy.statusAfterRefund(orderItems, completedById).ifPresent(status -> {
            if (status == OrderStatus.REFUNDED) {
                orderStatusService.markRefunded(order);
            } else {
                orderStatusService.confirm(order, orderItems, completedById);
            }
        });
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

    // 환불이 속한 주문을 잠그면서 최신 값으로 읽는다. 일반 refresh는 이 트랜잭션의 첫 일반 조회 시점 스냅샷을 읽어
    // 그 뒤 다른 트랜잭션이 바꾼 주문 상태를 놓치므로, 잠금 조회로 다시 읽는다
    private Order lockOrder(Refund refund) {
        Order order = refund.getPayment().getOrder();
        entityManager.refresh(order, LockModeType.PESSIMISTIC_WRITE);
        return order;
    }

    private Refund lockRefund(Long refundId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        // OSIV로 신청 때 읽은 엔티티가 재사용되면 잠금 후에도 예전 상태가 남으므로 DB 값으로 다시 읽는다.
        // 잠금 조회가 이 트랜잭션의 첫 조회라, 이 refresh의 스냅샷은 잠금을 얻은 뒤의 값이다
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
