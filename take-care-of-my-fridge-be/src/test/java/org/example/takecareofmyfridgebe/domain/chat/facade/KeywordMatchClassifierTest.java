package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch.Type;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordMatchClassifierTest {

    private static final List<String> KEYWORDS = List.of("냉면", "막국수", "콩국수", "냉메밀", "냉파스타", "비빔국수");

    @Test
    @DisplayName("사용자가 말한 이름이 공백을 빼고 레시피 이름에 통째로 있으면 이름으로 맞았다")
    void classifiesName() {
        assertThat(classify("함흥 냉 면", List.of(), false).type()).isEqualTo(Type.NAME);
    }

    @Test
    @DisplayName("AI가 늘린 키워드가 레시피 이름에 있으면 늘린 이름으로 맞았고 그 키워드를 담는다")
    void classifiesExpanded() {
        KeywordMatch match = classify("들기름 막국수", List.of("메밀면"), false);

        assertThat(match.type()).isEqualTo(Type.EXPANDED);
        assertThat(match.keyword()).isEqualTo("막국수");
    }

    @Test
    @DisplayName("키워드와 레시피 재료가 두 글자 이상 겹치면 재료로 맞았고 그 재료 이름을 담는다")
    void classifiesIngredient() {
        KeywordMatch match = classify("들깨 삼계면", List.of("닭", "메밀면", "들깨"), false);

        assertThat(match.type()).isEqualTo(Type.INGREDIENT);
        assertThat(match.ingredientName()).isEqualTo("메밀면");
    }

    @Test
    @DisplayName("검색으로 걸렸지만 이름·재료에 통째로 없으면 쪼개짐, 벡터 검색으로만 찾았으면 벡터다")
    void classifiesSplitOrVector() {
        assertThat(classify("비빔쌀국수", List.of("쌀국수면", "오이"), false).type()).isEqualTo(Type.SPLIT);
        assertThat(classify("들깨 칼제비", List.of("밀가루"), true).type()).isEqualTo(Type.VECTOR);
    }

    @Test
    @DisplayName("한 글자만 겹치는 재료(면, 콩)는 재료로 맞은 것으로 보지 않는다")
    void ignoresOneLetterOverlap() {
        assertThat(classify("콩자반", List.of("콩", "간장"), false).type()).isEqualTo(Type.SPLIT);
    }

    private static KeywordMatch classify(String recipeName, List<String> ingredients, boolean vectorOnly) {
        return KeywordMatchClassifier.classify("냉면", KEYWORDS, vectorOnly, recipeName, ingredients);
    }
}
