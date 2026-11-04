package org.example.takecareofmyfridgebe.domain.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

// 상담 공통 시스템 프롬프트(prompts/chat-system.txt)에 꼭 있어야 하는 지시
class ChatSystemPromptTest {

    @Test
    @DisplayName("일반 답·추천 답 모두 마크다운을 쓰지 말고 번호와 줄바꿈만 쓰라고 지시한다")
    void forbidsMarkdown() throws IOException {
        String prompt = new ClassPathResource("prompts/chat-system.txt").getContentAsString(StandardCharsets.UTF_8);

        assertThat(prompt).contains("마크다운(**굵게**, # 제목, - 목록 기호) 쓰지 않는다", "번호와 줄바꿈만 쓴다");
    }
}
