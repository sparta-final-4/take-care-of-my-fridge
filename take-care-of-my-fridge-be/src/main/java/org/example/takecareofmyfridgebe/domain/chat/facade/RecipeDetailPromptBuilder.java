package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse.IngredientItem;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse.StepItem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 레시피 자세히 답에 넣을 [레시피 정보] 줄 글을 만든다. 스프링에 의존하지 않는 순수 클래스.
 * <pre>
 * [레시피 정보]
 * 이름: 깐풍기 | 분류: 튀김 | 조리 시간: 40분 | 2인분 | 난이도 2   (조리 시간이 없으면 "조리 시간: 레시피에 없음")
 * 재료: 닭다리살 300g, 전분 1/2컵, 대파 1대
 * 기본 양념: 간장 2큰술, 설탕 1큰술
 * 단계:
 * 1. 닭을 한입 크기로 썬다.
 * 팁: 소스는 센 불에서 빠르게 졸인다.
 * [냉장고 기준] 쓰는 재료: 내일까지인 대파, 닭다리살 / 장볼 재료: 전분, 건고추
 * </pre>
 * 단계는 단계마다 {@value #STEP_MAX_LENGTH}자, 최대 {@value #MAX_STEPS}단계까지만 넣는다 (입력 토큰과 답 잘림을 줄이려고).
 * AI는 이 정보에 있는 것만 말한다 (prompts/chat-recipe-detail.txt).
 */
public final class RecipeDetailPromptBuilder {

    // 단계 하나의 최대 글자 수와 넣을 최대 단계 수.
    // dev 레시피: 평균 5.7단계·최대 10단계, 단계 글자 합 평균 217자(단계당 약 38자)·최대 587자라 거의 자르지 않는다
    static final int STEP_MAX_LENGTH = 120;
    static final int MAX_STEPS = 10;
    // 팁 요약이 없을 때 팁 원문에서 넣을 최대 글자 수
    static final int TIP_MAX_LENGTH = 200;
    private static final String FIELD_DELIMITER = " | ";
    private static final String NAME_DELIMITER = ", ";
    private static final String NONE = "없음";
    private static final String ELLIPSIS = "…";
    static final String NO_COOK_TIME = "레시피에 없음";
    // 단계를 잘랐을 때 단계 목록 끝에 붙이는 줄의 머리 (프롬프트가 이 줄이 있을 때만 카드를 눌러 보라고 한다)
    static final String REMAINING_STEPS = "나머지 단계: ";

    private RecipeDetailPromptBuilder() {
    }

    /**
     * @param recipe 레시피 상세 (재료·단계·팁)
     * @param fridge 지금 냉장고 기준으로 계산한 쓰는 재료·임박·장볼 재료. 계산하지 못했으면 null ([냉장고 기준] 줄을 넣지 않음)
     */
    public static String build(RecipeDetailResponse recipe, RecipeCandidate fridge) {
        StringBuilder text = new StringBuilder("[레시피 정보]\n");
        text.append(headerLine(recipe)).append('\n');
        List<IngredientItem> ingredients = recipe.ingredients() == null ? List.of() : recipe.ingredients();
        text.append("재료: ").append(ingredientLine(ingredients.stream().filter(item -> !item.basicSeasoning()).toList())).append('\n');
        List<IngredientItem> seasonings = ingredients.stream().filter(IngredientItem::basicSeasoning).toList();
        if (!seasonings.isEmpty()) {
            text.append("기본 양념: ").append(ingredientLine(seasonings)).append('\n');
        }
        text.append("단계:\n").append(stepLines(recipe.steps() == null ? List.of() : recipe.steps()));
        String tip = tip(recipe);
        if (tip != null) {
            text.append("팁: ").append(tip).append('\n');
        }
        if (fridge != null) {
            text.append("[냉장고 기준] ").append(fridgeLine(fridge.reason())).append('\n');
        }
        return text.toString().stripTrailing();
    }

    private static String headerLine(RecipeDetailResponse recipe) {
        List<String> fields = new ArrayList<>();
        fields.add("이름: " + recipe.name());
        fields.add("분류: " + (recipe.category() == null ? "분류 없음" : recipe.category()));
        // 조리 시간이 없는 레시피가 많다 (dev 레시피는 대부분 비어 있음). 없다는 걸 분명히 써서 AI가 "단계"까지 없다고 말하지 않게 한다
        fields.add("조리 시간: " + (recipe.cookTimeMin() == null ? NO_COOK_TIME : recipe.cookTimeMin() + "분"));
        if (recipe.servings() != null) {
            fields.add(recipe.servings() + "인분");
        }
        if (recipe.difficulty() > 0) {
            fields.add("난이도 " + recipe.difficulty());
        }
        return String.join(FIELD_DELIMITER, fields);
    }

    // "닭다리살 300g" 처럼 이름과 양. 양이 없으면 이름만
    private static String ingredientLine(List<IngredientItem> items) {
        if (items.isEmpty()) {
            return NONE;
        }
        return items.stream()
                .map(item -> item.amount() == null || item.amount().isBlank()
                        ? item.displayName()
                        : item.displayName() + " " + item.amount().strip())
                .collect(Collectors.joining(NAME_DELIMITER));
    }

    // 단계마다 한 줄. 길면 자르고, 단계가 많으면 뒤 단계는 몇 개를 뺐는지만 쓴다
    static String stepLines(List<StepItem> steps) {
        if (steps.isEmpty()) {
            return NONE + "\n";
        }
        StringBuilder text = new StringBuilder();
        int shown = Math.min(steps.size(), MAX_STEPS);
        for (int i = 0; i < shown; i++) {
            String description = oneLine(steps.get(i).description());
            text.append(i + 1).append(". ").append(cut(description, STEP_MAX_LENGTH)).append('\n');
        }
        // 실제로 단계를 잘랐을 때만 알린다. AI가 따라 쓰지 않게 괄호·"생략" 같은 표현은 쓰지 않는다
        if (steps.size() > MAX_STEPS) {
            text.append(REMAINING_STEPS).append(steps.size() - MAX_STEPS).append("단계가 레시피 화면에 더 있음\n");
        }
        return text.toString();
    }

    // 팁 요약이 있으면 그것, 없으면 팁 원문 앞부분. 둘 다 없으면 null
    private static String tip(RecipeDetailResponse recipe) {
        if (recipe.tipSummary() != null && !recipe.tipSummary().isBlank()) {
            return oneLine(recipe.tipSummary());
        }
        if (recipe.tip() != null && !recipe.tip().isBlank()) {
            return cut(oneLine(recipe.tip()), TIP_MAX_LENGTH);
        }
        return null;
    }

    // 쓰는 냉장고 재료(임박이면 "내일까지인 대파")와 장볼 재료(기본 양념 제외)
    private static String fridgeLine(Reason reason) {
        Map<String, Long> dDays = reason.imminentIngredients().stream()
                .collect(Collectors.toMap(ImminentIngredient::name, ImminentIngredient::dDay, (a, b) -> a));
        String used = reason.usedFridgeIngredients().isEmpty()
                ? NONE
                : reason.usedFridgeIngredients().stream()
                .map(name -> dDays.containsKey(name)
                        ? RecommendationPromptBuilder.dDayLabel(dDays.get(name)) + " " + name
                        : name)
                .collect(Collectors.joining(NAME_DELIMITER));
        String missing = reason.missingIngredients().isEmpty()
                ? NONE
                : reason.missingIngredients().stream().map(MissingIngredient::name).collect(Collectors.joining(NAME_DELIMITER));
        return "쓰는 재료: " + used + " / 장볼 재료: " + missing;
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").strip();
    }

    private static String cut(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + ELLIPSIS : text;
    }
}
