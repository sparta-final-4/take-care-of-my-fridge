package org.example.takecareofmyfridgebe.domain.chat.config;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 상담 단어 설정 (config/chat-words.yml). 맛·분류·묻는 말·음식 말·밀키트·냉장고 보기·온도 단어를 묶음마다 중첩 record로 둔다.
 * 비교는 모두 공백을 뺀 글로 한다.
 *
 * @param taste          맛 단어 (TasteWordChecker)
 * @param tasteOpposites 서로 반대인 맛
 * @param ask            묻는 말·달라는 말 (AskWordChecker)
 * @param category       분류 묶음 말 (CategoryWordChecker)
 * @param food           음식 말 (IntentCorrector.reviveOffTopic)
 * @param mealkit        밀키트 단어 (MealkitWordChecker)
 * @param fridgeShop     냉장고 보기·사기 단어 (FridgeShopWordChecker)
 * @param temperature    온도 말 (TemperatureWordChecker)
 */
@ConfigurationProperties(prefix = "app.chat.words")
public record ChatWordProperties(
        Taste taste,
        TasteOpposites tasteOpposites,
        Ask ask,
        Category category,
        Food food,
        Mealkit mealkit,
        FridgeShop fridgeShop,
        Temperature temperature
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
            List<String> spicy,
            List<String> sweet,
            List<String> negationBefore,
            List<String> negationAfter,
            List<String> salty,
            List<String> umami,
            List<String> mild,
            List<String> wantBefore,
            List<String> wantAfter,
            List<String> clear
    ) {
    }

    /**
     * 서로 반대인 맛. 다시 추천에서 이번 말에 원한 맛과 반대인 맛은 원하는 맛에서 지운다 ("짭짤한 거" → "담백한 거로"면 짠맛을 지움).
     * 반대가 아닌 맛은 쌓는다 ("매운 걸로" → "단 것도" → 매운맛 + 단맛). 쌍은 양쪽으로 본다.
     *
     * @param pairs 반대인 두 맛 (예: [MILD, SPICY], [MILD, SALTY]). 단맛·감칠맛은 반대가 없다
     */
    public record TasteOpposites(
            List<List<Member.Taste>> pairs
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
     * 묻는 말·달라는 말 단어 (AskWordChecker).
     * 서버가 AI 의도를 추천으로 바꿀 때는 달라는 말이 있고 묻는 말이 없을 때만 바꾼다.
     *
     * @param question       묻는 말 ("추천 기준이 뭐야?", "두부 얼마나 보관돼?", 지난 일을 묻는 "밥 먹었어?")
     * @param request        달라는 말 ("밀키트 추천해줘", "뭐 있어", "먹고 싶어")
     * @param mealkitRequest 밀키트와 같이 있을 때만 달라는 말로 보는 말 ("국물 밀키트 있어?", "밀키트 뭐가 좋아?")
     * @param greeting       인사 말 ("안녕", "고마워"). 있으면 이름만 말한 짧은 말이어도 추천으로 바꾸지 않는다 ("안녕 밥은?")
     * @param recipe         요리 이름에 붙어 그 요리를 보여 달라는 말 ("닭볶음탕 레시피", "잡채 만드는 법"). 이름만 말한 말에서 레시피 이름을
     *                       찾기 전에 지운다 (맛 단어도 같이 지운다)
     */
    public record Ask(
            List<String> question,
            List<String> request,
            List<String> mealkitRequest,
            List<String> greeting,
            List<String> recipe
    ) {
    }

    /**
     * 분류 이름과 다른 흔한 말을 분류 묶음으로 본다 (CategoryWordChecker). 예: "국물" → 국·찌개·전골·수프·스튜.
     * 추천·다시 추천에서 분류 단어 보정에 쓴다. 묶음의 분류는 실제 레시피 분류 이름이어야 하고, 없는 분류는 무시한다.
     *
     * @param groups 묶음 말과 그 말이 뜻하는 분류
     */
    public record Category(
            List<Group> groups
    ) {
        public Category {
            groups = groups == null ? List.of() : List.copyOf(groups);
        }

        /**
         * @param word       묶음 말 ("국물")
         * @param categories 그 말이 뜻하는 분류 ("국", "찌개", …)
         */
        public record Group(String word, List<String> categories) {
            public Group {
                categories = categories == null ? List.of() : List.copyOf(categories);
            }
        }
    }

    /**
     * 음식 말. AI가 상관없는 질문(OFF_TOPIC)이라고 해도 보낸 말에 이 말이 있으면 거절하지 않는다.
     * 분류 단어(묶음 말 포함)·재료 사전 이름·레시피 이름도 같이 본다 (IntentCorrector.reviveOffTopic).
     *
     * @param food 음식·요리·장보기를 말하는 말 ("주식으로 먹을 거 추천해줘"의 "먹", "장보기 목록 짜줘"의 "장보기")
     */
    public record Food(
            List<String> food
    ) {
    }

    /**
     * 메시지에서 밀키트를 원하는지 볼 때 쓰는 단어 (MealkitWordChecker).
     * 부정어("말고·싫·안" 등)는 맛 단어 확인과 같은 단어(Taste)를 쓴다.
     *
     * @param mealkit 밀키트를 직접 말하는 단어 ("밀키트 추천해줘"). 있으면 밀키트만 보여 준다
     * @param easy    요리를 번거로워하는 말 ("간단하게", "귀찮아"). 있으면 레시피 카드 아래 밀키트 한 장을 붙인다
     */
    public record Mealkit(
            List<String> mealkit,
            List<String> easy
    ) {
    }

    /**
     * 냉장고 보기 말·사기 말·요리 말 단어 (FridgeShopWordChecker).
     * AI가 추천·일반 대화로 봐도 냉장고 보기 말이 있으면 냉장고 보기, 사기 말이 있으면 사기로 서버가 바꾼다 (둘 다 있으면 냉장고 보기).
     * 요리 말이 같이 있거나 묻는 말(ask.question)이 있으면 바꾸지 않는다.
     *
     * @param fridge   냉장고 보기 말 ("밀키트 뭐 남았어", "두부 아직 있어?", "냉장고에 뭐 있어", "곧 버려야 할 거 있어?", "슬슬 처리해야 할 거 있어?").
     *                 "처리해야"만으로는 보지 않는다 ("고기 핏물 처리해야 돼?")
     * @param imminent 냉장고 보기 범위를 소비기한 임박·지남으로 볼 말 ("곧 버려야 할 거", "유통기한 지난 거")
     * @param seasoning 냉장고 보기 범위를 기본 양념으로 볼 말 ("기본 양념 뭐 있어?"). 이 말은 찾을 재료 이름으로 보지 않는다
     * @param shop     사기 말 ("당근 사고 싶어", "두부 어디서 사", "대파 장바구니에 담아줘", "우유 주문해줘").
     *                 "주문"만으로는 보지 않는다 ("주문한 거 언제 와?"는 사기가 아니다)
     * @param sale     할인 말 ("세일하는 재료 있어?", "할인하는 밀키트 뭐 있어?"). 사기로 보고 할인 중인 상품만 보여 준다
     * @param cook     요리 말. 같이 있으면 추천 그대로 둔다 ("냉장고에 있는 재료로 뭐 먹지", "두부 있으면 두부 요리 추천해줘").
     *                 "먹" 한 글자는 넣지 않는다 ("냉장고에 먹을 거 뭐 남았어"는 냉장고 보기)
     * @param general  AI가 냉장고 보기·사기로 봤어도 일반 답으로 되돌릴 묻는 말 ("유통기한 지난 거 먹어도 돼?", "주문한 거 언제 와?", "장보기 목록 짜줘").
     *                 "얼마나"·"가격"은 넣지 않는다 ("두부 얼마나 남았어?", "두부 가격 얼마야?"는 냉장고 보기·사기)
     */
    public record FridgeShop(
            List<String> fridge,
            List<String> imminent,
            List<String> seasoning,
            List<String> shop,
            List<String> sale,
            List<String> cook,
            List<String> general
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
            List<String> warm,
            List<String> cold,
            List<String> coldDishes,
            List<String> hotDishEndings,
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
