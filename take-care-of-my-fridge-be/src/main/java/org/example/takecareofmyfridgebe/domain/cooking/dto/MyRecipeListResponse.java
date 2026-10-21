package org.example.takecareofmyfridgebe.domain.cooking.dto;

import org.example.takecareofmyfridgebe.global.response.PageResponse;

public record MyRecipeListResponse(
        MyRecipeSummaryResponse summary,
        PageResponse<MyRecipeResponse> recipes
) {}
