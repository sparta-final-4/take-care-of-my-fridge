package org.example.takecareofmyfridgebe.domain.chat.reply;

import org.example.takecareofmyfridgebe.domain.chat.reply.RecommendationTextSplitter.Piece;
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
    @DisplayName("어떤 표시도 끝까지 나오지 않으면 전체를 소개로 보낸다")
    void sendsEverythingAsIntro_whenNoMarker() {
        Map<Integer, String> result = split(3, "면 요리를 ", "골랐어요. 맛있게 ", "드세요.");

        assertThat(result).containsExactly(Map.entry(-1, "면 요리를 골랐어요. 맛있게 드세요."));
    }

    @Test
    @DisplayName("[소개] 앞에 글이 있으면 버리지 않고 소개에 합치고, [1]~[3]은 글 어디서든 찾아 나눈다")
    void mergesTextBeforeIntroMarker() {
        Map<Integer, String> result = split(3, "말씀하신 두부는 지금 냉장고에 없어서 준비가 필요해요.\n[소", "개] 버섯과 두부를 활용한 요리예요.",
                " [1] 된장국이에요. [", "2] 두부조림이에요.", " [3", "] 버섯전이에요.");

        assertThat(result).containsExactly(
                Map.entry(-1, "말씀하신 두부는 지금 냉장고에 없어서 준비가 필요해요. 버섯과 두부를 활용한 요리예요."),
                Map.entry(0, "된장국이에요."),
                Map.entry(1, "두부조림이에요."),
                Map.entry(2, "버섯전이에요."));
    }

    @Test
    @DisplayName("[소개] 없이 [1]부터 써도 앞 글은 소개, 표시 뒤 글은 카드 설명으로 나눈다")
    void splitsCards_whenIntroMarkerMissing() {
        Map<Integer, String> result = split(2, "면 요리를 골랐어요.\n[1] 냉면이에요.\n[2] 막국수예요.");

        assertThat(result).containsExactly(
                Map.entry(-1, "면 요리를 골랐어요."), Map.entry(0, "냉면이에요."), Map.entry(1, "막국수예요."));
    }

    @Test
    @DisplayName("설명 안에 남은 [다른 제안] 같은 대괄호 표시는 조각 경계에서 쪼개져 와도 지우고 보낸다")
    void removesLeftoverMarkers() {
        Map<Integer, String> result = split(3, "[소개] [이미 안내한 말] 튀김을 골랐어요.\n[1] 새콤해요.\n[2] 매콤해요.\n[3] [다른",
                " 제안] 색다르게 장어조림도 곁들여 보세요. [다른 제안]");

        assertThat(result).containsExactly(
                Map.entry(-1, "튀김을 골랐어요."),
                Map.entry(0, "새콤해요."),
                Map.entry(1, "매콤해요."),
                Map.entry(2, "색다르게 장어조림도 곁들여 보세요."));
        assertThat(allText(3, "[소개] 소개.\n[1] 하나. [다른 제안] 둘.")).doesNotContain("[", "]").contains("하나. 둘.");
    }

    @Test
    @DisplayName("서버가 이미 보낸 안내 문장을 AI가 소개 첫머리에 되풀이하면 뺀다")
    void dropsRepeatedNotice() {
        List<String> notices = List.of("말씀하신 두부는 지금 냉장고에 없어서 준비가 필요해요");

        assertThat(split(1, notices, "말씀하신 두부는 지금 ", "냉장고에 없어서 준비가 필요해요.\n[소개] 버섯 요리예요.\n[1] 된장국이에요."))
                .containsExactly(Map.entry(-1, "버섯 요리예요."), Map.entry(0, "된장국이에요."));
        assertThat(split(1, notices, "[소개] 말씀하신 두부는 지금 냉장고에 없어서 준비가 필요해요. 버섯 요리예요."))
                .containsExactly(Map.entry(-1, "버섯 요리예요."));
        assertThat(split(1, notices, "[소개] 말씀하신 버섯 요리예요."))
                .containsExactly(Map.entry(-1, "말씀하신 버섯 요리예요."));
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

    @Test
    @DisplayName("카드 설명 첫 문장이 레시피 이름뿐이면(\"소고기채소불고기예요.\") 공백을 무시하고 문장째 지운다, 조각이 쪼개져 와도")
    void dropsLeadingNameSentence() {
        List<Piece> pieces = piecesWithNames(List.of("소고기채소불고기", "깐풍기"),
                "[소개] 골랐어요.\n[1] 소고기 채소", "불고기예요. 달", "콤하게 볶아요.\n[2] 깐풍기는 바삭해요.");

        assertThat(group(pieces)).containsExactly(
                Map.entry(-1, "골랐어요."), Map.entry(0, "달콤하게 볶아요."), Map.entry(1, "바삭해요."));
        assertThat(pieces).extracting(Piece::text).noneMatch(text -> text.contains("불고기") || text.contains("깐풍기"));
    }

    @Test
    @DisplayName("이름 뒤가 문장 끝이나 주어 조사가 아니면(\"두부조림처럼\") 지우지 않고, 이름으로 시작하지 않는 설명은 바로 보낸다")
    void keepsDescription_whenNotStartingWithNameSentence() {
        assertThat(group(piecesWithNames(List.of("두부조림"), "[1] 두부조림처럼 짭조름해요.")))
                .containsExactly(Map.entry(0, "두부조림처럼 짭조름해요."));

        RecommendationTextSplitter splitter = new RecommendationTextSplitter(1, List.of(), List.of("깐풍기"));
        assertThat(splitter.feed("[1] 바삭하게 튀겨 ")).extracting(Piece::text).containsExactly("바삭하게 튀겨");
    }

    private static List<Piece> piecesWithNames(List<String> names, String... chunks) {
        RecommendationTextSplitter splitter = new RecommendationTextSplitter(names.size(), List.of(), names);
        List<Piece> pieces = new ArrayList<>();
        for (String chunk : chunks) {
            pieces.addAll(splitter.feed(chunk));
        }
        pieces.addAll(splitter.finish());
        return pieces;
    }

    private static Map<Integer, String> group(List<Piece> pieces) {
        return pieces.stream()
                .collect(Collectors.groupingBy(Piece::index, TreeMap::new, Collectors.mapping(Piece::text, Collectors.joining())));
    }

    private static Map<Integer, String> split(int cardCount, String... chunks) {
        return split(cardCount, List.of(), chunks);
    }

    private static Map<Integer, String> split(int cardCount, List<String> notices, String... chunks) {
        return pieces(cardCount, notices, chunks).stream()
                .collect(Collectors.groupingBy(Piece::index, TreeMap::new, Collectors.mapping(Piece::text, Collectors.joining())));
    }

    private static String allText(int cardCount, String... chunks) {
        return pieces(cardCount, List.of(), chunks).stream().map(Piece::text).collect(Collectors.joining());
    }

    private static List<Piece> pieces(int cardCount, List<String> notices, String... chunks) {
        RecommendationTextSplitter splitter = new RecommendationTextSplitter(cardCount, notices);
        List<Piece> pieces = new ArrayList<>();
        for (String chunk : chunks) {
            pieces.addAll(splitter.feed(chunk));
        }
        pieces.addAll(splitter.finish());
        return pieces;
    }
}
