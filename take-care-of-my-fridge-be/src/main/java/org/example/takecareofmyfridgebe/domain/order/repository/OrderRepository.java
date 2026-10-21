package org.example.takecareofmyfridgebe.domain.order.repository;

import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);

    // 내 주문만 찾는다. 남의 주문이거나 없는 번호면 비어 있다.
    Optional<Order> findByIdAndMemberId(Long id, Long memberId);
}
