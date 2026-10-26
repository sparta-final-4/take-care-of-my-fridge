package org.example.takecareofmyfridgebe.domain.fridge.repository;

import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FridgeItemLogRepository extends JpaRepository<FridgeItemLog, Long>, FridgeItemLogRepositoryCustom {

    @Query("SELECT COUNT(DISTINCT l.ingredient.id) " +
           "FROM FridgeItemLog l " +
           "WHERE l.memberId = :memberId AND l.cookingLogId IS NOT NULL")
    long countConsumedByCooking(@Param("memberId") Long memberId);
}
