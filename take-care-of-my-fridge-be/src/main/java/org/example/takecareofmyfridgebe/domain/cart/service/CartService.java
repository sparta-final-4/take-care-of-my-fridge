package org.example.takecareofmyfridgebe.domain.cart.service;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemRequest;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResponse;
import org.example.takecareofmyfridgebe.domain.cart.dto.AddCartItemResult;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartItemRepository;
import org.example.takecareofmyfridgebe.domain.cart.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    @Transactional
    public AddCartItemResult addItem(Long memberId, AddCartItemRequest request) {
        // TODO: 상품 도메인 연동 시 상품 존재·판매 여부·재고, 번개 드롭 수량 한도 확인
        Cart cart = cartRepository.findByMemberId(memberId)
            .orElseGet(() -> cartRepository.save(Cart.builder().memberId(memberId).build()));

        Optional<CartItem> existing = cartItemRepository.findByCartAndProductId(cart, request.productId());
        if (existing.isEmpty()) {
            CartItem saved = cartItemRepository.save(request.toEntity(cart));
            return new AddCartItemResult(AddCartItemResponse.from(saved), true);
        }

        CartItem item = existing.get();
        item.changeQuantity(item.getQuantity() + request.quantity());
        return new AddCartItemResult(AddCartItemResponse.from(item), false);
    }
}
