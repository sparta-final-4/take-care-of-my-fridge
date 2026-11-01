package org.example.takecareofmyfridgebe.domain.order.facade;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.service.CartService;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateItemRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateRequest;
import org.example.takecareofmyfridgebe.domain.order.dto.OrderCreateResult;
import org.example.takecareofmyfridgebe.domain.order.service.OrderService;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.domain.sale.dto.FlashDropQuantity;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEventType;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventStockService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 주문하기. 장바구니·상품·할인을 읽고 재고를 뺀 뒤 OrderService로 주문을 저장한다.
// 재고 차감과 주문 저장이 한 트랜잭션이어야 해서 메서드에 트랜잭션을 연다. 하나라도 실패하면 재고까지 롤백된다.
// 잠금 순서는 OrderStatusFacade와 같다. 장바구니 행 → 상품 행(ID 순) → 번개 드롭 남은 수량 행.
// 상품 행 잠금 읽기는 ProductService에 없어 ProductRepository를 직접 쓴다.
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private static final int IDEMPOTENCY_KEY_MAX_LENGTH = 64;

    private final OrderService orderService;
    private final CartService cartService;
    private final ProductRepository productRepository;
    private final SaleEventReader saleEventReader;
    private final SaleEventStockService saleEventStockService;

    // 장바구니 항목은 결제 완료 때 지우므로 여기서는 건드리지 않는다.
    @Transactional
    public OrderCreateResult create(Long memberId, String idempotencyKey, OrderCreateRequest request) {
        validateIdempotencyKey(idempotencyKey);

        // 같은 회원의 주문하기를 하나씩 순서대로 처리해, 같은 멱등 키로 동시에 온 요청이 주문을 둘 만들지 않게 한다. 다른 회원끼리는 기다리지 않는다.
        // 이 잠금은 트랜잭션의 첫 조회여야 한다. 앞에 일반 조회가 있으면 그 시점 스냅샷으로 아래 확인을 해서 잠금을 기다리는 동안 커밋된 주문을 못 본다.
        cartService.getCartForUpdate(memberId);
        List<Long> cartItemIds = request.cartItemIds();
        List<CartItem> cartItems = cartService.getItems(memberId, cartItemIds);

        // 같은 키 확인을 장바구니 항목 검증보다 먼저 한다. 처음 주문 뒤 장바구니 항목이 지워져도 재전송에는 처음 주문을 돌려준다.
        Optional<OrderCreateResult> existing = orderService.findExistingOrder(memberId, idempotencyKey, cartItemIds, cartItems);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (cartItems.size() != cartItemIds.size()) {
            throw new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND);
        }

        // 상품 행을 잠근 뒤 읽으므로 아래 재고 확인과 차감 사이에 다른 주문이 끼어들지 못한다.
        Map<Long, Product> productsById = findProductsForUpdate(cartItems.stream().map(CartItem::getProductId).toList());
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

        return orderService.create(memberId, idempotencyKey, cartItems, productsById, pricesByProductId);
    }

    // 재고 되돌리기(OrderStatusFacade)와 같은 순서(상품 ID 순)로 상품 행을 잠가 서로 섞이지 않게 한다.
    private Map<Long, Product> findProductsForUpdate(List<Long> productIds) {
        return productRepository.findAllByIdInForUpdate(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "Idempotency-Key 헤더가 필요합니다. (64자 이하)");
        }
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

    // 이미 산 번개 드롭을 장바구니에 담은 경우는 여기서 막는다. 같은 회원의 주문하기는 장바구니 잠금으로 하나씩 처리된다.
    private void validateFlashDropLimit(Long memberId, CartItem item, Product product, SellingPrice price) {
        boolean bought = orderService.hasBoughtFlashDrop(price.saleEventItemId(), memberId);
        if (item.getQuantity() > SaleEvent.FLASH_DROP_PURCHASE_LIMIT || bought) {
            throw new BusinessException(ErrorCode.PURCHASE_LIMIT_EXCEEDED, "구매 한도 초과 · " + product.getName());
        }
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
}
