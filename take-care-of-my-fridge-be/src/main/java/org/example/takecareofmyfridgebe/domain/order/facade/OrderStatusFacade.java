package org.example.takecareofmyfridgebe.domain.order.facade;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderConfirmResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.service.OrderReader;
import org.example.takecareofmyfridgebe.domain.order.service.OrderStatusService;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.refund.service.RefundQueryService;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropQuantity;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventStockService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 주문 상태 변경. 주문 행을 잠그고 결제·환불을 확인한 뒤, 재고를 되돌리고 OrderStatusService로 상태를 바꾼다.
// 사용자 요청(취소·구매 확정)과 스케줄러(만료·자동 확정), 결제 쪽 스케줄러(결제 쪽 취소)가 부른다.
// 재고 되돌리기와 주문 삭제·취소가 한 트랜잭션이어야 해서 메서드마다 트랜잭션을 연다.
// 잠금 순서는 OrderFacade와 같다. 주문 행 → 상품 행(ID 순) → 번개 드롭 남은 수량 행.
// 상품 행 잠금 읽기는 ProductService에 없어 ProductRepository를 직접 쓴다.
@Component
@RequiredArgsConstructor
public class OrderStatusFacade {

    private final OrderStatusService orderStatusService;
    private final OrderReader orderReader;
    private final ProductRepository productRepository;
    private final SaleEventStockService saleEventStockService;
    private final PaymentService paymentService;
    private final RefundQueryService refundQueryService;

    // 주문 확인 화면의 취소하기와 나가기. 먼저 지운 요청 뒤에 온 요청은 주문을 찾지 못해 404를 받는다.
    @Transactional
    public void cancel(Long memberId, Long orderId) {
        Order order = orderReader.getOwnedOrderForUpdate(memberId, orderId);
        order.validatePending();
        if (paymentService.hasPayment(orderId)) {
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_ATTEMPTED);
        }
        List<OrderItem> items = orderReader.getOrderItems(order);
        restoreStock(items);
        orderStatusService.delete(order, items);
    }

    // 결제 쪽 스케줄러가 부른다. 고른 뒤 잠그기 전에 결제됐거나, 다시 결제를 시작했거나, 이미 취소된 주문은 건너뛴다.
    @Transactional
    public void cancelAbandoned(Long orderId, LocalDateTime lastPaymentBefore) {
        Optional<Order> found = orderReader.findForUpdate(orderId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        if (order.getStatus() != OrderStatus.PENDING || !paymentService.isAbandoned(orderId, lastPaymentBefore)) {
            return;
        }
        List<OrderItem> items = orderReader.getOrderItems(order);
        restoreStock(items);
        orderStatusService.cancel(order, items);
    }

    // 주문 만료 스케줄러가 부른다. 고른 뒤 잠그기 전에 지워졌거나, 결제됐거나, 결제가 붙은 주문은 건너뛴다.
    @Transactional
    public void expire(Long orderId) {
        Optional<Order> found = orderReader.findForUpdate(orderId);
        if (found.isEmpty()) {
            return;
        }
        Order order = found.get();
        if (order.getStatus() != OrderStatus.PENDING || paymentService.hasPayment(orderId)) {
            return;
        }
        List<OrderItem> items = orderReader.getOrderItems(order);
        restoreStock(items);
        orderStatusService.delete(order, items);
    }

    // 주문 상세 화면의 구매 확정과 자동 확정 스케줄러가 부른다. 이미 확정된 주문에는 같은 응답만 돌려준다.
    // 환불 신청도 같은 주문 행을 잠그므로, 잠근 뒤 요청 상태 환불이 없으면 확정이 끝날 때까지 새 환불이 생기지 않는다.
    @Transactional
    public OrderConfirmResponse confirm(Long memberId, Long orderId) {
        Order order = orderReader.getOwnedOrderForUpdate(memberId, orderId);
        if (order.isConfirmed()) {
            return OrderConfirmResponse.from(order);
        }
        if (refundQueryService.hasRequestedRefund(order.getId())) {
            throw new BusinessException(ErrorCode.ORDER_REFUND_IN_PROGRESS);
        }
        if (refundQueryService.isFullyRefunded(order)) {
            throw new BusinessException(ErrorCode.ORDER_FULLY_REFUNDED);
        }
        List<OrderItem> items = orderReader.getOrderItems(order);
        Map<Long, Long> completedRefundQuantities = refundQueryService.getCompletedRefundQuantities(
            items.stream().map(OrderItem::getId).toList());
        return orderStatusService.confirm(order, items, completedRefundQuantities);
    }

    // 주문하기(OrderFacade)와 같은 순서(상품 ID 순)로 상품 행을 잠가 서로 섞이지 않게 한다.
    private Map<Long, Product> findProductsForUpdate(List<Long> productIds) {
        return productRepository.findAllByIdInForUpdate(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    // 결제 전 주문의 재고를 되돌리는 곳은 여기 하나뿐이다. 결제 후 환불은 환불 쪽이 되돌린다.
    // 번개 드롭 남은 수량은 주문하기와 같은 순서(상품 행 다음 남은 수량 행)로 잠근다.
    private void restoreStock(List<OrderItem> orderItems) {
        Map<Long, Product> productsById = findProductsForUpdate(orderItems.stream().map(OrderItem::getProductId).toList());
        orderItems.forEach(item -> productsById.get(item.getProductId()).increaseStock(item.getQuantity()));

        List<OrderItem> flashDropItems = orderItems.stream()
            .filter(item -> item.getSaleType() == SaleType.FLASH_DROP)
            .toList();
        if (flashDropItems.isEmpty()) {
            return;
        }
        saleEventStockService.increaseRemaining(flashDropItems.stream()
            .map(item -> new FlashDropQuantity(item.getSaleEventItemId(), item.getQuantity(), item.getProductName()))
            .toList());
    }
}
