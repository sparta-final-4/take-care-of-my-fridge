package org.example.takecareofmyfridgebe.domain.order.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.example.takecareofmyfridgebe.domain.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 생성 시각이 기준보다 이른 주문을 id 순으로 나눠 읽는다. 처리에 실패해 남은 건이 뒤의 건을 막지 않도록 id 커서를 쓴다
    @Query("""
        select o.id from Order o
        where o.status = :status and o.createdAt < :createdBefore and o.id > :afterId
        order by o.id
        """)
    List<Long> findIdsCreatedBefore(@Param("status") OrderStatus status,
                                    @Param("createdBefore") LocalDateTime createdBefore,
                                    @Param("afterId") long afterId, Pageable pageable);

    // 한 주문 처리 실패가 뒤의 주문을 막지 않도록 ID 커서로 결제 후 7일 지난 건을 읽는다.
    @Query("""
        select o from Order o
        where o.status = :status and o.paidAt <= :paidBefore and o.id > :afterId
        order by o.id
        """)
    List<Order> findPaidBefore(@Param("status") OrderStatus status,
                               @Param("paidBefore") LocalDateTime paidBefore,
                               @Param("afterId") long afterId, Pageable pageable);

    Page<Order> findByMemberIdAndStatusIn(Long memberId, Collection<OrderStatus> statuses, Pageable pageable);
}
