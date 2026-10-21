package org.example.takecareofmyfridgebe.domain.cart.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.cart.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByMemberId(Long memberId);

    // 회원의 장바구니 행을 SELECT ... FOR UPDATE로 잠근다. 같은 회원의 주문하기를 하나씩 순서대로 처리하게 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.memberId = :memberId")
    Optional<Cart> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
