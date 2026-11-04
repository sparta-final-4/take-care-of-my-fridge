package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch.Type;

import java.util.Collection;
import java.util.List;

/**
 * 요리 이름 키워드로 찾은 레시피가 어떻게 맞았는지 가른다. 스프링에 의존하지 않는 순수 클래스.
 * 검색(ES 이름·재료·소개)은 넓게 걸리므로, 카드 이유를 정확하게 쓰려고 이름·재료를 직접 비교한다. 공백은 빼고 비교한다.
 * <ol>
 *   <li>NAME: 사용자가 말한 요리 이름(dishText)이 레시피 이름에 통째로 있음 ("냉면" → "물냉면")</li>
 *   <li>EXPANDED: AI가 늘린 키워드가 레시피 이름에 통째로 있음 ("막국수" → "들기름 막국수")</li>
 *   <li>INGREDIENT: 키워드와 레시피 재료 이름이 두 글자 이상 겹치고, 겹친 말이 레시피 이름에는 없음 ("냉메밀" ↔ 재료 "메밀면").
 *       겹친 말이 이름에도 있으면("비빔국수" ↔ "비빔쌀국수"의 재료 "쌀국수면") 이름이 쪼개져 걸린 것으로 본다</li>
 *   <li>SPLIT: 키워드 검색으로 걸렸지만 위에 해당하지 않음 ("비빔국수" → "비빔쌀국수")</li>
 *   <li>VECTOR: 키워드 검색이 0건이라 벡터 검색으로만 찾음</li>
 * </ol>
 */
public final class KeywordMatchClassifier {

    // 재료 겹침으로 볼 최소 글자 수. 한 글자("면", "콩")는 너무 많이 겹친다
    private static final int MIN_COMMON_LENGTH = 2;

    private KeywordMatchClassifier() {
    }

    /**
     * @param dishText        사용자가 말한 요리 이름 (없으면 null)
     * @param keywords        AI가 늘린 키워드 전체
     * @param vectorOnly      키워드 검색이 아니라 벡터 검색으로만 찾았는지
     * @param recipeName      레시피 이름
     * @param ingredientNames 레시피 재료 이름 (기본 양념 제외)
     */
    public static KeywordMatch classify(String dishText, List<String> keywords, boolean vectorOnly,
                                        String recipeName, Collection<String> ingredientNames) {
        String name = compact(recipeName);
        if (dishText != null && !compact(dishText).isEmpty() && name.contains(compact(dishText))) {
            return KeywordMatch.of(Type.NAME);
        }
        for (String keyword : keywords) {
            if (!compact(keyword).isEmpty() && name.contains(compact(keyword))) {
                return new KeywordMatch(Type.EXPANDED, keyword, null);
            }
        }
        for (String keyword : keywords) {
            for (String ingredient : ingredientNames) {
                String common = commonWord(compact(keyword), compact(ingredient));
                if (common != null && !name.contains(common)) {
                    return new KeywordMatch(Type.INGREDIENT, keyword, ingredient);
                }
            }
        }
        return KeywordMatch.of(vectorOnly ? Type.VECTOR : Type.SPLIT);
    }

    // 두 이름이 MIN_COMMON_LENGTH 글자 이상 이어서 겹치는 말 ("냉메밀" ↔ "메밀면": "메밀"). 없으면 null
    static String commonWord(String a, String b) {
        if (a.length() < MIN_COMMON_LENGTH || b.length() < MIN_COMMON_LENGTH) {
            return null;
        }
        for (int i = 0; i + MIN_COMMON_LENGTH <= a.length(); i++) {
            String part = a.substring(i, i + MIN_COMMON_LENGTH);
            if (b.contains(part)) {
                return part;
            }
        }
        return null;
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\s+", "");
    }
}
