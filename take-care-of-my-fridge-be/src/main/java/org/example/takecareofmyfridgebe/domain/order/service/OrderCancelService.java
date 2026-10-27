package org.example.takecareofmyfridgebe.domain.order.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.payment.service.PaymentService;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropQuantity;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventStockService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 주문 취소와 재고 되돌리기를 맡는다. 결제 전 주문의 재고를 되돌리는 곳은 이 클래스 하나뿐이다. 결제 후 환불은 환불 쪽이 되돌린다.
// 결제를 시도하지 않은 결제 대기 주문은 재고를 되돌린 뒤 지운다. 결제를 시도한 주문은 결제 쪽 스케줄러가 cancelAbandoned를 불러
// 취소 상태로 남긴다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCancelService {

    private final OrderReader orderReader;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final SaleEventStockService saleEventStockService;
    private final PaymentService paymentService;

    // 주문 행을 잠그고 확인하므로 같은 주문의 취소·결제하기·주문 만료·결제 쪽 정리가 동시에 와도 하나씩 순서대로 처리된다.
    // 먼저 지운 요청 뒤에 온 요청은 주문을 찾지 못해 404를 받는다.
    // 잠금이 트랜잭션의 첫 조회라, 결제 행 확인은 잠금을 얻은 뒤의 커밋까지 본다.
    @Transactional
    public void cancel(Long memberId, Long orderId) {
        Order order = orderReader.getOwnedOrderForUpdate(memberId, orderId);
        order.validatePending();
        if (paymentService.hasPayment(orderId)) {
            throw new BusinessException(ErrorCode.ORDER_PAYMENT_ATTEMPTED);
        }
        delete(order);
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
        delete(order);
    }

    // 결제를 시도했지만 끝내지 않은 주문을 취소 상태로 남기고 재고를 되돌린다. 결제 행이 주문을 참조해 지우지 않는다.
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
        order.cancel();
        restoreStock(orderReader.getOrderItems(order));
    }

    // 주문 만료 스케줄러가 처리할 결제 대기 주문을 afterId 다음부터 id 순으로 조회한다.
    // 결제가 붙었는지는 여기서 거르지 않고 expire에서 주문 행을 잠근 뒤 본다.
    public List<Long> findExpiredOrderIds(LocalDateTime createdBefore, long afterId, int limit) {
        return orderRepository.findIdsCreatedBefore(OrderStatus.PENDING, createdBefore, afterId, PageRequest.of(0, limit));
    }

    private void delete(Order order) {
        List<OrderItem> orderItems = orderReader.getOrderItems(order);
        restoreStock(orderItems);
        orderItemRepository.deleteAll(orderItems);
        orderRepository.delete(order);
    }

    // 주문하기와 같은 순서(상품 ID 순)로 상품 행을 잠가, 동시에 들어온 주문하기의 재고 차감과 섞이지 않게 한다.
    // 주문 상품 행을 지우는 경우에는 1인 1개 기록도 행과 함께 사라진다
    private void restoreStock(List<OrderItem> orderItems) {
        List<Long> productIds = orderItems.stream().map(OrderItem::getProductId).toList();
        Map<Long, Product> productsById = productRepository.findAllByIdInForUpdate(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        orderItems.forEach(item -> {
            productsById.get(item.getProductId()).increaseStock(item.getQuantity());
            item.releaseFlashDropMember();
        });
        restoreFlashDropRemaining(orderItems);
    }

    // 번개 드롭 남은 수량도 되돌린다. 주문하기와 같은 순서(상품 행 다음 남은 수량 행)로 잠근다.
    private void restoreFlashDropRemaining(List<OrderItem> orderItems) {
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
