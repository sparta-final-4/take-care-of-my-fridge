package org.example.takecareofmyfridgebe.domain.cooking.dto;

import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;

import java.time.LocalDateTime;

public record CookingLogResponse(
        Long id,
        Long recipeId,
        CookingResult result,
        String memo,
        boolean fromChat,
        LocalDateTime createdAt
) {

    public static CookingLogResponse from(CookingLog log) {
        return new CookingLogResponse(
                log.getId(),
                log.getRecipeId(),
                log.getResult(),
                log.getMemo(),
                log.getChatSessionId() != null,
                log.getCreatedAt()
        );
    }
}
