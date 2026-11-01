package org.example.takecareofmyfridgebe.domain.fridge.repository;

import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;

import java.util.List;

public interface FridgeRepositoryCustom {

    List<Fridge> findItemsByCondition(
            Long memberId,
            FridgeSortType sort
    );
}
