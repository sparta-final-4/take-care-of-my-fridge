package org.example.takecareofmyfridgebe.domain.chat.recommend;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.ChatIntentFixture;
import org.example.takecareofmyfridgebe.domain.member.entity.Member.Taste;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PickRuleTest {

    private static final Long MEMBER_ID = 1L;
    private static final RecipeCandidates EMPTY = new RecipeCandidates(List.of(), false);

    @Test
    @DisplayName("요리 이름·원하는 맛·분류를 말했으면 조건 수는 키워드 1 + 맛 수 + 분류 1이고, 반대 맛을 모은다")
    void countsSpokenConditions() {
        RecipeQuery query = new RecipeQuery(MEMBER_ID, List.of(), List.of(Taste.SPICY), List.of("국", "찌개"), null, null,
                false, List.of("닭볶음탕"), "닭볶음탕");

        PickRule rule = PickRule.of(query, EMPTY, ChatIntentFixture.TASTE_OPPOSITES::oppositesOf);

        assertThat(rule.requestCount()).isEqualTo(3);
        assertThat(rule.oppositeTastes()).containsExactly(Taste.MILD);
        assertThat(rule.categorySpoken()).isTrue();
    }

    @Test
    @DisplayName("조건 없이 추천해 달라고 하면 조건 수는 0이다")
    void hasNoRequest_whenNothingSpoken() {
        PickRule rule = PickRule.of(RecipeQuery.of(MEMBER_ID, List.of()), EMPTY,
                ChatIntentFixture.TASTE_OPPOSITES::oppositesOf);

        assertThat(rule.hasRequest()).isFalse();
    }
}
