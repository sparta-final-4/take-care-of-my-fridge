package org.example.takecareofmyfridgebe.domain.chat.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetailQuestionTest {

    @Test
    @DisplayName("질문 종류가 있으면 사용자 말 대신 정해진 질문을 넘겨 \"두 번째\" 같은 번호가 들어가지 않는다")
    void usesFixedQuestion_whenTypeIsKnown() {
        assertThat(DetailQuestion.questionFor(DetailQuestion.HOW_TO, "두 번째 거 어떻게 만들어?"))
                .isEqualTo("이 레시피 만드는 법을 처음부터 끝까지 알려줘").doesNotContain("두 번째");
        assertThat(DetailQuestion.questionFor(DetailQuestion.TIME, "첫 번째 거는 시간 얼마나 걸려?"))
                .isEqualTo("이 레시피 조리 시간이 얼마나 걸려?");
        assertThat(DetailQuestion.questionFor(DetailQuestion.INGREDIENTS, "그거 재료 뭐 들어가?")).isEqualTo("이 레시피 재료를 알려줘");
        assertThat(DetailQuestion.questionFor(DetailQuestion.TIP, "세 번째 거 팁 있어?")).isEqualTo("이 레시피를 맛있게 만드는 팁을 알려줘");
    }

    @Test
    @DisplayName("그 밖의 질문(OTHER)이나 종류를 모르면 가리키는 말(두 번째 거·그거·아까 그)만 지우고 넘긴다")
    void removesPointers_whenOtherOrUnknown() {
        assertThat(DetailQuestion.questionFor(DetailQuestion.OTHER, "두 번째 거 몇 인분이야?")).isEqualTo("몇 인분이야?");
        assertThat(DetailQuestion.questionFor(null, "아까 그 깐풍기 소스는 어떻게 해?")).isEqualTo("깐풍기 소스는 어떻게 해?");
        assertThat(DetailQuestion.questionFor(DetailQuestion.OTHER, "그거 냉동해도 돼?")).isEqualTo("냉동해도 돼?");
        assertThat(DetailQuestion.questionFor(DetailQuestion.OTHER, "두 번째 거?")).isEqualTo("이 레시피에 대해 알려줘");
    }
}
