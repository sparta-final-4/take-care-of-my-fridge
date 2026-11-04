package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Ranking;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse.IngredientItem;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse.StepItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeDetailPromptBuilderTest {

    @Test
    @DisplayName("레시피 이름·분류·시간·인분·난이도, 재료와 기본 양념, 단계, 팁 요약, 냉장고 기준(임박은 말 그대로)을 줄 글로 만든다")
    void buildsRecipeInfo() {
        RecipeDetailResponse recipe = recipe(List.of(
                ingredient("닭다리살", "300g", false), ingredient("대파", "1대", false), ingredient("간장", "2큰술", true)),
                List.of(step(1, "닭을 한입 크기로\n썬다."), step(2, "전분을 묻혀 튀긴다.")), "소스는 센 불에서 졸인다.", "긴 팁");
        RecipeCandidate fridge = new RecipeCandidate(20L, "깐풍기", "튀김", 40, new Ranking(0, 0, 0, 0, 1, 1, 0),
                new Reason(List.of("대파", "닭다리살"), List.of(new ImminentIngredient("대파", 1)), List.of(), List.of(), List.of(),
                        false, false, List.of(new MissingIngredient(7L, "전분")), List.of(), List.of(), null));

        String text = RecipeDetailPromptBuilder.build(recipe, fridge);

        assertThat(text).isEqualTo("""
                [레시피 정보]
                이름: 깐풍기 | 분류: 튀김 | 조리 시간: 40분 | 2인분 | 난이도 2
                재료: 닭다리살 300g, 대파 1대
                기본 양념: 간장 2큰술
                단계:
                1. 닭을 한입 크기로 썬다.
                2. 전분을 묻혀 튀긴다.
                팁: 소스는 센 불에서 졸인다.
                [냉장고 기준] 쓰는 재료: 내일까지인 대파, 닭다리살 / 장볼 재료: 전분""");
    }

    @Test
    @DisplayName("단계는 단계마다 120자까지 자르고 15단계까지만 넣은 뒤 뺀 단계 수를 쓴다")
    void cutsLongSteps() {
        List<StepItem> steps = IntStream.rangeClosed(1, 17)
                .mapToObj(no -> step(no, no == 1 ? "가".repeat(130) : no + "단계"))
                .toList();

        String lines = RecipeDetailPromptBuilder.stepLines(steps);

        assertThat(lines).startsWith("1. " + "가".repeat(120) + "…\n2. 2단계\n")
                .contains("15. 15단계\n")
                .doesNotContain("16단계")
                .endsWith("(뒤 2단계 생략, 레시피 화면에 있음)\n");
    }

    @Test
    @DisplayName("팁 요약이 없으면 팁 원문 앞 200자를 넣고, 냉장고 기준을 계산하지 못했으면 그 줄을 넣지 않는다")
    void usesTipAndSkipsFridge_whenMissing() {
        RecipeDetailResponse recipe = recipe(List.of(), List.of(), null, "팁".repeat(210));

        String text = RecipeDetailPromptBuilder.build(recipe, null);

        assertThat(text).contains("재료: 없음", "단계:\n없음", "팁: " + "팁".repeat(200) + "…").doesNotContain("[냉장고 기준]");
    }

    private static RecipeDetailResponse recipe(List<IngredientItem> ingredients, List<StepItem> steps, String tipSummary, String tip) {
        return RecipeDetailResponse.builder()
                .recipeId(20L)
                .name("깐풍기")
                .category("튀김")
                .cookTimeMin(40)
                .servings(2)
                .difficulty(2)
                .tip(tip)
                .tipSummary(tipSummary)
                .ingredients(ingredients)
                .steps(steps)
                .build();
    }

    private static IngredientItem ingredient(String name, String amount, boolean basicSeasoning) {
        return new IngredientItem(1, 1L, name, amount, null, null, null, basicSeasoning);
    }

    private static StepItem step(int no, String description) {
        return new StepItem(no, description, null, null);
    }
}
