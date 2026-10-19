package org.example.takecareofmyfridgebe.domain.fridge.dto;

import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;

public record FridgeItemSearchRequest(
        StorageType storage,
        FridgeSortType sort,
        Boolean imminentOnly
) {
    public FridgeItemSearchRequest {
        sort = sort == null ? FridgeSortType.EXPIRATION : sort;
        imminentOnly = imminentOnly != null && imminentOnly;
    }
}
