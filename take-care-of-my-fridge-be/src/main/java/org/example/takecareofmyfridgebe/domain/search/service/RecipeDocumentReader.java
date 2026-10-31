package org.example.takecareofmyfridgebe.domain.search.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeIndexSource;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeIndexSourceService;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.infra.elasticsearch.dto.EsDocument;
import org.example.takecareofmyfridgebe.infra.elasticsearch.index.EsDocumentReader;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class RecipeDocumentReader implements EsDocumentReader {

    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RecipeIndexSourceService recipeIndexSourceService;
    private final RecipeEmbedder embedder;
    private final RecipeSearchProperties properties;

    @Override
    public List<EsDocument> readAll() {
        return toDocuments(recipeIndexSourceService.findAll());
    }

    @Override
    // 단건 색인용: 레시피 하나. 삭제됐으면 빈 Optional
    public Optional<EsDocument> readOne(long recipeId) {
        return recipeIndexSourceService.findOne(recipeId)
                .map(recipe -> toDocuments(List.of(recipe)).get(0));
    }

    private List<EsDocument> toDocuments(List<RecipeIndexSource> recipes) {
        List<Map<String, Object>> docs = recipes.stream()
                .map(this::toDocument)
                .toList();
        attachEmbeddings(docs, recipes.stream()
                .map(RecipeDocumentReader::embeddingText)
                .toList());

        List<EsDocument> result = new ArrayList<>(recipes.size());
        for (int i = 0; i < recipes.size(); i++) {
            result.add(new EsDocument(recipes.get(i).id(), docs.get(i)));
        }
        return result;
    }

    // 매핑에 필드를 추가하면 여기도 추가한다
    private Map<String, Object> toDocument(RecipeIndexSource recipe) {
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

        return doc;
    }

    /**
     * 임베딩할 텍스트. 이 형식을 바꾸면 전체 재색인이 필요하다.
     * <br>
     * "칼칼한 국물"처럼 이름에 없는 표현도 잡히도록 요약, 분류, 재료, 태그를 함께 넣는다. 조리 단계는 길고 잡음이 많아 뺀다.
     */
    static String embeddingText(RecipeIndexSource recipe) {
        List<String> lines = new ArrayList<>();
        lines.add(recipe.name());
        addLine(lines, "", recipe.summary());
        addLine(lines, "분류: ", String.join(", ", nonBlank(recipe.cuisineType(), recipe.category(), recipe.cookMethod())));
        addLine(lines, "재료: ", String.join(", ", uniqueNames(recipe.ingredients(), false)));
        addLine(lines, "태그: ", String.join(", ", splitTags(recipe.tags())));
        return String.join("\n", lines);
    }

    private static void addLine(List<String> lines, String label, String value) {
        if (value != null && !value.isBlank()) {
            lines.add(label + value.strip());
        }
    }

    private static List<String> nonBlank(String... values) {
        return Stream.of(values)
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    /**
     * 레시피 임베딩을 켰을 때만 임베딩한다. 실패하면 벡터 없이 색인하고, 다음 재색인에서 채운다.
     * <br>
     * 키워드 검색은 벡터 없이도 동작하므로 OpenAI 장애가 재색인이나 레시피 저장 후 색인을 막지 않게 한다.
     */
    private void attachEmbeddings(List<Map<String, Object>> docs, List<String> texts) {
        RecipeSearchProperties.RecipeIndexProperties recipeIndex = properties.indexProperties().recipe();
        if (!recipeIndex.embeddingEnabled() || docs.isEmpty()) {
            return;
        }
        try {
            List<float[]> vectors = embedder.embedAll(texts);
            for (int i = 0; i < docs.size(); i++) {
                docs.get(i).put("embedding", vectors.get(i));
            }
        } catch (RuntimeException e) {
            log.warn("[{}] 레시피 임베딩 실패, 벡터 없이 색인한다 ({}건)", recipeIndex.alias(), docs.size(), e);
        }
    }

    private static List<String> splitTags(String tags) {
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

    private static List<String> uniqueNames(List<RecipeIndexSource.Ingredient> ingredients, boolean mainOnly) {
        Set<String> unique = new LinkedHashSet<>();
        for (RecipeIndexSource.Ingredient ingredient : ingredients) {
            if (!mainOnly || "주재료".equals(ingredient.role())) {
                unique.add(ingredient.name());
            }
        }
        return List.copyOf(unique);
    }

}
