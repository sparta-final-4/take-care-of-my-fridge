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
 *   <li>INGREDIENT: 키워드(두 글자 이상)가 레시피 재료 이름과 같거나 재료 이름에 통째로 있음 ("메밀면" ↔ "메밀면", "버섯" ↔ "표고버섯").
 *       글자 일부만 겹치는 것("중국음식" ↔ "중국부추", "냉메밀" ↔ "메밀면")은 검색이 키워드를 쪼개 걸린 것이라 재료로 맞은 것으로 보지 않는다</li>
 *   <li>SPLIT: 키워드 검색으로 걸렸지만 위에 해당하지 않음 ("비빔국수" → "비빔쌀국수", "중국음식" → 재료 "중국부추"인 기스면)</li>
 *   <li>VECTOR: 키워드 검색이 0건이라 벡터 검색으로만 찾음</li>
 * </ol>
 */
public final class KeywordMatchClassifier {

    // 재료로 맞았다고 볼 키워드의 최소 글자 수. 한 글자("면", "콩")는 너무 많은 재료 이름에 들어 있다
    private static final int MIN_KEYWORD_LENGTH = 2;

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
            String word = compact(keyword);
            if (word.length() < MIN_KEYWORD_LENGTH) {
                continue;
            }
            for (String ingredient : ingredientNames) {
                if (compact(ingredient).contains(word)) {
                    return new KeywordMatch(Type.INGREDIENT, keyword, ingredient);
                }
            }
        }
        // 쪼개짐: 레시피 인덱스 분석기가 키워드를 글자 단위까지 쪼개 ES가 넓게 건 것("탕짜면" → "짜"로 간장게장,
        // "중국음식" → "중국"으로 재료 중국부추)을 키워드 일치로 세지 않는다 (분석기는 아직 그대로다. #599는 _name만 고쳤다).
        // 분석기가 고쳐져도 카드 이유를 맞은 방식대로 쓰려면 이 비교는 필요해서 남긴다.
        // 걷어낼 것은 이 결과를 보고 벡터 보충을 정하는 우회(RecipeCandidateFacade.findCandidates)다
        return KeywordMatch.of(vectorOnly ? Type.VECTOR : Type.SPLIT);
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\s+", "");
    }
}
