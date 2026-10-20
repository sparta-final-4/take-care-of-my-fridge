package org.example.takecareofmyfridgebe.domain.cart.repository;

import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.example.takecareofmyfridgebe.domain.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndProductId(Cart cart, Long productId);

    // 담은 순서(id 오름차순)로 회원의 모든 줄을 읽는다. 장바구니가 없으면 빈 목록이다.
    List<CartItem> findByCartMemberIdOrderByIdAsc(Long memberId);

    // 내 장바구니의 줄만 읽는다. 남의 줄이거나 없는 줄이면 비어 있다.
    Optional<CartItem> findByIdAndCartMemberId(Long id, Long memberId);

    // 내 장바구니의 줄만 한 번에 지운다. 남의 줄이거나 없는 번호는 지워지지 않고 오류도 아니다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CartItem ci where ci.id in :ids "
            + "and ci.cart.id in (select c.id from Cart c where c.memberId = :memberId)")
    void deleteAllByIdsAndMemberId(@Param("ids") List<Long> ids, @Param("memberId") Long memberId);
}
