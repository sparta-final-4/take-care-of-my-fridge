package org.example.takecareofmyfridgebe.domain.chat.reply;

import java.util.regex.Pattern;

/**
 * AI 답에 섞인 링크(http://·https://·www.·도메인 형태)를 지운다. 한 답(스트림)마다 새로 만들어 쓴다 (상태가 있다).
 * 화면에 보낼 글과 저장할 글 모두 이 필터를 거친다. 링크만 지우고 "(링크 생략)" 같은 말은 넣지 않는다.
 * <ul>
 *   <li>끝에 남은 링크에 쓰일 수 있는 글자 묶음(영문·숫자와 :/._-?=&%#~+@)에 영문이 있으면, 그 묶음과 앞 공백은 다음 조각이 올 때까지
 *       (최대 {@value #MAX_HELD}글자) 보내지 않고 모아 둔다. "https:/" + "/naver.com"처럼 조각 경계에서 쪼개진 링크도 이어 붙인 뒤 지운다.
 *       한글과 영문 없는 묶음("." "?" "1.5")은 바로 보낸다 (숫자로만 된 도메인이 조각 경계에서 쪼개지면 놓칠 수 있다)</li>
 *   <li>도메인 형태는 정해진 끝(.com .net .org .kr .co.kr .io .me .shop .info .app .dev .ly)만 본다. "1.5배", "3.5cm", "약 2.5kg"은 그대로다</li>
 *   <li>링크 앞 공백도 같이 지우고, 링크만 있던 괄호("(https://a.com)")는 괄호째 지운다</li>
 * </ul>
 */
public class LinkFilter {

    // 모아 둘 최대 글자 수 (넘으면 링크가 아닌 긴 영문·숫자로 보고 보낸다)
    static final int MAX_HELD = 300;

    private static final String URL_CHARS = "A-Za-z0-9:/._\\-?=&%#~+@";
    private static final Pattern LINK = Pattern.compile(
            "[ \\t]*(?:"
                    + "(?i:https?://|www\\.)[" + URL_CHARS + "]*"
                    + "|(?<![A-Za-z0-9.@\\-])[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)*"
                    + "\\.(?i:co\\.kr|com|net|org|kr|io|me|shop|info|app|dev|ly)(?![A-Za-z0-9\\-])(?:/[" + URL_CHARS + "]*)?"
                    + ")");
    // 링크를 지우고 남은 빈 괄호
    private static final Pattern EMPTY_PARENTHESES = Pattern.compile("[ \\t]*\\([ \\t]*\\)");
    private static final Pattern URL_CHAR = Pattern.compile("[" + URL_CHARS + "]");

    private final StringBuilder buffer = new StringBuilder();
    // 지금까지 보낸 글이 공백으로 끝났는지 (다음 글이 링크로 시작하면 지운 뒤 앞 공백을 빼서 공백이 겹치지 않게)
    private boolean sentEndsWithSpace;

    /**
     * 받은 조각을 넣고 지금 보내도 되는 글(링크를 지운 글)을 돌려준다 (없으면 빈 문자열).
     */
    public String feed(String chunk) {
        buffer.append(chunk);
        int holdFrom = holdStart(buffer);
        if (buffer.length() - holdFrom > MAX_HELD) {
            holdFrom = buffer.length();
        }
        String ready = buffer.substring(0, holdFrom);
        buffer.delete(0, holdFrom);
        return send(ready);
    }

    // 스트림이 끝났을 때 남은 글 (링크를 지운 글)
    public String finish() {
        String rest = buffer.toString();
        buffer.setLength(0);
        return send(rest);
    }

    private String send(String text) {
        if (text.isEmpty()) {
            return text;
        }
        String cleaned = strip(text);
        if (sentEndsWithSpace && cleaned.length() < text.length() && LINK.matcher(text).lookingAt()) {
            cleaned = cleaned.replaceFirst("^[ \\t]+", "");
        }
        if (!cleaned.isEmpty()) {
            sentEndsWithSpace = Character.isWhitespace(cleaned.charAt(cleaned.length() - 1));
        }
        return cleaned;
    }

    static String strip(String text) {
        if (text.isEmpty()) {
            return text;
        }
        String removed = LINK.matcher(text).replaceAll("");
        if (removed.length() == text.length()) {
            return text;
        }
        return EMPTY_PARENTHESES.matcher(removed).replaceAll("");
    }

    // 모아 둘 곳의 시작: 끝의 링크 글자 묶음과 그 앞 공백. 묶음에 영문이 없으면("." "?" "1.5") 링크가 시작될 수 없어 모아 두지 않는다
    private static int holdStart(CharSequence text) {
        int end = text.length();
        int start = end;
        boolean hasLetter = false;
        while (start > 0 && isUrlChar(text.charAt(start - 1))) {
            hasLetter |= isAsciiLetter(text.charAt(start - 1));
            start--;
        }
        if (!hasLetter) {
            return end;
        }
        while (start > 0 && Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return start;
    }

    private static boolean isUrlChar(char c) {
        return URL_CHAR.matcher(String.valueOf(c)).matches();
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }
}
