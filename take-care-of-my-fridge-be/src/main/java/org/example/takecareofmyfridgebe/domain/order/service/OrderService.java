package org.example.takecareofmyfridgebe.domain.order.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCheckoutResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateResult;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 주문하기에서 주문 도메인이 맡는 일을 한다. 같은 키로 만든 주문 찾기, 번개 드롭 구매 기록 확인, 주문 저장.
// 장바구니·상품·할인을 읽고 재고를 빼는 일은 OrderFacade가 하고, 이 서비스는 그 트랜잭션 안에서 불린다.
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private static final String ORDER_NUMBER_UNIQUE = "uk_orders_order_number";
    private static final String IDEMPOTENCY_KEY_UNIQUE = "uk_orders_member_id_idempotency_key";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderReader orderReader;

    // 같은 키로 이미 만든 주문을 돌려준다. 고른 상품·수량이 처음 요청과 다르면 키를 잘못 재사용한 것으로 보고 거절한다.
    public Optional<OrderCreateResult> findExistingOrder(
        Long memberId, String idempotencyKey, List<Long> cartItemIds, List<CartItem> cartItems
    ) {
        return orderRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey)
            .map(order -> toExistingResult(order, cartItemIds, cartItems));
    }

    // 이 회원이 이 번개 드롭을 이미 샀는지 본다. 1인 1개 기록은 주문 상품 행에 남는다.
    public boolean hasBoughtFlashDrop(Long saleEventItemId, Long memberId) {
        return orderItemRepository.existsBySaleEventItemIdAndFlashDropMemberId(saleEventItemId, memberId);
    }

    // 상품 행을 잠그고 재고를 뺀 뒤에 불린다. 주문과 주문 상품을 저장한다.
    @Transactional
    public OrderCreateResult create(
        Long memberId, String idempotencyKey, List<CartItem> cartItems,
        Map<Long, Product> productsById, Map<Long, SellingPrice> pricesByProductId
    ) {
        long totalAmount = cartItems.stream()
            .mapToLong(item -> (long) pricesByProductId.get(item.getProductId()).unitPrice() * item.getQuantity())
            .sum();
        Order order = saveOrder(memberId, idempotencyKey, totalAmount);

        List<OrderItem> orderItems = cartItems.stream()
            .map(item -> toOrderItem(order, memberId, productsById.get(item.getProductId()),
                pricesByProductId.get(item.getProductId()), item.getQuantity()))
            .toList();
        orderItemRepository.saveAll(orderItems);
        return new OrderCreateResult(OrderCheckoutResponse.from(order, orderItems), true);
    }

    // 장바구니에는 같은 상품이 한 항목뿐이라 상품·수량이 같으면 같은 요청이다.
    private OrderCreateResult toExistingResult(Order order, List<Long> cartItemIds, List<CartItem> cartItems) {
        List<OrderItem> orderItems = orderReader.getOrderItems(order);
        OrderCreateResult result = new OrderCreateResult(OrderCheckoutResponse.from(order, orderItems), false);
        // 결제 완료나 사용자 삭제로 장바구니 항목이 일부 사라졌으면 비교할 수 없으므로 처음 주문을 그대로 돌려준다.
        if (cartItems.size() != cartItemIds.size()) {
            return result;
        }

        Map<Long, Integer> ordered = orderItems.stream()
            .collect(Collectors.toMap(OrderItem::getProductId, OrderItem::getQuantity));
        Map<Long, Integer> requested = cartItems.stream()
            .collect(Collectors.toMap(CartItem::getProductId, CartItem::getQuantity));
        if (!ordered.equals(requested)) {
            throw new BusinessException(ErrorCode.ORDER_IDEMPOTENCY_KEY_REUSED);
        }
        return result;
    }

    // 주문번호가 겹치거나(시계가 순번 키 만료 시간보다 크게 뒤로 간 경우 등, 드묾) 같은 멱등 키가 동시에 저장되면(장바구니 잠금이 있으면 생기지 않음).
    // 이 주문은 롤백하고 다시 주문하게 한다. 그 밖의 제약 위반은 원인을 가리지 않도록 그대로 던진다.
    private Order saveOrder(Long memberId, String idempotencyKey, long totalAmount) {
        Order order = Order.builder()
            .orderNumber(orderNumberGenerator.next())
            .memberId(memberId)
            .idempotencyKey(idempotencyKey)
            .totalAmount(totalAmount)
            .build();
        try {
            return orderRepository.save(order);
        } catch (DataIntegrityViolationException e) {
            String constraint = constraintName(e);
            log.warn("주문 저장 중 제약 위반: constraint={}", constraint, e);
            if (constraint.contains(ORDER_NUMBER_UNIQUE) || constraint.contains(IDEMPOTENCY_KEY_UNIQUE)) {
                throw new BusinessException(ErrorCode.ORDER_NUMBER_CONFLICT);
            }
            throw e;
        }
    }

    private String constraintName(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cause && cause.getConstraintName() != null) {
            return cause.getConstraintName();
        }
        return "";
    }

    private OrderItem toOrderItem(Order order, Long memberId, Product product, SellingPrice price, int quantity) {
        return OrderItem.builder()
            .order(order)
            .productId(product.getId())
            .productName(product.getName())
            .productImageUrl(product.getImageUrl())
            .quantity(quantity)
            .listPrice(price.listPrice())
            .unitPrice(price.unitPrice())
            .saleType(SaleType.from(price.saleType()))
            .saleEventItemId(price.saleEventItemId())
            .flashDropMemberId(price.isFlashDrop() ? memberId : null)
            .build();
    }
}
