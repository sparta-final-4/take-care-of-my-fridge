package org.example.takecareofmyfridgebe.domain.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 추천 답 스트림을 표시([소개], [1]~[3])로 나눈다. 한 답(스트림)마다 새로 만들어 쓴다 (상태가 있다).
 * <ul>
 *   <li>[n] 뒤 글은 n번째 카드 설명(index n-1), [소개] 뒤 글과 첫 표시 앞 글은 소개(index -1). 표시는 글 어디서든 찾는다</li>
 *   <li>표시가 조각 경계에서 쪼개져 와도("[소" + "개]") 표시가 될 수 있는 끝부분은 버퍼에 남겼다가 다음 조각과 합쳐 본다</li>
 *   <li>어떤 표시도 끝까지 나오지 않으면 전체가 소개가 된다 (카드 설명은 비움)</li>
 *   <li>나누고 남은 대괄호 표시([다른 제안], [이미 안내한 말] 등)는 지우고 보낸다</li>
 *   <li>서버가 이미 보낸 안내 문장을 AI가 소개 첫머리에 되풀이하면 그 문장은 뺀다</li>
 *   <li>카드 수보다 큰 번호의 글은 버린다. 표시 바로 뒤 공백·줄바꿈과 다음 표시 바로 앞 공백·줄바꿈은 보내지 않는다</li>
 * </ul>
 */
public class RecommendationTextSplitter {

    public static final int INTRO = -1;
    static final String INTRO_MARKER = "[소개]";
    // 나누는 표시: [소개] 또는 [1]~[9]
    private static final Pattern MARKER = Pattern.compile("\\[(소개|[1-9])]");
    // 나눈 뒤 남은 대괄호 표시 ([다른 제안], [이미 안내한 말] 등). 뒤따르는 공백까지 지운다
    private static final Pattern LEFTOVER_MARKER = Pattern.compile("\\[[^\\[\\]\\n]{1,10}][ \\t]*");
    // 표시가 될 수 있는 닫히지 않은 끝부분 최대 길이 ("[" + 10글자)
    private static final int MAX_PARTIAL_MARKER = 11;
    // 되풀이한 안내 문장 뒤에 붙은 문장부호·공백
    private static final Pattern SENTENCE_END = Pattern.compile("^[.!?。\\s]*");

    private final int cardCount;
    private final List<String> alreadySaid;
    private final StringBuilder buffer = new StringBuilder();
    private int section = INTRO;
    // 지금 칸에서 글을 하나라도 보냈는지 (칸 첫머리의 공백·줄바꿈은 보내지 않는다)
    private boolean sectionStarted = false;
    // 소개 첫머리에서 되풀이한 안내 문장을 다 걸렀는지
    private boolean repeatChecked;

    public RecommendationTextSplitter(int cardCount) {
        this(cardCount, List.of());
    }

    /**
     * @param alreadySaid 서버가 소개 앞에 이미 보낸 안내 문장 (AI가 되풀이하면 뺀다)
     */
    public RecommendationTextSplitter(int cardCount, List<String> alreadySaid) {
        this.cardCount = cardCount;
        this.alreadySaid = alreadySaid.stream().map(String::strip).filter(line -> !line.isEmpty()).toList();
        this.repeatChecked = this.alreadySaid.isEmpty();
    }

    /**
     * 나눈 글 한 조각.
     *
     * @param index 소개면 -1, 카드 설명이면 카드 순서(0부터)
     */
    public record Piece(int index, String text) {
        public boolean intro() {
            return index == INTRO;
        }
    }

    public List<Piece> feed(String chunk) {
        buffer.append(chunk);
        List<Piece> pieces = new ArrayList<>();
        if (!repeatChecked && !dropRepeatedNotice(false)) {
            return pieces;
        }
        drain(pieces, false);
        return pieces;
    }

    // 스트림이 끝났을 때 버퍼에 남은 글을 보낸다
    public List<Piece> finish() {
        List<Piece> pieces = new ArrayList<>();
        if (!repeatChecked) {
            dropRepeatedNotice(true);
        }
        drain(pieces, true);
        return pieces;
    }

    // 소개 첫머리(공백·[소개] 뒤)에 이미 보낸 안내 문장이 있으면 뺀다. 아직 가릴 수 없으면 false (더 받아 본다)
    private boolean dropRepeatedNotice(boolean end) {
        while (true) {
            String text = buffer.toString();
            int start = headStart(text);
            if (start < 0) {
                if (end) {
                    repeatChecked = true;
                    return true;
                }
                return false;
            }
            String head = text.substring(start);
            String repeated = alreadySaid.stream().filter(head::startsWith).findFirst().orElse(null);
            if (repeated != null) {
                int after = start + repeated.length();
                Matcher ending = SENTENCE_END.matcher(text.substring(after));
                ending.find();
                buffer.delete(start, after + ending.end());
                continue;
            }
            boolean mayRepeat = alreadySaid.stream().anyMatch(line -> line.startsWith(head) && head.length() < line.length());
            if (mayRepeat && !end) {
                return false;
            }
            repeatChecked = true;
            return true;
        }
    }

    // 소개 글이 시작하는 위치 (앞 공백과 [소개] 건너뜀). 아직 [소개]의 앞부분만 왔으면 -1
    private static int headStart(String text) {
        int start = skipSpaces(text, 0);
        String rest = text.substring(start);
        if (rest.startsWith(INTRO_MARKER)) {
            return skipSpaces(text, start + INTRO_MARKER.length());
        }
        return INTRO_MARKER.startsWith(rest) ? -1 : start;
    }

    private static int skipSpaces(String text, int from) {
        int at = from;
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
        return at;
    }

    // 버퍼에서 표시를 찾아 칸을 바꾸며 글을 보낸다. 끝이 아니면 표시가 될 수 있는 끝부분은 남긴다
    private void drain(List<Piece> pieces, boolean end) {
        while (true) {
            Matcher matcher = MARKER.matcher(buffer);
            if (matcher.find()) {
                // 버퍼를 고치기 전에 표시 위치·글자를 먼저 읽어 둔다 (Matcher는 버퍼를 그대로 본다)
                String label = matcher.group(1);
                int start = matcher.start();
                int markerLength = matcher.end() - start;
                emit(pieces, section, take(start), false);
                buffer.delete(0, markerLength);
                int next = INTRO_MARKER.equals("[" + label + "]") ? INTRO : Integer.parseInt(label) - 1;
                // 첫 표시 앞 소개 글 뒤에 [소개]가 오면 같은 소개로 이어 쓴다 (한 칸 띄움)
                if (next == section && sectionStarted) {
                    if (!buffer.isEmpty() && !Character.isWhitespace(buffer.charAt(0))) {
                        buffer.insert(0, ' ');
                    }
                } else {
                    section = next;
                    sectionStarted = false;
                }
                continue;
            }
            int keep = end ? partialKnownMarkerLength() : partialMarkerLength();
            emit(pieces, section, take(buffer.length() - keep), !end);
            if (end) {
                buffer.setLength(0);
            }
            return;
        }
    }

    // 버퍼 끝에 닫히지 않은 "["로 시작하는 짧은 끝부분 ("[", "[소", "[2", "[다른 제")
    private int partialMarkerLength() {
        int open = buffer.lastIndexOf("[");
        if (open < 0 || buffer.length() - open > MAX_PARTIAL_MARKER || buffer.indexOf("]", open) >= 0) {
            return 0;
        }
        return buffer.length() - open;
    }

    // 끝났을 때 남은 "[소개", "[2" 같은 표시 앞부분은 보내지 않는다
    private int partialKnownMarkerLength() {
        int length = partialMarkerLength();
        if (length == 0) {
            return 0;
        }
        String tail = buffer.substring(buffer.length() - length);
        return INTRO_MARKER.startsWith(tail) || tail.matches("\\[[1-9]?") ? length : 0;
    }

    private String take(int length) {
        String text = buffer.substring(0, length);
        buffer.delete(0, length);
        return text;
    }

    // 남은 표시를 지우고, 칸 첫머리 공백은 빼고, holdTrailing이면 끝 공백·줄바꿈은 다음 글이 올 때까지 버퍼 앞에 되돌려 둔다
    private void emit(List<Piece> pieces, int index, String text, boolean holdTrailing) {
        String cleaned = LEFTOVER_MARKER.matcher(text).replaceAll("");
        String out = sectionStarted ? cleaned : cleaned.stripLeading();
        if (holdTrailing || !out.isEmpty()) {
            String stripped = out.stripTrailing();
            String trailing = out.substring(stripped.length());
            out = stripped;
            if (holdTrailing && !trailing.isEmpty()) {
                buffer.insert(0, trailing);
            }
        }
        if (out.isEmpty() || (index != INTRO && index >= cardCount)) {
            return;
        }
        sectionStarted = true;
        pieces.add(new Piece(index, out));
    }
}
