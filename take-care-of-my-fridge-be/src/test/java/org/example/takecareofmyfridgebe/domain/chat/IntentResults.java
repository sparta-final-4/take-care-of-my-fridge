package org.example.takecareofmyfridgebe.domain.chat;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntent;
import org.example.takecareofmyfridgebe.domain.chat.dto.IntentResult;

import java.util.List;

/**
 * 테스트에서 IntentResult를 짧게 만드는 도우미. 앞쪽 칸만 주면 나머지는 null(말하지 않음)이다.
 * 운영 코드는 모든 칸을 주는 생성자(JevIntentResolver)만 쓰므로 짧은 생성자는 여기에 둔다.
 */
public final class IntentResults {

    private IntentResults() {
    }

    // 의도만 있고 조건은 하나도 말하지 않은 결과
    public static IntentResult general() {
        return of(ChatIntent.GENERAL, null, null, null, null, null, null, null, null, null);
    }

    public static IntentResult of(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                                  List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                                  List<String> keywords, String dishText, String preferenceText) {
        return of(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, null, null);
    }

    public static IntentResult of(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                                  List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                                  List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName) {
        return of(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, targetIndex, targetName, null);
    }

    public static IntentResult of(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                                  List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                                  List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName,
                                  List<String> removed) {
        return of(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, targetIndex, targetName, removed, null);
    }

    public static IntentResult of(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                                  List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                                  List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName,
                                  List<String> removed, List<String> avoidTastes) {
        return of(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, null);
    }

    public static IntentResult of(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                                  List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                                  List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName,
                                  List<String> removed, List<String> avoidTastes, String detailQuestion) {
        return of(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, detailQuestion, null);
    }

    public static IntentResult of(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                                  List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                                  List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName,
                                  List<String> removed, List<String> avoidTastes, String detailQuestion, String fridgeScope) {
        return of(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients, useSelectedOnly,
                keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, detailQuestion, fridgeScope, null);
    }

    public static IntentResult of(ChatIntent intent, List<String> wantedTastes, List<String> moodCategories, Integer maxMinutes,
                                  List<String> includeIngredients, List<String> excludeIngredients, Boolean useSelectedOnly,
                                  List<String> keywords, String dishText, String preferenceText, Integer targetIndex, String targetName,
                                  List<String> removed, List<String> avoidTastes, String detailQuestion, String fridgeScope,
                                  Boolean followsPrevious) {
        return new IntentResult(intent, wantedTastes, moodCategories, maxMinutes, includeIngredients, excludeIngredients,
                useSelectedOnly, keywords, dishText, preferenceText, targetIndex, targetName, removed, avoidTastes, detailQuestion,
                fridgeScope, followsPrevious);
    }
}
