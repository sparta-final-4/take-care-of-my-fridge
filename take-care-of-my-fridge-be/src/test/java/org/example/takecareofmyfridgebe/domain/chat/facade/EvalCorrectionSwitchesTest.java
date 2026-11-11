package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.config.ChatCorrectionProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvalCorrectionSwitchesTest {

    @Test
    @DisplayName("끌 설정이 없으면 모두 켜짐")
    void allOn_whenEmpty() {
        assertThat(EvalCorrectionSwitches.properties(EvalCorrectionSwitches.parse(null))).isEqualTo(ChatCorrectionProperties.ALL_ON);
        assertThat(EvalCorrectionSwitches.describe(Set.of())).isEqualTo("보정: 모두 켜짐");
    }

    @Test
    @DisplayName("적은 보정만 끈다")
    void turnsOffListed() {
        Set<String> off = EvalCorrectionSwitches.parse("taste-words, spoken-tastes-only");

        ChatCorrectionProperties properties = EvalCorrectionSwitches.properties(off);

        assertThat(properties.tasteWords()).isFalse();
        assertThat(properties.spokenTastesOnly()).isFalse();
        assertThat(properties.keepOtherTastes()).isTrue();
        assertThat(properties.dishCheck()).isTrue();
        assertThat(EvalCorrectionSwitches.describe(off)).isEqualTo("보정: 끈 설정 taste-words, spoken-tastes-only");
    }

    @Test
    @DisplayName("모르는 이름은 오타로 보고 멈춘다")
    void rejectsUnknownName() {
        assertThatThrownBy(() -> EvalCorrectionSwitches.parse("taste-word"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taste-word");
    }

    @Test
    @DisplayName("이름 목록이 설정 수와 같다")
    void namesMatchProperties() {
        assertThat(EvalCorrectionSwitches.NAMES).hasSize(ChatCorrectionProperties.class.getRecordComponents().length);
    }
}
