package org.example.takecareofmyfridgebe.domain.chat.text;

import java.util.regex.Pattern;

/**
 * 상담 코드가 같이 쓰는 글 다듬기. 스프링에 의존하지 않는 순수 클래스.
 * 단어 비교는 공백을 무시하므로("매운 거" = "매운거") 비교 전에 모든 공백을 지운다.
 * 다른 도메인에서도 필요해지면 global로 옮긴다.
 */
public final class ChatTexts {

    // 스페이스·탭·줄바꿈(\n, \r)·\f·\x0B. 전각 공백(U+3000)은 지우지 않는다
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ChatTexts() {
    }

    /**
     * 모든 공백을 지운다. null이면 빈 글을 돌려준다.
     * 예) "김치 찌개" → "김치찌개", "밀키트\n추천해줘" → "밀키트추천해줘"
     */
    public static String removeWhitespace(String text) {
        return text == null ? "" : WHITESPACE.matcher(text).replaceAll("");
    }
}
