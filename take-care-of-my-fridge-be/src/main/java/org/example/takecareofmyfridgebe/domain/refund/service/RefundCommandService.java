package org.example.takecareofmyfridgebe.domain.refund.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.service.OrderReader;
import org.example.takecareofmyfridgebe.domain.payment.entity.Payment;
import org.example.takecareofmyfridgebe.domain.payment.entity.PaymentStatus;
import org.example.takecareofmyfridgebe.domain.payment.repository.PaymentRepository;
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

// 환불 상태를 바꾸고, 완료 시 재고를 되돌린다. 외부 PG 호출은 하지 않는다.
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

    // 주문 행을 잠그고 검증하므로 같은 주문의 환불 신청과 구매 확정이 하나씩 처리돼 누적 수량이 주문 수량을 넘지 않는다
    @Transactional
    public RefundRequested request(Long memberId, RefundCreateRequest request) {
        Order order = orderReader.getOwnedOrderForUpdate(memberId, request.orderId());
        RefundableOrderPolicy.validateRefundableOrder(order);
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

    // PG 취소가 끝난 환불을 완료하고 환불 수량만큼 재고를 되돌린다
    @Transactional
    public RefundResponse complete(Long refundId, String portoneCancellationId) {
        Refund refund = refundRepository.findByIdForUpdate(refundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_NOT_FOUND));
        // OSIV로 신청 때 읽은 엔티티가 재사용되면 잠금 후에도 예전 상태가 남으므로 DB 값으로 다시 읽는다
        entityManager.refresh(refund);
        refund.complete(portoneCancellationId, LocalDateTime.now());
        restoreStock(refundItemRepository.findWithOrderItemByRefundId(refundId));
        return RefundResponse.from(refund);
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
