package org.example.takecareofmyfridgebe.domain.cart.repository;

import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndProductId(Cart cart, Long productId);

    // 담은 순서(id 오름차순)로 회원의 모든 줄을 읽는다. 장바구니가 없으면 빈 목록이다.
    List<CartItem> findByCartMemberIdOrderByIdAsc(Long memberId);

    // 내 장바구니의 줄만 읽는다. 남의 줄이거나 없는 줄이면 비어 있다.
    Optional<CartItem> findByIdAndCartMemberId(Long id, Long memberId);
}
