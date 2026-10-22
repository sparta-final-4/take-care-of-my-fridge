package org.example.takecareofmyfridgebe.domain.refund.repository;

import org.example.takecareofmyfridgebe.domain.refund.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefundRepository extends JpaRepository<Refund, Long> {
}
