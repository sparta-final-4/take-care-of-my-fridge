package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 음식 말 (공백 빼고 비교). AI가 상관없는 질문(OFF_TOPIC)이라고 해도 보낸 말에 이 말이 있으면 거절하지 않는다.
 * 분류 단어(묶음 말 포함)·재료 사전 이름·레시피 이름도 같이 본다 (ChatIntentFacade.reviveOffTopic).
 *
 * @param food 음식·요리·장보기를 말하는 말 ("주식으로 먹을 거 추천해줘"의 "먹", "장보기 목록 짜줘"의 "장보기")
 */
@ConfigurationProperties(prefix = "app.chat.food-words")
public record ChatFoodWordProperties(
        @DefaultValue({"먹", "요리", "레시피", "밥", "반찬", "끼니", "아침", "점심", "저녁", "야식", "간식", "재료", "냉장고",
                "장보기", "밀키트"})
        List<String> food
) {
}
