package org.example.takecareofmyfridgebe.domain.ingredient.dto;

/**
 * 재료명 하나에 대한 표준 재료 후보.
 *
 * @param value 표준 재료명 (ingredients.name)
 * @param score 0~1. 1이면 별칭이 정확히 일치하거나 입력과 같은 이름 수준으로 맞은 것
 */
public record IngredientCandidate(String value, double score) {}
