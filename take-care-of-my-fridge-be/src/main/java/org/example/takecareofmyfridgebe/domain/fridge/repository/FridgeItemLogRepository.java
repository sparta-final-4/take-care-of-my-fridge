package org.example.takecareofmyfridgebe.domain.fridge.repository;

import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FridgeItemLogRepository extends JpaRepository<FridgeItemLog, Long> {
}
