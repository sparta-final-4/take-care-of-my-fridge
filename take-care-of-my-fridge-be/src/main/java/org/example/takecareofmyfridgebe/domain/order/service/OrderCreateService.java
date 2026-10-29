package org.example.takecareofmyfridgebe.domain.order.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCheckoutResponse;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateItemRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateResult;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderItem;
import org.example.takecareofmyfridgebe.domain.order.entity.SaleType;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderItemRepository;
import org.example.takecareofmyfridgebe.domain.order.repository.OrderRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropQuantity;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventStockService;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCreateService {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;
    private static final String ORDER_NUMBER_UNIQUE = "uk_orders_order_number";
    private static final String IDEMPOTENCY_KEY_UNIQUE = "uk_orders_member_id_idempotency_key";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final OrderReader orderReader;
    private final SaleEventReader saleEventReader;
    private final SaleEventStockService saleEventStockService;

    // 재고 차감과 주문 저장을 한 트랜잭션으로 묶는다. 하나라도 실패하면 앞서 뺀 재고까지 롤백된다.
    // 장바구니 항목은 결제 완료 때 지우므로 여기서는 건드리지 않는다.
    @Transactional
    public OrderCreateResult createOrder(Long memberId, String idempotencyKey, OrderCreateRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Idempotency-Key 헤더가 필요합니다. (64자 이하)");
        }

        // 같은 회원의 주문하기를 하나씩 순서대로 처리해, 같은 멱등 키로 동시에 온 요청이 주문을 둘 만들지 않게 한다. 다른 회원끼리는 기다리지 않는다.
        // 이 잠금은 트랜잭션의 첫 조회여야 한다. 앞에 일반 조회가 있으면 그 시점 스냅샷으로 아래 확인을 해서 잠금을 기다리는 동안 커밋된 주문을 못 본다.
        cartRepository.findByMemberIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));

        List<Long> cartItemIds = request.cartItemIds();
        List<CartItem> cartItems = cartItemRepository.findByIdInAndCartMemberId(cartItemIds, memberId);

        // 같은 키 확인을 장바구니 항목 검증보다 먼저 한다. 처음 주문 뒤 장바구니 항목이 지워져도 재전송에는 처음 주문을 돌려준다.
        Optional<Order> existing = orderRepository.findByMemberIdAndIdempotencyKey(memberId, idempotencyKey);
        if (existing.isPresent()) {
            return toExistingResult(existing.get(), cartItemIds, cartItems);
        }
        if (cartItems.size() != cartItemIds.size()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        // 상품 행을 잠근 뒤 읽으므로 아래 재고 확인과 차감 사이에 다른 주문이 끼어들지 못한다
        List<Long> productIds = cartItems.stream().map(CartItem::getProductId).toList();
        Map<Long, Product> productsById = productRepository.findAllByIdInForUpdate(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        cartItems.forEach(item -> validateSellable(productsById.get(item.getProductId())));

        // 진행 중 할인은 상품 행을 잠근 뒤 읽는다. 장바구니에서 본 단가·할인 구분과 하나라도 다르면 재고를 잡기 전에 거절한다.
        Map<Long, SellingPrice> pricesByProductId = saleEventReader.findSellingPrices(List.copyOf(productsById.values()));
        Map<Long, OrderCreateItemRequest> requestedByCartItemId = request.items().stream()
            .collect(Collectors.toMap(OrderCreateItemRequest::cartItemId, Function.identity(), (first, second) -> first));
        cartItems.forEach(item -> validatePrice(
            productsById.get(item.getProductId()),
            pricesByProductId.get(item.getProductId()),
            requestedByCartItemId.get(item.getId())));

        cartItems.forEach(item -> productsById.get(item.getProductId()).decreaseStock(item.getQuantity()));
        decreaseFlashDropRemaining(memberId, cartItems, productsById, pricesByProductId);

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

    // 같은 키로 이미 만든 주문을 돌려준다. 고른 상품·수량이 처음 요청과 다르면 키를 잘못 재사용한 것으로 보고 거절한다.
    // 장바구니에는 같은 상품이 한 항목뿐이라 상품·수량이 같으면 같은 요청이다.
    private OrderCreateResult toExistingResult(Order order, List<Long> cartItemIds, List<CartItem> cartItems) {
        List<OrderItem> orderItems = orderReader.getOrderItems(order);
        OrderCreateResult result = new OrderCreateResult(OrderCheckoutResponse.from(order, orderItems), false);
        // 결제 완료나 사용자 삭제로 장바구니 항목이 일부 사라졌으면 비교할 수 없으므로 처음 주문을 그대로 돌려준다
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

    // 판매 중지만 막는다. 상태가 품절이어도 재고 숫자가 남아 있으면 판다.
    private void validateSellable(Product product) {
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (product.getStatus() == ProductStatus.STOPPED) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
    }

    // 주문번호가 겹치거나(시계가 뒤로 간 채 재시작한 경우 등, 드묾) 같은 멱등 키가 동시에 저장되면(장바구니 잠금이 있으면 생기지 않음)
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

    // 보낸 단가나 할인 구분이 주문 시점 값과 다르면 오르든 내리든 거절한다. 문구는 할인 구분 비교로 고른다.
    private void validatePrice(Product product, SellingPrice price, OrderCreateItemRequest requested) {
        if (price.matches(requested.unitPrice(), requested.saleType())) {
            return;
        }
        SaleEventType currentType = price.saleType();
        String reason = "가격 변경";
        if (requested.saleType() == null && currentType != null) {
            reason = "할인 시작";
        } else if (requested.saleType() != null && currentType == null) {
            reason = "할인 종료";
        }
        throw new BusinessException(ErrorCode.ORDER_PRICE_CHANGED, reason + " · " + product.getName());
    }

    // 번개 드롭은 1인 1개이고 남은 수량이 있어야 한다. 남은 수량 차감은 상품 행을 잠근 뒤 한다.
    private void decreaseFlashDropRemaining(
        Long memberId, List<CartItem> cartItems, Map<Long, Product> productsById, Map<Long, SellingPrice> pricesByProductId
    ) {
        List<CartItem> flashDropItems = cartItems.stream()
            .filter(item -> pricesByProductId.get(item.getProductId()).isFlashDrop())
            .toList();
        if (flashDropItems.isEmpty()) {
            return;
        }
        flashDropItems.forEach(item -> validateFlashDropLimit(
            memberId, item, productsById.get(item.getProductId()), pricesByProductId.get(item.getProductId())));

        saleEventStockService.decreaseRemaining(flashDropItems.stream()
            .map(item -> new FlashDropQuantity(pricesByProductId.get(item.getProductId()).saleEventItemId(),
                item.getQuantity(), productsById.get(item.getProductId()).getName()))
            .toList());
    }

    // 이미 산 번개 드롭을 장바구니에 담은 경우는 여기서 막는다. 같은 회원의 주문하기는 장바구니 잠금으로 하나씩 처리된다.
    private void validateFlashDropLimit(Long memberId, CartItem item, Product product, SellingPrice price) {
        boolean bought = orderItemRepository.existsBySaleEventItemIdAndFlashDropMemberId(price.saleEventItemId(), memberId);
        if (item.getQuantity() > SaleEvent.FLASH_DROP_PURCHASE_LIMIT || bought) {
            throw new BusinessException(ErrorCode.PURCHASE_LIMIT_EXCEEDED, "구매 한도 초과 · " + product.getName());
        }
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
