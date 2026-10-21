package org.example.takecareofmyfridgebe.domain.order.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
    // 내 주문만 찾는다. 남의 주문이거나 없는 번호면 비어 있다.
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);

    // 내 주문 행을 SELECT ... FOR UPDATE로 잠근다. 같은 주문의 상태 변경을 하나씩 순서대로 처리하게 한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id and o.memberId = :memberId")
    Optional<Order> findByIdAndMemberIdForUpdate(@Param("id") Long id, @Param("memberId") Long memberId);
}
