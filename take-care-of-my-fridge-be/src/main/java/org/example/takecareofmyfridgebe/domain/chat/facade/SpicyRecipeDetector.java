package org.example.takecareofmyfridgebe.domain.chat.facade;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 재료로 매운 레시피를 가른다. 스프링에 의존하지 않는 순수 클래스.
 * 레시피 맛 데이터(recipe_tastes)에 매운맛이 빠진 레시피가 많아(깐풍기·깻잎말이김치 등), 매운 재료가 들어 있으면 매운 레시피로 본다.
 * <ul>
 *   <li>매운 재료 목록은 설정값(app.chat.candidate.spicy-ingredients)이다. 그냥 "고추"는 넣지 않는다 (풋고추·꽈리고추·고명이 많아서)</li>
 *   <li>재료 사전 이름과 레시피에 적힌 이름을 모두 본다 (사전 정리로 홍고추가 "고추"로 합쳐졌어도 원문 이름으로 잡히게)</li>
 *   <li>공백은 빼고, 이름에 목록 재료가 들어 있으면 매운 재료다 ("다진 청양고추", "홍고추채")</li>
 * </ul>
 */
public final class SpicyRecipeDetector {

    private SpicyRecipeDetector() {
    }

    /**
     * @param ingredientTexts  레시피별 재료 이름들 (사전 이름·레시피에 적힌 이름)
     * @param spicyIngredients 매운 재료 이름 목록
     * @return 매운 재료가 들어 있는 레시피 ID
     */
    public static Set<Long> spicyRecipeIds(Map<Long, List<String>> ingredientTexts, Collection<String> spicyIngredients) {
        List<String> spicy = spicyIngredients.stream().map(SpicyRecipeDetector::compact).filter(name -> !name.isEmpty()).toList();
        return ingredientTexts.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(name -> isSpicy(name, spicy)))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isSpicy(String name, List<String> spicy) {
        String text = compact(name);
        return spicy.stream().anyMatch(text::contains);
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}
