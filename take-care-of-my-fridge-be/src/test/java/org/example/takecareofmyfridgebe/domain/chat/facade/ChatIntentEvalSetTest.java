package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.EvalCase;
import org.example.takecareofmyfridgebe.domain.chat.facade.ChatIntentEvalManualTest.EvalSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 의도 평가 세트 고르기 (OPENAI_INTENT_EVAL_SET). 파일만 읽고 AI는 부르지 않는다.
 */
class ChatIntentEvalSetTest {

    @Test
    @DisplayName("평가 세트 옵션이 없거나 main이면 기본 세트 파일을 고른다")
    void choosesMainSet_whenEmptyOrMain() {
        assertThat(EvalSet.resource(null)).isEqualTo("chat-intent-eval.yml");
        assertThat(EvalSet.resource("")).isEqualTo("chat-intent-eval.yml");
        assertThat(EvalSet.resource("main")).isEqualTo("chat-intent-eval.yml");
    }

    @Test
    @DisplayName("holdout을 고르면 chat-intent-holdout.yml을 읽어 기본 세트에 없는 문장 15개를 돌린다")
    void loadsHoldoutSet_whenHoldout() throws IOException {
        EvalSet holdout = EvalSet.load(EvalSet.resource("holdout"));
        Set<String> mainMessages = EvalSet.load().allCases().stream().map(EvalCase::message).collect(Collectors.toSet());

        List<EvalCase> cases = holdout.cases(Set.of());

        assertThat(EvalSet.resource("holdout")).isEqualTo("chat-intent-holdout.yml");
        assertThat(cases).hasSize(15);
        assertThat(cases).extracting(EvalCase::message).doesNotContainAnyElementsOf(mainMessages);
        assertThat(holdout.contexts()).containsOnlyKeys("pasta", "braise");
    }

    @Test
    @DisplayName("모르는 평가 세트 이름은 오타로 보고 멈춘다")
    void throwsIllegalArgument_whenUnknownSet() {
        assertThatThrownBy(() -> EvalSet.resource("hold-out"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hold-out");
    }
}
