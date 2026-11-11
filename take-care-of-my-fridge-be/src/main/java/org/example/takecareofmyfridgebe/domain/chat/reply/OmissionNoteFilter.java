package org.example.takecareofmyfridgebe.domain.chat.reply;

import java.util.regex.Pattern;

/**
 * 레시피 자세히 답에서 "(뒤 단계 생략)", "(이하 생략)", "(생략)" 같은 생략 괄호 글을 지운다. 한 답(스트림)마다 새로 만들어 쓴다 (상태가 있다).
 * AI가 단계를 다 쓰고도 생략 표시를 붙이는 경우를 막는 안전장치다. 화면에 보낼 글과 저장할 글 모두 이 필터를 거친다.
 * 괄호가 조각 경계에서 쪼개져 와도 닫는 괄호가 올 때까지(최대 {@value #MAX_HELD}글자) 보내지 않고 모아 둔다.
 * "생략"이 없는 괄호 글("(약 2분)")은 그대로 보낸다.
 */
public class OmissionNoteFilter {

    // 앞 공백·줄바꿈까지 포함한 생략 괄호 글
    private static final Pattern OMISSION_NOTE = Pattern.compile("\\s*\\([^()]*생략[^()]*\\)");
    // 닫는 괄호를 기다리며 모아 둘 최대 글자 수 (넘으면 괄호 글이 아닌 것으로 보고 보낸다)
    static final int MAX_HELD = 30;

    private final StringBuilder buffer = new StringBuilder();

    /**
     * 받은 조각을 넣고 지금 보내도 되는 글을 돌려준다 (없으면 빈 문자열).
     */
    public String feed(String chunk) {
        buffer.append(chunk);
        String cleaned = OMISSION_NOTE.matcher(buffer).replaceAll("");
        buffer.setLength(0);
        int open = cleaned.lastIndexOf('(');
        boolean unclosed = open >= 0 && cleaned.indexOf(")", open) < 0 && cleaned.length() - open <= MAX_HELD;
        if (!unclosed) {
            return holdTrailingSpaces(cleaned);
        }
        // 닫히지 않은 괄호부터는 남겨 둔다 (앞 공백도 같이 지울 수 있게 공백부터)
        int holdFrom = open;
        while (holdFrom > 0 && Character.isWhitespace(cleaned.charAt(holdFrom - 1))) {
            holdFrom--;
        }
        buffer.append(cleaned, holdFrom, cleaned.length());
        return cleaned.substring(0, holdFrom);
    }

    // 스트림이 끝났을 때 남은 글 (닫히지 않은 괄호는 그대로 보낸다)
    public String finish() {
        String rest = OMISSION_NOTE.matcher(buffer).replaceAll("");
        buffer.setLength(0);
        return rest.stripTrailing();
    }

    // 끝 공백·줄바꿈은 다음 조각이 생략 괄호 글일 수 있어 남겨 둔다
    private String holdTrailingSpaces(String text) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        buffer.append(text, end, text.length());
        return text.substring(0, end);
    }
}
