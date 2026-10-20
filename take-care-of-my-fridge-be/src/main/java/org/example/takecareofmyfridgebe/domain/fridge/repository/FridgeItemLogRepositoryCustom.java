package org.example.takecareofmyfridgebe.domain.fridge.repository;

import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface FridgeItemLogRepositoryCustom {

    Page<FridgeItemLog> findLogsByCondition(
            Long memberId,
            FridgeItemAction action,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );
}
