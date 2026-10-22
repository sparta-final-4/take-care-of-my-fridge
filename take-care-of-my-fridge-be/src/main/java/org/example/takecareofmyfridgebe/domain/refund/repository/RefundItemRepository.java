package org.example.takecareofmyfridgebe.domain.refund.repository;

import org.example.takecareofmyfridgebe.domain.refund.entity.RefundItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundItemRepository extends JpaRepository<RefundItem, Long> {
}
