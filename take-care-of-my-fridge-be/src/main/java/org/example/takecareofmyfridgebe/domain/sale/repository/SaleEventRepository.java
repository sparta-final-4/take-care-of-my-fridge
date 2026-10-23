package org.example.takecareofmyfridgebe.domain.sale.repository;

import org.example.takecareofmyfridgebe.domain.sale.entity.SaleEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleEventRepository extends JpaRepository<SaleEvent, Long> {
}
