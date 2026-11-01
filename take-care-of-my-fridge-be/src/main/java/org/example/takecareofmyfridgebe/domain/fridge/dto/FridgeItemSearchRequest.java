package org.example.takecareofmyfridgebe.domain.fridge.dto;

public record FridgeItemSearchRequest(
        FridgeSortType sort
) {
    public FridgeItemSearchRequest {
        sort = sort == null ? FridgeSortType.EXPIRATION : sort;
    }
}
