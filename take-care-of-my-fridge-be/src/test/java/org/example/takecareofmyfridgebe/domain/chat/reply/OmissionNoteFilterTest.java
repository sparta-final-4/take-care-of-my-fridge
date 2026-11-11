package org.example.takecareofmyfridgebe.domain.chat.reply;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OmissionNoteFilterTest {

    @Test
    @DisplayName("답 끝의 \"(뒤 단계 생략)\"은 조각이 쪼개져 와도 앞 줄바꿈까지 지운다")
    void removesTrailingOmissionNote_acrossChunks() {
        assertThat(filter("1. 썬다.\n2. 볶는다.\n3. 끓인다.\n4. 담는다.", "\n(뒤 단", "계 생략)"))
                .isEqualTo("1. 썬다.\n2. 볶는다.\n3. 끓인다.\n4. 담는다.");
    }

    @Test
    @DisplayName("(이하 생략)·(생략)도 지우고, 생략이 없는 괄호 글(약 2분)은 그대로 둔다")
    void removesOnlyOmissionParentheses() {
        assertThat(filter("1. 약불에서 (약 2분) 볶는다. (이하 생략)")).isEqualTo("1. 약불에서 (약 2분) 볶는다.");
        assertThat(filter("4. 담는다. (생략)")).isEqualTo("4. 담는다.");
        assertThat(filter("1. 썬다(", "잘게) 뒤 볶는다.")).isEqualTo("1. 썬다(잘게) 뒤 볶는다.");
    }

    private static String filter(String... chunks) {
        OmissionNoteFilter filter = new OmissionNoteFilter();
        StringBuilder out = new StringBuilder();
        for (String chunk : chunks) {
            out.append(filter.feed(chunk));
        }
        return out.append(filter.finish()).toString();
    }
}
