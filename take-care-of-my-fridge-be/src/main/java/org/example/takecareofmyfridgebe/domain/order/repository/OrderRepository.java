package org.example.takecareofmyfridgebe.domain.order.repository;

import java.util.Optional;
import org.example.takecareofmyfridgebe.domain.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByMemberIdAndIdempotencyKey(Long memberId, String idempotencyKey);
}
