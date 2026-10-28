package org.example.takecareofmyfridgebe.domain.search.dto;

import java.util.List;

public record RecipeSearchResult(
        List<Long> recipeIds,
        long total
) {
}
