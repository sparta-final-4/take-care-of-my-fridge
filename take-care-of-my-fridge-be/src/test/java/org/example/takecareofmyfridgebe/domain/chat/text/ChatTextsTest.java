package org.example.takecareofmyfridgebe.domain.chat.text;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatTextsTest {

    @Test
    @DisplayName("스페이스·줄바꿈·탭이 섞인 글에서 모든 공백을 지운다")
    void removesAllWhitespace_whenSpacesNewlinesAndTabsMixed() {
        assertThat(ChatTexts.removeWhitespace(" 밀키트\n추천\t해 줘\r\n")).isEqualTo("밀키트추천해줘");
    }

    @Test
    @DisplayName("null이면 빈 글을 돌려준다")
    void returnsEmpty_whenNull() {
        assertThat(ChatTexts.removeWhitespace(null)).isEmpty();
    }

    @Test
    @DisplayName("빈 글이나 공백만 있는 글이면 빈 글을 돌려준다")
    void returnsEmpty_whenBlank() {
        assertThat(ChatTexts.removeWhitespace("")).isEmpty();
        assertThat(ChatTexts.removeWhitespace(" \n\t ")).isEmpty();
    }

    @Test
    @DisplayName("공백이 없는 글은 그대로 돌려준다")
    void returnsSameText_whenNoWhitespace() {
        assertThat(ChatTexts.removeWhitespace("김치찌개")).isEqualTo("김치찌개");
    }
}
