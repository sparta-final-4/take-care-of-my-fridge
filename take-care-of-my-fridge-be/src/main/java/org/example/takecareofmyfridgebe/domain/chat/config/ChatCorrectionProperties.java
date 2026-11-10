package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 의도 판단 뒤 서버가 AI 값을 고치는 보정(ChatIntentFacade)을 보정마다 켜고 끈다. 기본은 모두 켜짐(지금 동작).
 * AI만으로 충분한지 평가(ChatIntentEvalManualTest)로 비교하려고 둔다. 안전 단어 차단·알레르기 빼기는 여기서 끄지 않는다.
 *
 * @param tasteWords         맛 보정(correctTastes): 맛 단어로 원하는 맛·피할 맛을 다시 정한다
 * @param spokenTastesOnly   새 추천 맛 정리(keepSpokenTastes): 이번 말에 맛 단어가 없는 맛을 지운다
 * @param keepOtherTastes    피할 맛만 말하면 맛 조건 지우기를 뺀다(keepOtherTastes)
 * @param refineSpokenTastes 다시 추천에서 맛이 부딪히면 이번 말의 맛 단어를 따른다(followSpokenTastes)
 * @param categoryWords      분류 단어·묶음 말로 분류를 정한다(correctCategories)
 * @param dishFromKeywords   요리 이름을 첫 키워드로 서버가 정한다(withDishFromKeywords). 끄면 요리 이름 없음
 * @param nameOnly           이름만 말한 짧은 말을 추천으로(nameOnly)
 * @param mealkitRequest     밀키트를 달라는 말을 추천으로(mealkitRequested)
 * @param offTopicRevive     음식 말이 있으면 상관없는 질문을 되살린다(reviveOffTopic)
 * @param fridgeShopWords    냉장고 보기·사기 단어로 의도를 바꾼다(applyFridgeShopWords)
 */
@ConfigurationProperties(prefix = "app.chat.corrections")
public record ChatCorrectionProperties(
        @DefaultValue("true") boolean tasteWords,
        @DefaultValue("true") boolean spokenTastesOnly,
        @DefaultValue("true") boolean keepOtherTastes,
        @DefaultValue("true") boolean refineSpokenTastes,
        @DefaultValue("true") boolean categoryWords,
        @DefaultValue("true") boolean dishFromKeywords,
        @DefaultValue("true") boolean nameOnly,
        @DefaultValue("true") boolean mealkitRequest,
        @DefaultValue("true") boolean offTopicRevive,
        @DefaultValue("true") boolean fridgeShopWords
) {

    // 모두 켜짐 (지금 동작)
    public static final ChatCorrectionProperties ALL_ON =
            new ChatCorrectionProperties(true, true, true, true, true, true, true, true, true, true);
}
