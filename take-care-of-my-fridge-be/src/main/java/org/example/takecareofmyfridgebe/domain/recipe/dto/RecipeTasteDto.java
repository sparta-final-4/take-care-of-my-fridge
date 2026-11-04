package org.example.takecareofmyfridgebe.domain.recipe.dto;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;

public record RecipeTasteDto(
        Long recipeId,
        Member.Taste taste
) {}
