package org.example.takecareofmyfridgebe.domain.refund.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.service.OrderReader;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundCreateRequest;
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

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// 환불 신청을 검증하고 요청 상태로 저장한다. PG 취소는 하지 않는다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundCommandService {

    // 처리 중이거나 끝난 환불의 수량만 누적에 넣는다. 실패한 환불 수량은 다시 신청할 수 있다
    private static final List<RefundStatus> COUNTED_STATUSES = List.of(RefundStatus.REQUESTED, RefundStatus.COMPLETED);

    private final OrderReader orderReader;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;

    // 주문 행을 잠그고 검증하므로 같은 주문의 환불 신청과 구매 확정이 하나씩 처리돼 누적 수량이 주문 수량을 넘지 않는다
    @Transactional
    public Long request(Long memberId, Long orderId, RefundCreateRequest request) {
        Order order = orderReader.getOwnedOrderForUpdate(memberId, orderId);
        validateRefundableOrder(order);
        Payment payment = getPaidPayment(orderId);

        Map<Long, OrderItem> orderItemsById = orderReader.getOrderItems(order).stream()
                .collect(Collectors.toMap(OrderItem::getId, Function.identity()));
        List<Long> requestedIds = request.items().stream().map(RefundCreateRequest.Item::orderItemId).toList();
        if (requestedIds.stream().distinct().count() != requestedIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        Map<Long, Long> refundedById = refundItemRepository
                .findRefundedQuantitiesByOrderItemIds(requestedIds, COUNTED_STATUSES).stream()
                .collect(Collectors.toMap(RefundedQuantity::orderItemId, RefundedQuantity::quantity));

        for (RefundCreateRequest.Item item : request.items()) {
            OrderItem orderItem = orderItemsById.get(item.orderItemId());
            if (orderItem == null) {
                throw new BusinessException(ErrorCode.REFUND_ITEM_NOT_FOUND);
            }
            if (orderItem.getSaleType() != SaleType.NONE) {
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
        return refund.getId();
    }

    private void validateRefundableOrder(Order order) {
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.REFUND_ORDER_CONFIRMED);
        }
        if (order.getStatus() != OrderStatus.PAID) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_STATUS);
        }
    }

    // 주문 잠금으로 한 주문의 결제 완료는 한 건만 남는다
    private Payment getPaidPayment(Long orderId) {
        return paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.PAID).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFUND_STATUS));
    }
}
