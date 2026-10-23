package org.example.takecareofmyfridgebe.domain.refund.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.service.OrderReader;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundHistoryResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundableItemResponse;
import org.example.takecareofmyfridgebe.domain.refund.dto.RefundedQuantity;
import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.example.takecareofmyfridgebe.domain.refund.entity.RefundStatus;
import org.example.takecareofmyfridgebe.domain.refund.repository.RefundItemRepository;
import org.example.takecareofmyfridgebe.domain.refund.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// 환불 화면에 필요한 조회. 주문을 잠그지 않으므로 신청 시점의 최종 검증은 RefundCommandService가 한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundQueryService {

    private final OrderReader orderReader;
    private final RefundRepository refundRepository;
    private final RefundItemRepository refundItemRepository;

    // 환불 신청 화면. 주문 상품마다 남은 환불 가능 수량을 주문 상품 순서대로 돌려준다
    public List<RefundableItemResponse> getRefundableItems(Long memberId, Long orderId) {
        Order order = orderReader.getOwnedOrder(memberId, orderId);
        RefundableOrderPolicy.validateRefundableOrder(order);

        List<OrderItem> orderItems = orderReader.getOrderItems(order);
        Map<Long, Long> refundedById = refundItemRepository
                .findRefundedQuantitiesByOrderItemIds(orderItems.stream().map(OrderItem::getId).toList(),
                        RefundStatus.COUNTED).stream()
                .collect(Collectors.toMap(RefundedQuantity::orderItemId, RefundedQuantity::quantity));

        return orderItems.stream()
                .map(item -> RefundableItemResponse.of(item,
                        refundedById.getOrDefault(item.getId(), 0L).intValue(),
                        RefundableOrderPolicy.isRefundableItem(item)))
                .toList();
    }

    // 환불 상세 화면. 주문의 환불을 상태와 관계없이 최신순으로 돌려준다. 구매 확정된 주문의 지난 환불도 보여 준다
    public List<RefundHistoryResponse> getRefundHistory(Long memberId, Long orderId) {
        orderReader.getOwnedOrder(memberId, orderId);

        List<Refund> refunds = refundRepository.findByOrderIdOrderByIdDesc(orderId);
        if (refunds.isEmpty()) {
            return List.of();
        }
        Map<Long, List<RefundItem>> itemsByRefundId = refundItemRepository
                .findWithOrderItemByRefundIdIn(refunds.stream().map(Refund::getId).toList()).stream()
                .collect(Collectors.groupingBy(item -> item.getRefund().getId()));

        return refunds.stream()
                .map(refund -> RefundHistoryResponse.from(refund,
                        itemsByRefundId.getOrDefault(refund.getId(), List.of())))
                .toList();
    }
}
