package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIndexSource;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeIndexSourceService;
import org.example.takecareofmyfridgebe.domain.search.dto.EsDocument;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class RecipeDocumentReader implements EsDocumentReader {

    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RecipeIndexSourceService recipeIndexSourceService;

    @Override
    public List<EsDocument> readAll() {
        return recipeIndexSourceService.findAll().stream()
                .map(this::toDocument)
                .toList();
    }

    @Override
    // 단건 색인용: 레시피 하나. 삭제됐으면 빈 Optional
    public Optional<EsDocument> readOne(long recipeId) {
        return recipeIndexSourceService.findOne(recipeId)
                .map(this::toDocument);
    }

    // 매핑에 필드를 추가하면 여기도 추가한다
    private EsDocument toDocument(RecipeIndexSource recipe) {
        List<String> ingredientNames = uniqueNames(recipe.ingredients(), false);

        Map<String, Object> doc = new HashMap<>();
        doc.put("id", recipe.id());
        doc.put("source", recipe.source());
        doc.put("source_id", recipe.sourceId());
        doc.put("name", recipe.name());
        doc.put("summary", recipe.summary());
        doc.put("cuisine_type", recipe.cuisineType());
        doc.put("category", recipe.category());
        doc.put("cook_method", recipe.cookMethod());
        doc.put("main_ingredient_category", recipe.mainIngredientCategory());
        doc.put("cook_time_min", recipe.cookTimeMin());
        doc.put("servings", recipe.servings());
        doc.put("difficulty", recipe.difficulty());
        doc.put("weight_g", recipe.weightG());
        doc.put("calories", recipe.calories());
        doc.put("carbs_g", recipe.carbsG());
        doc.put("protein_g", recipe.proteinG());
        doc.put("fat_g", recipe.fatG());
        doc.put("sodium_mg", recipe.sodiumMg());
        doc.put("tip", recipe.tip());
        doc.put("image_url", recipe.imageUrl());
        doc.put("created_at", recipe.createdAt() == null ? null : recipe.createdAt().format(CREATED_AT));
        doc.put("tags", splitTags(recipe.tags()));
        doc.put("ingredients", ingredientNames);
        doc.put("main_ingredients", uniqueNames(recipe.ingredients(), true));
        doc.put("ingredient_count", ingredientNames.size());
        doc.put("steps_text", String.join("\n", recipe.steps()));
        doc.put("step_count", recipe.steps().size());

        return new EsDocument(recipe.id(), doc);
    }

    private List<String> splitTags(String tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        Arrays.stream(tags.split(","))
                .map(String::strip)
                .filter(t -> !t.isEmpty())
                .forEach(unique::add);
        return List.copyOf(unique);
    }

    private List<String> uniqueNames(List<RecipeIndexSource.Ingredient> ingredients, boolean mainOnly) {
        Set<String> unique = new LinkedHashSet<>();
        for (RecipeIndexSource.Ingredient ingredient : ingredients) {
            if (!mainOnly || "주재료".equals(ingredient.role())) {
                unique.add(ingredient.name());
            }
        }
        return List.copyOf(unique);
    }

}
