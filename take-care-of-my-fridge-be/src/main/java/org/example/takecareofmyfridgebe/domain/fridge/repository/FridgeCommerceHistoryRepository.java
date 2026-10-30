package org.example.takecareofmyfridgebe.domain.fridge.repository;

import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeCommerceHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface FridgeCommerceHistoryRepository extends JpaRepository<FridgeCommerceHistory, Long> {

    List<FridgeCommerceHistory> findByOrderItemIdIn(Collection<Long> orderItemIds);
}
