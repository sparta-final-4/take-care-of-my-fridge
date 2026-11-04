package org.example.takecareofmyfridgebe.domain.chat.service;

import org.example.takecareofmyfridgebe.domain.chat.service.RecommendationTextSplitter.Piece;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationTextSplitterTest {

    @Test
    @DisplayName("[소개] 글은 소개(-1), [n] 글은 n-1번 카드 설명으로 나누고 표시 앞뒤 줄바꿈은 빼며 글 안의 줄바꿈은 남긴다")
    void splitsIntroAndCards() {
        Map<Integer, String> result = split(3,
                "[소개] 면 요리를 골랐어요.\n[1] 메밀면을 써요.\n두 줄이에요.\n[2] 찾으신 냉면이에요.\n[3] 파스타예요.\n");

        assertThat(result).containsExactly(
                Map.entry(-1, "면 요리를 골랐어요."),
                Map.entry(0, "메밀면을 써요.\n두 줄이에요."),
                Map.entry(1, "찾으신 냉면이에요."),
                Map.entry(2, "파스타예요."));
    }

    @Test
    @DisplayName("표시가 조각 경계에서 쪼개져 와도 표시로 알아보고 표시 글자를 보내지 않는다")
    void handlesMarkersSplitAcrossChunks() {
        Map<Integer, String> result = split(2, "[", "소", "개] 소개", "예요.\n[", "1", "] 첫 카드", "예요.\n[2", "] 둘째 카드예요.");

        assertThat(result).containsExactly(
                Map.entry(-1, "소개예요."),
                Map.entry(0, "첫 카드예요."),
                Map.entry(1, "둘째 카드예요."));
        assertThat(allText(2, "[", "소", "개] 소개", "예요.\n[", "1", "] 첫 카드")).doesNotContain("[", "]");
    }

    @Test
    @DisplayName("첫머리에 [소개]가 없으면 표시를 빼먹은 답으로 보고 전체를 소개로 보낸다")
    void sendsEverythingAsIntro_whenFirstMarkerMissing() {
        Map<Integer, String> result = split(3, "면 요리를 ", "골랐어요. [1] 이건 그대로예요.");

        assertThat(result).containsExactly(Map.entry(-1, "면 요리를 골랐어요. [1] 이건 그대로예요."));
    }

    @Test
    @DisplayName("카드 수보다 큰 번호의 글은 버린다")
    void dropsTextBeyondCardCount() {
        Map<Integer, String> result = split(1, "[소개] 소개.\n[1] 하나.\n[2] 없는 카드.");

        assertThat(result).containsExactly(Map.entry(-1, "소개."), Map.entry(0, "하나."));
    }

    @Test
    @DisplayName("짧게 끝난 답도 끝날 때 남은 글을 보낸다")
    void flushesOnFinish() {
        assertThat(split(1, "[소개")).isEmpty();
        assertThat(split(1, "짧은 답")).containsExactly(Map.entry(-1, "짧은 답"));
    }

    private static Map<Integer, String> split(int cardCount, String... chunks) {
        return pieces(cardCount, chunks).stream()
                .collect(Collectors.groupingBy(Piece::index, TreeMap::new, Collectors.mapping(Piece::text, Collectors.joining())));
    }

    private static String allText(int cardCount, String... chunks) {
        return pieces(cardCount, chunks).stream().map(Piece::text).collect(Collectors.joining());
    }

    private static List<Piece> pieces(int cardCount, String... chunks) {
        RecommendationTextSplitter splitter = new RecommendationTextSplitter(cardCount);
        List<Piece> pieces = new ArrayList<>();
        for (String chunk : chunks) {
            pieces.addAll(splitter.feed(chunk));
        }
        pieces.addAll(splitter.finish());
        return pieces;
    }
}
