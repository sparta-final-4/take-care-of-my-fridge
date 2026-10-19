package org.example.takecareofmyfridgebe.domain.fridge.repository;

import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;

import java.time.LocalDate;
import java.util.List;

public interface FridgeRepositoryCustom {

    List<Fridge> findItemsByCondition(
            Long memberId,
            StorageType storage,
            FridgeSortType sort,
            boolean imminentOnly,
            LocalDate today
    );
}
