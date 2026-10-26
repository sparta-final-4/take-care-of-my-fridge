package org.example.takecareofmyfridgebe.domain.recipe.dto;

import java.time.LocalDateTime;

/**
 * 레시피 ID와 최근 수정 일시(updatedAt) 프로젝션 DTO.
 * 새벽 요약 재작업 대상 선별 시 사용된다.
 */
public record RecipeUpdatedAtDto(
        Long id,
        LocalDateTime updatedAt
) {}
