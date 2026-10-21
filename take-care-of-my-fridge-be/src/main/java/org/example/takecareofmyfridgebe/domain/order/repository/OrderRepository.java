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

    // 주문을 SELECT ... FOR UPDATE로 잠근다. 한 주문에 결제가 여러 건이므로 결제 승인·주문 취소·만료가 이 잠금으로 순서대로 처리된다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
    // 내 주문만 찾는다. 남의 주문이거나 없는 번호면 비어 있다.
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);
}
