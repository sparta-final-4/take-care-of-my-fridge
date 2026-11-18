package org.example.takecareofmyfridgebe.domain.chat.config;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AI 상담 단어 설정 (config/chat-words.yml). 맛·밀키트·냉장고 보기·온도 단어를 묶음마다 중첩 record로 둔다.
 * 의도를 단어로 고치던 단계(분류 묶음 말·음식 말·달라는 말 등)는 Jev 전환 정리에서 지웠고, 그 단계만 쓰던 단어도 지웠다.
 * 비교는 모두 공백을 뺀 글로 한다.
 * 목록은 비면 서버가 뜨지 않는다 (@NotEmpty). 온도의 제외 목록(hotDishExclusions)만 비어도 된다.
 *
 * @param taste          맛 단어 (TasteWordChecker)
 * @param tasteOpposites 서로 반대인 맛
 * @param mealkit        밀키트 단어 (MealkitWordChecker)
 * @param fridgeShop     냉장고 보기 범위·할인·양념 단어 (FridgeShopWordChecker)
 * @param temperature    온도 말 (TemperatureWordChecker)
 */
@Validated
@ConfigurationProperties(prefix = "app.chat.words")
public record ChatWordProperties(
        @Valid @NotNull Taste taste,
        @Valid @NotNull TasteOpposites tasteOpposites,
        @Valid @NotNull Mealkit mealkit,
        @Valid @NotNull FridgeShop fridgeShop,
        @Valid @NotNull Temperature temperature
) {

    /**
     * 메시지의 맛 단어로 AI가 뽑은 원하는 맛·피할 맛을 한 번 더 확인할 때 쓰는 단어 (TasteWordChecker).
     *
     * @param spicy          매운맛 단어 ("좀 더 매운 걸로", "칼칼한 거")
     * @param sweet          단맛 단어 ("달달한 거", "단 거")
     * @param negationBefore 맛 단어 바로 앞에 오면 피하는 말 ("안 맵게", "덜 달게")
     * @param negationAfter  맛 단어 뒤 몇 글자 안에서 시작하면 피하는 말 ("매운 거 말고", "맵지 않게", "단 거 싫어", "매운 건 빼줘", "매운 게 안 들어간")
     * @param salty          짠맛 단어
     * @param umami          감칠맛 단어
     * @param mild           순한맛 단어
     * @param wantBefore     맛 단어 바로 앞에 오면 그 맛을 확실히 원하는 말 ("좀 더 매운")
     * @param wantAfter      맛 단어 뒤 몇 글자 안에서 시작하면 그 맛을 확실히 원하는 말 ("매운 걸로", "매운 거 먹고 싶어").
     *                       부정어도 이 말도 없으면 서버 판단이 애매하므로 AI가 피할 맛으로 둔 맛을 원하는 맛으로 바꾸지 않는다
     * @param clear          맛 조건을 지우라는 말 ("맛 상관없이", "맛은 아무거나"). 이 말이 없고 피하는 맛만 말했으면
     *                       AI가 넣은 뺄 조건 TASTE를 서버가 지운다 ("매운 건 빼줘"가 직전에 원한 짠맛까지 지우지 않게)
     */
    public record Taste(
            @NotEmpty List<String> spicy,
            @NotEmpty List<String> sweet,
            @NotEmpty List<String> negationBefore,
            @NotEmpty List<String> negationAfter,
            @NotEmpty List<String> salty,
            @NotEmpty List<String> umami,
            @NotEmpty List<String> mild,
            @NotEmpty List<String> wantBefore,
            @NotEmpty List<String> wantAfter,
            @NotEmpty List<String> clear
    ) {
    }

    /**
     * 서로 반대인 맛. 다시 추천에서 이번 말에 원한 맛과 반대인 맛은 원하는 맛에서 지운다 ("짭짤한 거" → "담백한 거로"면 짠맛을 지움).
     * 반대가 아닌 맛은 쌓는다 ("매운 걸로" → "단 것도" → 매운맛 + 단맛). 쌍은 양쪽으로 본다.
     *
     * @param pairs 반대인 두 맛 (예: [MILD, SPICY], [MILD, SALTY]). 단맛·감칠맛은 반대가 없다
     */
    public record TasteOpposites(
            @NotEmpty List<List<Member.Taste>> pairs
    ) {
        public TasteOpposites {
            pairs = pairs == null ? List.of() : pairs.stream().map(List::copyOf).toList();
        }

        // 그 맛과 반대인 맛
        public Set<Member.Taste> oppositesOf(Member.Taste taste) {
            Set<Member.Taste> result = EnumSet.noneOf(Member.Taste.class);
            for (List<Member.Taste> pair : pairs) {
                if (pair.size() == 2 && pair.contains(taste)) {
                    pair.stream().filter(other -> other != taste).forEach(result::add);
                }
            }
            return result;
        }
    }

    /**
     * 메시지에서 밀키트를 원하는지 볼 때 쓰는 단어 (MealkitWordChecker).
     * 부정어("말고·싫·안" 등)는 맛 단어 확인과 같은 단어(Taste)를 쓴다.
     *
     * @param mealkit 밀키트를 직접 말하는 단어 ("밀키트 추천해줘"). 있으면 밀키트만 보여 준다
     * @param easy    요리를 번거로워하는 말 ("간단하게", "귀찮아"). 있으면 레시피 카드 아래 밀키트 한 장을 붙인다
     */
    public record Mealkit(
            @NotEmpty List<String> mealkit,
            @NotEmpty List<String> easy
    ) {
    }

    /**
     * 냉장고 보기·사기에서 볼 것을 정하는 단어 (FridgeShopWordChecker). 의도(냉장고 보기·사기)는 Jev·OpenAI가 정한다.
     *
     * @param imminent  냉장고 보기 범위를 소비기한 임박·지남으로 볼 말 ("곧 버려야 할 거", "유통기한 지난 거")
     * @param seasoning 냉장고 보기 범위를 기본 양념으로 볼 말 ("기본 양념 뭐 있어?"). 이 말은 찾을 재료 이름으로 보지 않는다
     * @param sale      할인 말 ("세일하는 재료 있어?", "할인하는 밀키트 뭐 있어?"). 사기에서 할인 중인 상품만 보여 준다
     */
    public record FridgeShop(
            @NotEmpty List<String> imminent,
            @NotEmpty List<String> seasoning,
            @NotEmpty List<String> sale
    ) {
    }

    /**
     * 온도 말 (TemperatureWordChecker).
     *
     * @param warm              따뜻한 요리를 원하는 말 ("따뜻한", "뜨끈한")
     * @param cold              차가운 요리를 원하는 말 ("차가운", "시원한")
     * @param coldDishes        이름에 들어 있으면 찬 요리인 말 ("냉국", "냉면"). "냉" 한 글자는 넣지 않는다 ("냉이")
     * @param hotDishEndings    이름이 이 말로 끝나면 뜨거운 국물 요리 ("탕", "찌개")
     * @param hotDishExclusions 끝말이 맞아도 뜨거운 국물 요리가 아닌 이름 ("그라탕")
     */
    public record Temperature(
            @NotEmpty List<String> warm,
            @NotEmpty List<String> cold,
            @NotEmpty List<String> coldDishes,
            @NotEmpty List<String> hotDishEndings,
            List<String> hotDishExclusions
    ) {
        public Temperature {
            warm = warm == null ? List.of() : List.copyOf(warm);
            cold = cold == null ? List.of() : List.copyOf(cold);
            coldDishes = coldDishes == null ? List.of() : List.copyOf(coldDishes);
            hotDishEndings = hotDishEndings == null ? List.of() : List.copyOf(hotDishEndings);
            hotDishExclusions = hotDishExclusions == null ? List.of() : List.copyOf(hotDishExclusions);
        }
    }
}
