package org.example.takecareofmyfridgebe.domain.order.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCancelResponse;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 주문 취소와 재고 되돌리기를 맡는다. 재고를 되돌리는 곳은 이 클래스 하나뿐이다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCancelService {

    private final OrderReader orderReader;
    private final ProductRepository productRepository;

    // 주문 행을 잠그고 상태를 확인하므로 같은 주문의 취소가 동시에 와도 하나씩 순서대로 처리된다.
    // 결제 대기에서 취소로 바꾼 요청만 재고를 되돌리고, 뒤에 온 요청은 이미 취소된 주문을 보고 같은 응답만 돌려준다.
    @Transactional
    public OrderCancelResponse cancel(Long memberId, Long orderId) {
        Order order = orderReader.getOwnedOrderForUpdate(memberId, orderId);
        if (order.isCancelled()) {
            return OrderCancelResponse.from(order);
        }
        order.cancel();
        restoreStock(order);
        return OrderCancelResponse.from(order);
    }

    // 주문하기와 같은 순서(상품 ID 순)로 상품 행을 잠가, 동시에 들어온 주문하기의 재고 차감과 섞이지 않게 한다
    private void restoreStock(Order order) {
        List<OrderItem> orderItems = orderReader.getOrderItems(order);
        List<Long> productIds = orderItems.stream().map(OrderItem::getProductId).toList();
        Map<Long, Product> productsById = productRepository.findAllByIdInForUpdate(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        orderItems.forEach(item -> {
            productsById.get(item.getProductId()).increaseStock(item.getQuantity());
            item.releaseFlashDropMember();
        });
    }
}
