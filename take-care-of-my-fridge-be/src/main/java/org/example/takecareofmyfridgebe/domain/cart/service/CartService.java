package org.example.takecareofmyfridgebe.domain.cart.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResult;
import org.example.takecareofmyfridgebe.domain.cart.dto.CartItemsResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.ChangeCartItemQuantityResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.RemoveCartItemsRequest;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.repository.ProductRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Transactional
    public AddCartItemResult addItem(Long memberId, AddCartItemRequest request) {
        // TODO: 번개 드롭 수량 한도 확인
        Product product = findSellableProduct(request.productId());

        Cart cart = cartRepository.findByMemberId(memberId)
            .orElseGet(() -> cartRepository.save(Cart.builder().memberId(memberId).build()));

        Optional<CartItem> existing = cartItemRepository.findByCartAndProductId(cart, request.productId());
        int currentQuantity = existing.map(CartItem::getQuantity).orElse(0);
        // 기존 수량 + 요청 수량은 int를 넘칠 수 있어 재고 쪽에서 뺀다
        if (request.quantity() > product.getStock() - currentQuantity) {
            throw new BusinessException(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        }

        if (existing.isEmpty()) {
            CartItem saved = cartItemRepository.save(request.toEntity(cart));
            return new AddCartItemResult(AddCartItemResponse.from(saved), true);
        }

        CartItem item = existing.get();
        item.changeQuantity(item.getQuantity() + request.quantity());
        return new AddCartItemResult(AddCartItemResponse.from(item), false);
    }

    public CartItemsResponse getItems(Long memberId) {
        List<CartItem> items = cartItemRepository.findByCartMemberIdOrderByIdAsc(memberId);
        // 줄마다 상품을 따로 읽지 않고 한 번에 읽는다
        List<Long> productIds = items.stream().map(CartItem::getProductId).toList();
        Map<Long, Product> productsById = productRepository.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        return CartItemsResponse.from(items, productsById);
    }

    @Transactional
    public ChangeCartItemQuantityResponse changeQuantity(Long memberId, Long cartItemId, ChangeCartItemQuantityRequest request) {
        // TODO: 번개 드롭 수량 한도 확인
        CartItem item = cartItemRepository.findByIdAndCartMemberId(cartItemId, memberId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CART_ITEM_NOT_FOUND));
        Product product = findSellableProduct(item.getProductId());

        // 담아 둔 뒤 재고가 줄었어도 수량을 내리는 것은 막지 않는다
        int quantity = request.quantity();
        if (quantity > product.getStock() && quantity > item.getQuantity()) {
            throw new BusinessException(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
        }

        item.changeQuantity(quantity);
        return ChangeCartItemQuantityResponse.from(item);
    }

    @Transactional
    public void removeItems(Long memberId, RemoveCartItemsRequest request) {
        cartItemRepository.deleteAllByIdsAndMemberId(request.cartItemIds(), memberId);
    }

    // 품절은 상태값이 아니라 재고 숫자로 판단하므로 판매 중지만 거른다
    private Product findSellableProduct(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() == ProductStatus.STOPPED) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE);
        }
        return product;
    }
}
