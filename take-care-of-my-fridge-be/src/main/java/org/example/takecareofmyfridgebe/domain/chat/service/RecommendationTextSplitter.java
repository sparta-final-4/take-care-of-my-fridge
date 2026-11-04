package org.example.takecareofmyfridgebe.domain.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 추천 답 스트림을 표시([소개], [1]~[3])로 나눈다. 한 답(스트림)마다 새로 만들어 쓴다 (상태가 있다).
 * <ul>
 *   <li>[소개] 뒤 글은 소개(index -1), [n] 뒤 글은 n번째 카드 설명(index n-1)</li>
 *   <li>표시가 조각 경계에서 쪼개져 와도("[소" + "개]") 표시가 될 수 있는 끝부분은 버퍼에 남겼다가 다음 조각과 합쳐 본다</li>
 *   <li>답 첫머리에 [소개]가 없으면 표시를 빼먹은 답으로 보고, 전체를 소개로 보낸다 (카드 설명은 비움)</li>
 *   <li>카드 수보다 큰 번호의 글은 버린다. 표시 바로 뒤 공백·줄바꿈과 다음 표시 바로 앞 공백·줄바꿈은 보내지 않는다</li>
 * </ul>
 */
public class RecommendationTextSplitter {

    public static final int INTRO = -1;
    static final String INTRO_MARKER = "[소개]";
    // 표시: [소개] 또는 [1]~[9]
    private static final Pattern MARKER = Pattern.compile("\\[(소개|[1-9])]");
    // 표시가 될 수 있는 끝부분 최대 길이 ("[소개" = 3글자)
    private static final int MAX_PARTIAL_MARKER = INTRO_MARKER.length() - 1;

    private enum Mode { DETECTING, MARKED, UNMARKED }

    private final int cardCount;
    private final StringBuilder buffer = new StringBuilder();
    private Mode mode = Mode.DETECTING;
    private int section = INTRO;
    // 지금 칸에서 글을 하나라도 보냈는지 (칸 첫머리의 공백·줄바꿈은 보내지 않는다)
    private boolean sectionStarted = false;

    public RecommendationTextSplitter(int cardCount) {
        this.cardCount = cardCount;
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
        if (mode == Mode.DETECTING && !detect()) {
            return pieces;
        }
        if (mode == Mode.UNMARKED) {
            emit(pieces, INTRO, take(buffer.length()), true);
            return pieces;
        }
        drain(pieces, false);
        return pieces;
    }

    // 스트림이 끝났을 때 버퍼에 남은 글을 보낸다
    public List<Piece> finish() {
        List<Piece> pieces = new ArrayList<>();
        if (mode == Mode.DETECTING) {
            // 짧은 답이라 [소개]인지 가리기 전에 끝났다. 표시의 앞부분("[소개")만 왔으면 보낼 글이 없다
            String head = buffer.toString().strip();
            if (INTRO_MARKER.startsWith(head)) {
                buffer.setLength(0);
                return pieces;
            }
            mode = head.startsWith(INTRO_MARKER) ? Mode.MARKED : Mode.UNMARKED;
            if (mode == Mode.MARKED) {
                skipIntroMarker();
            }
        }
        if (mode == Mode.UNMARKED) {
            emit(pieces, INTRO, take(buffer.length()), false);
            return pieces;
        }
        drain(pieces, true);
        return pieces;
    }

    // 첫머리가 [소개]인지 가린다. 아직 모르면 false
    private boolean detect() {
        String head = buffer.toString().stripLeading();
        if (head.length() < INTRO_MARKER.length()) {
            if (INTRO_MARKER.startsWith(head)) {
                return false;
            }
            mode = Mode.UNMARKED;
            return true;
        }
        if (head.startsWith(INTRO_MARKER)) {
            mode = Mode.MARKED;
            skipIntroMarker();
        } else {
            mode = Mode.UNMARKED;
        }
        return true;
    }

    private void skipIntroMarker() {
        int at = buffer.indexOf(INTRO_MARKER);
        buffer.delete(0, at + INTRO_MARKER.length());
        section = INTRO;
        sectionStarted = false;
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
                section = INTRO_MARKER.equals("[" + label + "]") ? INTRO : Integer.parseInt(label) - 1;
                sectionStarted = false;
                continue;
            }
            int keep = end ? 0 : partialMarkerLength();
            emit(pieces, section, take(buffer.length() - keep), !end);
            return;
        }
    }

    // 버퍼 끝에서 표시의 앞부분일 수 있는 길이 ("[", "[소", "[소개", "[2")
    private int partialMarkerLength() {
        int open = buffer.lastIndexOf("[");
        if (open < 0 || buffer.length() - open > MAX_PARTIAL_MARKER) {
            return 0;
        }
        String tail = buffer.substring(open);
        boolean prefix = INTRO_MARKER.startsWith(tail) || tail.matches("\\[[1-9]?");
        return prefix ? buffer.length() - open : 0;
    }

    private String take(int length) {
        String text = buffer.substring(0, length);
        buffer.delete(0, length);
        return text;
    }

    // 칸 첫머리 공백은 빼고, holdTrailing이면 끝 공백·줄바꿈은 다음 글이 올 때까지 버퍼 앞에 되돌려 둔다
    private void emit(List<Piece> pieces, int index, String text, boolean holdTrailing) {
        String out = sectionStarted ? text : text.stripLeading();
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
