package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.TasteLabel;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 추천 설명 AI에 넘길 후보 정보를 줄 글로 만든다. 스프링에 의존하지 않는 순수 클래스.
 * <pre>
 * [먼저 말할 것]
 * 말씀하신 표고버섯, 두부는 지금 냉장고에 없어서 준비가 필요해요
 * [사용자 요청]
 * 메시지: 버섯 두부로 할 수 있는 요리 추천해줘
 * 선택 재료: 버섯, 두부 (이 재료를 꼭 쓰는 레시피만)
 * 조건: 원하는 맛 매운맛 / 분류 국, 찌개 / 30분 이내 / 뺄 재료 오이
 * [추천 후보]
 * 후보: 두부제육찜 | 찜 | 조리 시간 40분 | 카드 이유: 두부가 주재료예요 | 선택 재료 쓰임: 두부 | 쓰는 냉장고 재료: 오늘까지인 양파, 모레까지인 고추, 배추 | 냉장고에 없는 선택 재료: 두부 | 원하는 맛과 맞음: 매운맛 | 장볼 재료: 돼지고기, 부추, 당근 외 3개 | 재료 많이 모자람
 * </pre>
 * [먼저 말할 것]은 원한 표현이 있는데 고른 레시피가 요청에 하나도 안 맞거나, 요리 이름 키워드로 레시피를 못 찾았거나, 고른 후보 중 냉장고에 없는 선택 재료가 있을 때만 넣는다
 * (AI가 판단하지 않게 서버가 정한다).
 * 후보가 없으면 [추천 후보] 아래에 "후보 없음"을 쓴다. 맛은 한글로 쓰고, 중괄호({})는 괄호로 바꿔 넣지 않는다.
 */
public final class RecommendationPromptBuilder {

    static final String NO_CANDIDATES = "후보 없음";
    private static final int MISSING_SHOWN = 3;
    private static final String NONE = "없음";
    private static final String FIELD_DELIMITER = " | ";
    private static final String NAME_DELIMITER = ", ";

    private RecommendationPromptBuilder() {
    }

    /**
     * @param content         사용자 메시지 원문
     * @param query           추천 조건
     * @param ingredientNames 선택·뺄 재료 ID별 이름 (없는 ID는 건너뛴다)
     * @param picked          추천할 레시피 (RecommendationPicker가 고른 순서)
     * @param keywordsNotFound 요리 이름 키워드로 레시피를 하나도 찾지 못했는지
     */
    public static String build(String content, RecipeQuery query, Map<Long, String> ingredientNames,
                               List<RecipeCandidate> picked, boolean keywordsNotFound) {
        StringBuilder text = new StringBuilder();
        List<String> notices = new ArrayList<>();
        // 원한 게 있었는데 고른 레시피가 요청(키워드·분류·맛)에 하나도 안 맞으면 그렇다고 먼저 말한다.
        // 그렇지 않고 키워드로 이름·재료가 맞은 레시피만 없으면 비슷한 걸 골랐다고 말한다
        if (preferenceUnmet(query, picked)) {
            notices.add(preferenceUnmetNotice(query.preferenceText()));
        } else if (keywordsNotFound) {
            notices.add(keywordNotFoundNotice(query));
        }
        String missingNotice = missingSelectedNotice(picked);
        if (missingNotice != null) {
            notices.add(missingNotice);
        }
        if (!notices.isEmpty()) {
            text.append("[먼저 말할 것]\n").append(String.join("\n", notices)).append('\n');
        }
        text.append("[사용자 요청]\n");
        text.append("메시지: ").append(content.strip()).append('\n');
        text.append("선택 재료: ").append(selectedLine(query, ingredientNames)).append('\n');
        text.append("조건: ").append(conditionLine(query, ingredientNames)).append('\n');
        text.append("[추천 후보]\n");
        if (picked.isEmpty()) {
            text.append(NO_CANDIDATES).append('\n');
        }
        // 번호를 붙이면 AI가 번호 목록으로 답해서 "후보:"로 시작한다
        List<String> reasons = RecommendationReasonWriter.writeDistinct(picked, query.keywordText());
        for (int i = 0; i < picked.size(); i++) {
            text.append("후보: ").append(candidateLine(picked.get(i), reasons.get(i))).append('\n');
        }
        return withoutBraces(text.toString().stripTrailing());
    }

    // 원한 표현이나 요청 조건(키워드·분류·맛)이 있는데 고른 레시피 중 요청에 맞은 것이 하나도 없는지
    static boolean preferenceUnmet(RecipeQuery query, List<RecipeCandidate> picked) {
        boolean asked = query.preferenceText() != null || !query.keywords().isEmpty()
                || !query.moodCategories().isEmpty() || !query.wantedTastes().isEmpty();
        return asked
                && !picked.isEmpty()
                && picked.stream().allMatch(candidate -> candidate.ranking().requestMatches() == 0);
    }

    // 원한 표현이 있으면 그 말로, 없으면 "조건에 맞는 레시피"로
    static String preferenceUnmetNotice(String preferenceText) {
        String subject = preferenceText != null
                ? preferenceText + topicParticle(preferenceText)
                : "조건에 맞는 레시피는";
        return "말씀하신 " + subject + " 찾지 못해서 냉장고 재료로 만들 수 있는 요리를 골랐어요";
    }

    // 말한 요리를 못 찾았을 때 안내. 요리 이름을 말했으면 "말씀하신 김피탕은", 없으면 첫 키워드로 "말씀하신 냉면 요리는"
    static String keywordNotFoundNotice(RecipeQuery query) {
        if (query.keywordText() != null) {
            String dish = query.keywordText();
            return "말씀하신 " + dish + topicParticle(dish) + " 찾지 못해서 비슷한 걸 골랐어요";
        }
        String dish = query.keywords().get(0);
        String dishPhrase = dish.endsWith("요리") ? dish : dish + " 요리";
        return "말씀하신 " + dishPhrase + "는 찾지 못해서 비슷한 걸 골랐어요";
    }

    // 고른 후보들의 냉장고에 없는 선택 재료를 모아 안내 문장을 만든다. 없으면 null
    static String missingSelectedNotice(List<RecipeCandidate> picked) {
        List<String> names = picked.stream()
                .flatMap(candidate -> candidate.reason().missingSelectedIngredients().stream())
                .distinct()
                .toList();
        if (names.isEmpty()) {
            return null;
        }
        String last = names.get(names.size() - 1);
        return "말씀하신 " + String.join(NAME_DELIMITER, names) + topicParticle(last) + " 지금 냉장고에 없어서 준비가 필요해요";
    }

    // 받침이 있으면 "은", 없으면 "는" (한글이 아니면 "는")
    private static String topicParticle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < '가' || last > '힣') {
            return "는";
        }
        return (last - '가') % 28 == 0 ? "는" : "은";
    }

    private static String selectedLine(RecipeQuery query, Map<Long, String> ingredientNames) {
        String names = namesOf(query.selectedIngredientIds(), ingredientNames);
        if (names.isEmpty()) {
            return NONE;
        }
        return query.useSelectedOnly() ? names + " (이 재료를 꼭 쓰는 레시피만)" : names;
    }

    private static String conditionLine(RecipeQuery query, Map<Long, String> ingredientNames) {
        List<String> conditions = new ArrayList<>();
        if (!query.wantedTastes().isEmpty()) {
            conditions.add("원하는 맛 " + tastes(query.wantedTastes()));
        }
        if (!query.moodCategories().isEmpty()) {
            conditions.add("분류 " + String.join(NAME_DELIMITER, query.moodCategories()));
        }
        if (query.maxMinutes() != null) {
            conditions.add(query.maxMinutes() + "분 이내");
        }
        String excluded = namesOf(query.excludeIngredientIds(), ingredientNames);
        if (!excluded.isEmpty()) {
            conditions.add("뺄 재료 " + excluded);
        }
        return conditions.isEmpty() ? NONE : String.join(" / ", conditions);
    }

    private static String candidateLine(RecipeCandidate candidate, String cardReason) {
        Reason reason = candidate.reason();
        List<String> fields = new ArrayList<>();
        fields.add(candidate.name());
        fields.add(candidate.category() == null ? "분류 없음" : candidate.category());
        fields.add(candidate.cookTimeMin() == null ? "조리 시간 정보 없음" : "조리 시간 " + candidate.cookTimeMin() + "분");
        fields.add("카드 이유: " + cardReason);
        if (!reason.usedSelectedIngredients().isEmpty()) {
            fields.add("선택 재료 쓰임: " + String.join(NAME_DELIMITER, reason.usedSelectedIngredients()));
        }
        fields.add("쓰는 냉장고 재료: " + usedLine(reason));
        if (!reason.missingSelectedIngredients().isEmpty()) {
            fields.add("냉장고에 없는 선택 재료: " + String.join(NAME_DELIMITER, reason.missingSelectedIngredients()));
        }
        if (!reason.matchedWantedTastes().isEmpty()) {
            fields.add("원하는 맛과 맞음: " + tastes(reason.matchedWantedTastes()));
        } else if (!reason.matchedTastes().isEmpty()) {
            fields.add("좋아하는 맛과 맞음: " + tastes(reason.matchedTastes()));
        }
        // 카드·설명과 같은 말로 "장볼 재료"라고 쓴다 (기본 양념 제외)
        fields.add("장볼 재료: " + missingLine(reason.missingIngredients()));
        if (reason.lacksManyIngredients()) {
            fields.add("재료 많이 모자람");
        }
        return String.join(FIELD_DELIMITER, fields);
    }

    // 쓰는 냉장고 재료. 임박 재료는 말 순서대로 앞에 소비기한을 붙인다 ("내일까지인 고추")
    private static String usedLine(Reason reason) {
        if (reason.usedFridgeIngredients().isEmpty()) {
            return NONE;
        }
        Map<String, Long> dDays = reason.imminentIngredients().stream()
                .collect(Collectors.toMap(ImminentIngredient::name, ImminentIngredient::dDay, (a, b) -> a));
        return reason.usedFridgeIngredients().stream()
                .map(name -> dDays.containsKey(name) ? dDayLabel(dDays.get(name)) + " " + name : name)
                .collect(Collectors.joining(NAME_DELIMITER));
    }

    // 소비기한을 재료 이름 앞에 붙일 말로 쓴다 (AI가 D-1을 "오늘까지"로 잘못 읽거나 괄호째 옮겨 쓰지 않게)
    static String dDayLabel(long dDay) {
        return switch ((int) dDay) {
            case 0 -> "오늘까지인";
            case 1 -> "내일까지인";
            case 2 -> "모레까지인";
            default -> dDay + "일 남은";
        };
    }

    private static String missingLine(List<MissingIngredient> missing) {
        if (missing.isEmpty()) {
            return NONE;
        }
        String shown = missing.stream()
                .limit(MISSING_SHOWN)
                .map(MissingIngredient::name)
                .collect(Collectors.joining(NAME_DELIMITER));
        int rest = missing.size() - MISSING_SHOWN;
        return rest > 0 ? shown + " 외 " + rest + "개" : shown;
    }

    private static String tastes(List<Member.Taste> tastes) {
        return tastes.stream().map(TasteLabel::of).collect(Collectors.joining(NAME_DELIMITER));
    }

    private static String namesOf(List<Long> ids, Map<Long, String> names) {
        return ids.stream()
                .map(names::get)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(NAME_DELIMITER));
    }

    // Spring AI 템플릿 문법과 겹치지 않게 중괄호를 괄호로 바꾼다
    private static String withoutBraces(String text) {
        return text.replace('{', '(').replace('}', ')');
    }
}
