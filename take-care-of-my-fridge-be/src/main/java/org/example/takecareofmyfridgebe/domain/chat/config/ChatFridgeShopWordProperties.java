package org.example.takecareofmyfridgebe.domain.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * 냉장고 보기 말·사기 말·요리 말 단어 (FridgeShopWordChecker). 비교는 공백을 뺀 글로 한다.
 * AI가 추천·일반 대화로 봐도 냉장고 보기 말이 있으면 냉장고 보기, 사기 말이 있으면 사기로 서버가 바꾼다 (둘 다 있으면 냉장고 보기).
 * 요리 말이 같이 있거나 묻는 말(app.chat.ask-words.question)이 있으면 바꾸지 않는다.
 *
 * @param fridge   냉장고 보기 말 ("밀키트 뭐 남았어", "두부 아직 있어?", "냉장고에 뭐 있어", "곧 버려야 할 거 있어?")
 * @param shop     사기 말 ("당근 사고 싶어", "두부 어디서 사", "대파 장바구니에 담아줘", "우유 주문해줘").
 *                 "주문"만으로는 보지 않는다 ("주문한 거 언제 와?"는 사기가 아니다)
 * @param sale     할인 말 ("세일하는 재료 있어?", "할인하는 밀키트 뭐 있어?"). 사기로 보고 할인 중인 상품만 보여 준다
 * @param imminent 냉장고 보기 범위를 소비기한 임박·지남으로 볼 말 ("곧 버려야 할 거", "유통기한 지난 거")
 * @param seasoning 냉장고 보기 범위를 기본 양념으로 볼 말 ("기본 양념 뭐 있어?"). 이 말은 찾을 재료 이름으로 보지 않는다
 * @param cook     요리 말. 같이 있으면 추천 그대로 둔다 ("냉장고에 있는 재료로 뭐 먹지", "두부 있으면 두부 요리 추천해줘").
 *                 "먹" 한 글자는 넣지 않는다 ("냉장고에 먹을 거 뭐 남았어"는 냉장고 보기)
 */
@ConfigurationProperties(prefix = "app.chat.fridge-shop-words")
public record ChatFridgeShopWordProperties(
        @DefaultValue({"남았", "아직있", "냉장고에뭐", "냉장고에있", "냉장고에서", "버려야", "유통기한지난", "소비기한지난"})
        List<String> fridge,
        @DefaultValue({"버려야", "지난", "임박", "유통기한", "소비기한"})
        List<String> imminent,
        @DefaultValue({"양념", "조미료"})
        List<String> seasoning,
        @DefaultValue({"사고싶", "어디서사", "장바구니", "담아", "주문해"})
        List<String> shop,
        @DefaultValue({"세일", "할인", "특가", "번개"})
        List<String> sale,
        @DefaultValue({"추천", "만들", "해먹", "요리", "레시피", "뭐먹", "먹지", "먹을까"})
        List<String> cook
) {
}
