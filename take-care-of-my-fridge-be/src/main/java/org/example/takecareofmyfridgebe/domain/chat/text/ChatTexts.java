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

    /**
     * 줄임말의 글자가 정식 이름에 순서대로 다 있는지 (띄어쓰기 무시). 김볶밥 → 김치볶음밥, 된찌 → 된장찌개는 맞고, 김피탕 → 김치찌개는 맞지 않는다.
     * 비슷한 요리의 정식 이름 확인(SimilarDishFinder)과 OpenAI 요리 이름 확인(IntentCorrector.dishSpoken)이 같이 쓴다.
     */
    public static boolean containsInOrder(String formalName, String abbreviation) {
        String formal = removeWhitespace(formalName);
        int at = 0;
        for (char letter : removeWhitespace(abbreviation).toCharArray()) {
            at = formal.indexOf(letter, at);
            if (at < 0) {
                return false;
            }
            at++;
        }
        return true;
    }
}
