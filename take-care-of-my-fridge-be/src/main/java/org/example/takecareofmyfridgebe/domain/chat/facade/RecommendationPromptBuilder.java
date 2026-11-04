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
 * [사용자 요청]
 * 메시지: 버섯 두부로 할 수 있는 요리 추천해줘
 * 선택 재료: 버섯, 두부 (이 재료를 꼭 쓰는 레시피만)
 * 조건: 원하는 맛 매운맛 / 분류 국, 찌개 / 30분 이내 / 뺄 재료 오이
 * [추천 후보]
 * 1. 제육배추찜 | 찜 | 조리 시간 40분 | 쓰는 냉장고 재료: 양파(오늘까지), 고추(D-2), 배추 | 원하는 맛과 맞음: 매운맛 | 부족한 재료: 돼지고기, 부추, 당근 외 3개 | 재료 많이 모자람
 * </pre>
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
     */
    public static String build(String content, RecipeQuery query, Map<Long, String> ingredientNames,
                               List<RecipeCandidate> picked) {
        StringBuilder text = new StringBuilder();
        text.append("[사용자 요청]\n");
        text.append("메시지: ").append(content.strip()).append('\n');
        text.append("선택 재료: ").append(selectedLine(query, ingredientNames)).append('\n');
        text.append("조건: ").append(conditionLine(query, ingredientNames)).append('\n');
        text.append("[추천 후보]\n");
        if (picked.isEmpty()) {
            text.append(NO_CANDIDATES).append('\n');
        }
        for (int i = 0; i < picked.size(); i++) {
            text.append(i + 1).append(". ").append(candidateLine(picked.get(i))).append('\n');
        }
        return withoutBraces(text.toString().stripTrailing());
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

    private static String candidateLine(RecipeCandidate candidate) {
        Reason reason = candidate.reason();
        List<String> fields = new ArrayList<>();
        fields.add(candidate.name());
        fields.add(candidate.category() == null ? "분류 없음" : candidate.category());
        fields.add(candidate.cookTimeMin() == null ? "조리 시간 정보 없음" : "조리 시간 " + candidate.cookTimeMin() + "분");
        fields.add("쓰는 냉장고 재료: " + usedLine(reason));
        if (!reason.matchedWantedTastes().isEmpty()) {
            fields.add("원하는 맛과 맞음: " + tastes(reason.matchedWantedTastes()));
        } else if (!reason.matchedTastes().isEmpty()) {
            fields.add("좋아하는 맛과 맞음: " + tastes(reason.matchedTastes()));
        }
        fields.add("부족한 재료: " + missingLine(reason.missingIngredients()));
        if (reason.lacksManyIngredients()) {
            fields.add("재료 많이 모자람");
        }
        return String.join(FIELD_DELIMITER, fields);
    }

    // 쓰는 냉장고 재료. 임박 재료는 이름 뒤에 (오늘까지)·(D-2)를 붙인다
    private static String usedLine(Reason reason) {
        if (reason.usedFridgeIngredients().isEmpty()) {
            return NONE;
        }
        Map<String, Long> dDays = reason.imminentIngredients().stream()
                .collect(Collectors.toMap(ImminentIngredient::name, ImminentIngredient::dDay, (a, b) -> a));
        return reason.usedFridgeIngredients().stream()
                .map(name -> dDays.containsKey(name) ? name + "(" + dDayLabel(dDays.get(name)) + ")" : name)
                .collect(Collectors.joining(NAME_DELIMITER));
    }

    private static String dDayLabel(long dDay) {
        return dDay == 0 ? "오늘까지" : "D-" + dDay;
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
