package org.example.takecareofmyfridgebe.domain.cart.service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResult;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartQuantityLimit;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.RemoveCartItemsRequest;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 장바구니 일만 한다. 담기·수량 변경에 필요한 상품 한도는 CartFacade가 읽어 넘긴다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private static final String CART_MEMBER_UNIQUE = "uk_carts_member_id";
    private static final String CART_ITEM_UNIQUE = "uk_cart_items_cart_id_product_id";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    public List<CartItem> getItems(Long memberId) {
        return cartItemRepository.findByCartMemberIdOrderByIdAsc(memberId);
    }

    // 수량을 바꿀 장바구니 항목. 퍼사드가 상품 ID를 알아 상품 한도를 읽는 데 쓴다.
    public CartItem getItem(Long memberId, Long cartItemId) {
        return cartItemRepository.findByIdAndCartMemberId(cartItemId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    // 주문하기가 고른 장바구니 항목. 남의 항목이거나 없는 ID는 빠진다.
    public List<CartItem> getItems(Long memberId, Collection<Long> cartItemIds) {
        return cartItemRepository.findByIdInAndCartMemberId(cartItemIds, memberId);
    }

    // 주문하기가 같은 회원의 요청을 하나씩 순서대로 처리하려고 장바구니 행을 잠근다. 호출하는 쪽의 쓰기 트랜잭션 안에서 부른다.
    @Transactional
    public Cart getCartForUpdate(Long memberId) {
        return cartRepository.findByMemberIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
    }

    @Transactional
    public AddCartItemResult addItem(Long memberId, AddCartItemRequest request, CartQuantityLimit limit) {
        Cart cart = cartRepository.findByMemberId(memberId)
                .orElseGet(() -> saveCart(memberId));

        Optional<CartItem> existing = cartItemRepository.findByCartAndProductId(cart, request.productId());
        // 기존 수량 + 요청 수량은 int를 넘칠 수 있어 long으로 더한다.
        long total = (long) existing.map(CartItem::getQuantity).orElse(0) + request.quantity();
        validateQuantity(limit, total);

        if (existing.isEmpty()) {
            CartItem saved = saveCartItem(cart, request);
            return new AddCartItemResult(AddCartItemResponse.from(saved), true);
        }

        CartItem item = existing.get();
        item.changeQuantity((int) total);
        return new AddCartItemResult(AddCartItemResponse.from(item), false);
    }

    @Transactional
    public ChangeCartItemQuantityResponse changeQuantity(
        Long memberId, Long cartItemId, ChangeCartItemQuantityRequest request, CartQuantityLimit limit
    ) {
        CartItem item = getItem(memberId, cartItemId);

        // 담아 둔 뒤 번개 드롭으로 바뀌었거나 재고가 줄었어도 수량을 내리는 것은 막지 않는다.
        int quantity = request.quantity();
        if (quantity > item.getQuantity()) {
            validateQuantity(limit, quantity);
        }

        item.changeQuantity(quantity);
        return ChangeCartItemQuantityResponse.from(item);
    }

    @Transactional
    public void removeItems(Long memberId, RemoveCartItemsRequest request) {
        cartItemRepository.deleteAllByIdsAndMemberId(request.cartItemIds(), memberId);
    }

    // 결제한 주문의 상품을 장바구니에서 항목째 지운다. 주문 뒤 수량을 바꿨어도 같은 상품 항목이면 지운다.
    // 결제 완료가 커밋된 뒤 불리므로 끝난 결제 트랜잭션에 합류하지 않도록 새 트랜잭션을 연다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removePaidItems(Long memberId, Long orderId) {
        cartItemRepository.deleteAllOrderedByMemberId(orderId, memberId);
    }

    // 같은 회원의 담기가 동시에 오면 둘 다 장바구니가 없다고 보고 저장하려 해 유니크 제약이 두 번째를 막는다.
    // 이 트랜잭션은 이미 실패 상태라 이어서 진행하지 않고, 충돌 예외로 바꿔 던져 호출한 쪽이 새 트랜잭션으로 다시 시도하게 한다.
    private Cart saveCart(Long memberId) {
        try {
            return cartRepository.save(Cart.builder().memberId(memberId).build());
        } catch (DataIntegrityViolationException e) {
            throw toAddConflict(e);
        }
    }

    private CartItem saveCartItem(Cart cart, AddCartItemRequest request) {
        try {
            return cartItemRepository.save(request.toEntity(cart));
        } catch (DataIntegrityViolationException e) {
            throw toAddConflict(e);
        }
    }

    // 장바구니와 항목의 유니크 제약만 충돌로 보고, 그 밖의 제약 위반은 원인을 가리지 않도록 그대로 던진다.
    private RuntimeException toAddConflict(DataIntegrityViolationException e) {
        String constraint = constraintName(e);
        if (constraint.contains(CART_MEMBER_UNIQUE) || constraint.contains(CART_ITEM_UNIQUE)) {
            return new BusinessException(ErrorCode.CART_ADD_CONFLICT);
        }
        return e;
    }

    private String constraintName(DataIntegrityViolationException e) {
        if (e.getCause() instanceof ConstraintViolationException cause && cause.getConstraintName() != null) {
            return cause.getConstraintName();
        }
        return "";
    }

    // 번개 드롭은 합친 수량이 1을 넘을 수 없고, 어떤 상품이든 재고보다 많이 담을 수 없다.
    private void validateQuantity(CartQuantityLimit limit, long totalQuantity) {
        if (limit.flashDrop() && totalQuantity > SaleEvent.FLASH_DROP_PURCHASE_LIMIT) {
            throw new BusinessException(ErrorCode.PURCHASE_LIMIT_EXCEEDED, "구매 한도 초과 · " + limit.productName());
        }
        if (totalQuantity > limit.stock()) {
            throw new BusinessException(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK, "재고 초과 · " + limit.productName());
        }
    }
}
