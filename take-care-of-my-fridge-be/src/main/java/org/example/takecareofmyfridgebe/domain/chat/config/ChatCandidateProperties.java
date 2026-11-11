package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * AI 상담 레시피 후보 검색 설정.
 * 목록은 비면 서버가 뜨지 않는다 (@NotEmpty). 안 매운 김치 목록(spicyExclusions)만 비어도 된다.
 *
 * @param excludedCategories 후보에서 뺄 레시피 분류(recipes.category 값). 요리 추천에 맞지 않는 양념·음료 등
 * @param minOwnedRatio      보유 비율(가진 재료 / 기본 양념 뺀 재료) 기준. 미만인 레시피는 기준 이상 레시피로
 *                           10개가 안 찰 때만 뒤에 채운다(선택 재료 2개 이상을 전부 쓰는 레시피는 예외). 0이면 기준 없이 정렬 순서대로 고른다
 * @param spicyIngredients     이 재료가 들어 있으면 "확실히 매운" 레시피다 (맛 데이터에 매운맛이 빠진 레시피 보완). 공백 빼고 이름에 들어 있으면 해당.
 *                             원하는 맛 매운맛("매운 걸로")은 이 기준만 센다. 그냥 "고추"는 넣지 않는다. 김치류는 넣는다
 * @param spicyExclusions      이름에 매운 재료가 들어 있어도 이 이름이면 매운 재료로 보지 않는다 (안 매운 김치: 백김치·물김치·동치미·나박김치)
 * @param mildSpicyIngredients 이 재료가 들어 있으면 "매울 수 있는" 레시피다 (홍고추·청고추처럼 조금 들어가는 경우가 많은 것).
 *                             피할 맛 매운맛("매운 거 말고")은 확실히 매운 레시피와 함께 이것까지 뺀다
 * @param mildSpicyExactIngredients 공백 뺀 이름이 이것과 똑같으면 "매울 수 있는" 레시피다 (고추·붉은고추·고추냉이).
 *                             이름에 들어 있는 것으로 보면 오이고추·꽈리고추·아삭이고추까지 걸려서 따로 둔다
 */
@Validated
@ConfigurationProperties(prefix = "app.chat.candidate")
public record ChatCandidateProperties(
        @NotEmpty List<String> excludedCategories,
        double minOwnedRatio,
        @NotEmpty List<String> spicyIngredients,
        List<String> spicyExclusions,
        @NotEmpty List<String> mildSpicyIngredients,
        @NotEmpty List<String> mildSpicyExactIngredients
) {
}
