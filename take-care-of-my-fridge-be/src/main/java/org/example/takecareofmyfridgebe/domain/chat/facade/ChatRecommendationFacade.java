package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatAllergyWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatMealkitWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatTasteOppositeProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatTasteWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.config.ChatTemperatureWordProperties;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ImminentIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.MissingIngredient;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedMealkit;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ShoppingProduct;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.RecommendedRecipe;
import org.example.takecareofmyfridgebe.domain.chat.dto.KeywordMatch;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidate.Reason;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeCandidates;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeDetailReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecipeQuery;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.chat.dto.TasteLabel;
import org.example.takecareofmyfridgebe.domain.chat.facade.RecipeCandidateFacade.MealkitRecipes;
import org.example.takecareofmyfridgebe.domain.chat.service.ChatMessageService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeCandidateIngredient;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeDetailResponse;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 상담 추천 답을 만들 재료를 모은다: 레시피 후보 검색 → 3개 고르기 → 카드 이벤트와 설명용 후보 정보.
 * 카드 이미지는 레시피 요약 조회(findSummaries)로 한 번에 가져온다.
 * 장볼 재료에는 담을 상품을 붙인다 (고른 카드들의 장볼 재료를 모아 상품 조회·판매가 계산을 한 번씩, ShoppingProductPicker로 하나씩).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatRecommendationFacade {

    // 레시피 자세히 답에 붙는 카드 한 줄. 카드 설명으로도 넣어 저장하므로(화면은 설명이 있으면 이유 줄을 숨김)
    // 답 받을 때와 다시 불러올 때 모두 이 줄만 보이고, 지금 냉장고 기준 이유("내일까지인 깻잎…")는 보이지 않는다
    static final String DETAIL_CARD_REASON = "물어보신 레시피예요";
    // 레시피 정보 DEBUG 로그에 남길 앞부분 글자 수
    private static final int RECIPE_INFO_LOG_LENGTH = 300;
    // 밀키트를 직접 말했을 때 보여 줄 밀키트 수 (레시피 카드 없음)
    static final int MEALKIT_ONLY_COUNT = 3;
    // 번거로워하는 말일 때 레시피 카드 아래 붙일 밀키트 수
    static final int MEALKIT_WITH_RECIPES_COUNT = 1;
    // 밀키트를 말했는데 고를 밀키트가 없을 때 레시피 카드 앞에 서버가 붙이는 안내
    static final String NO_MEALKIT_NOTICE = "지금은 추천드릴 밀키트가 없어요.";
    private static final String MEALKIT_WORD = "밀키트";
    // 한글 종성 번호의 ㄹ
    private static final int FINAL_RIEUL = 8;

    private final RecipeCandidateFacade recipeCandidateFacade;
    private final RecipeService recipeService;
    private final IngredientService ingredientService;
    private final ProductService productService;
    private final SaleEventReader saleEventReader;
    private final ChatMessageService chatMessageService;
    private final ChatMealkitWordProperties chatMealkitWordProperties;
    private final ChatTasteWordProperties chatTasteWordProperties;
    private final ChatAllergyWordProperties chatAllergyWordProperties;
    private final ChatTasteOppositeProperties chatTasteOppositeProperties;
    private final ChatTemperatureWordProperties chatTemperatureWordProperties;

    /**
     * @param content 사용자 메시지 원문 (설명 프롬프트에 넣는다)
     * @param query   의도 판단에서 만든 추천 조건
     * @return 카드 이벤트(후보가 없으면 empty=true)와 설명용 후보 정보
     */
    public RecommendationReply recommend(String content, RecipeQuery query) {
        return recommend(content, query, List.of());
    }

    public RecommendationReply recommend(String content, RecipeQuery request, Collection<Long> previousCardIds) {
        return recommend(content, request, previousCardIds, null);
    }

    /**
     * 추천 답. 말에 밀키트 단어가 있으면(MealkitWordChecker) 밀키트를 함께 고른다.
     * <ul>
     *   <li>"밀키트"를 말함: 밀키트만 최대 {@value #MEALKIT_ONLY_COUNT}장, 레시피 카드 없음. 글은 AI 없이 서버가 쓴다.
     *       고를 밀키트가 없으면 "{@value #NO_MEALKIT_NOTICE}" 안내와 레시피 카드</li>
     *   <li>번거로워하는 말("간단하게·귀찮")만 있음: 레시피 카드 아래 밀키트 한 장 (레시피 카드와 같은 레시피는 빼고)</li>
     *   <li>밀키트를 보여 줄 때는 키워드에서 "밀키트"를 뺀다 (레시피 검색이 "밀키트"를 찾지 않게)</li>
     * </ul>
     * 다시 추천이면 직전 카드(previousCardIds)는 후보에서 빼고 고른다. 빼서 후보가 3개보다 적어지면 그때만 뒤에 다시 넣는다.
     * 새 추천은 previousCardIds 없이 부르고, 이미 추천한 레시피는 정렬에서 뒤로 간다 (RecipeQuery.alreadyRecommendedIds).
     *
     * @param previousCardIds 직전 추천 카드 레시피 ID ("다른 거 없어?"에서 같은 카드가 다시 나오지 않게)
     * @param sessionId       상담 ID. 이 상담에서 이미 보여 준 밀키트를 뒤로 보낼 때 쓴다 (null이면 보지 않는다)
     */
    public RecommendationReply recommend(String content, RecipeQuery request, Collection<Long> previousCardIds, Long sessionId) {
        MealkitWordChecker.Mode mode = MealkitWordChecker.check(content, chatMealkitWordProperties, chatTasteWordProperties);
        if (!mode.shows()) {
            return recommendRecipes(content, request, previousCardIds);
        }
        RecipeQuery query = request.withoutKeywordsContaining(chatMealkitWordProperties.mealkit());
        Set<Long> shown = sessionId == null ? Set.of() : chatMessageService.findRecommendedProductIds(sessionId);
        if (mode == MealkitWordChecker.Mode.ONLY) {
            List<MealkitPicker.Picked> picked = pickMealkits(query, List.of(), shown, MEALKIT_ONLY_COUNT);
            logMealkits(query, mode, picked);
            if (!picked.isEmpty()) {
                List<RecommendedMealkit> cards = toMealkitCards(picked, null);
                return RecommendationReply.fixed(ChatRecommendationsEventResponse.of(List.of(), cards),
                        mealkitOnlyReply(query, picked));
            }
            RecommendationReply recipes = recommendRecipes(content, query, previousCardIds);
            return recipes.event().empty() ? recipes : recipes.withNoticeFirst(NO_MEALKIT_NOTICE);
        }
        RecommendationReply recipes = recommendRecipes(content, query, previousCardIds);
        if (recipes.event().empty()) {
            return recipes;
        }
        List<Long> cardRecipeIds = recipes.event().recipes().stream().map(RecommendedRecipe::recipeId).toList();
        List<MealkitPicker.Picked> picked = pickMealkits(query, cardRecipeIds, shown, MEALKIT_WITH_RECIPES_COUNT);
        logMealkits(query, mode, picked);
        if (picked.isEmpty()) {
            return recipes;
        }
        List<RecommendedMealkit> cards = toMealkitCards(picked,
                p -> mealkitSuggestion(p.mealkit().getProduct().getName(), p.inFridge()));
        return recipes.withEvent(recipes.event().withMealkits(cards));
    }

    /**
     * 판매 중인 밀키트를 MealkitPicker로 고른다. 밀키트·판매가·연결 레시피(냉장고 기준)를 한 번씩 읽는다. 판매 중 밀키트가 없으면 더 읽지 않는다.
     */
    private List<MealkitPicker.Picked> pickMealkits(RecipeQuery query, Collection<Long> cardRecipeIds, Set<Long> shown, int limit) {
        List<MealkitProduct> mealkits = withoutExcludedIngredients(productService.getOnSaleMealkits(), query);
        if (mealkits.isEmpty()) {
            return List.of();
        }
        List<Long> recipeIds = mealkits.stream().map(MealkitProduct::getRecipeId).filter(Objects::nonNull).distinct().toList();
        MealkitRecipes recipes = recipeCandidateFacade.describeMealkitRecipes(query.memberId(), recipeIds);
        Map<Long, SellingPrice> prices = saleEventReader.findSellingPrices(
                mealkits.stream().map(MealkitProduct::getProduct).toList());
        return MealkitPicker.pick(new MealkitPicker.Input(mealkits, prices, recipes.recipes(), recipes.recipeTastes(),
                recipes.maybeSpicyRecipeIds(), query,
                cardRecipeIds, recipes.fridgeMealkits(), shown), limit);
    }

    private static List<RecommendedMealkit> toMealkitCards(List<MealkitPicker.Picked> picked,
                                                           Function<MealkitPicker.Picked, String> description) {
        return picked.stream()
                .map(p -> toMealkitCard(p.mealkit(), p.price(), p.recipe() == null ? null : p.recipe().name(),
                        description == null ? null : description.apply(p), p.fridgeDDay()))
                .toList();
    }

    private static RecommendedMealkit toMealkitCard(MealkitProduct mealkit, SellingPrice price, String recipeName,
                                                    String description, Long fridgeDDay) {
        return new RecommendedMealkit(ShoppingProduct.from(mealkit.getProduct(), price), mealkit.getServings(),
                mealkit.getRecipeId(), recipeName, description, fridgeDDay != null, fridgeDDay);
    }

    /**
     * 레시피 카드 아래 밀키트 한 장의 설명: "요리하기 번거로우면 된장국 밀키트로 간편하게 드셔 보세요."
     * 상품 이름에 "밀키트"가 이미 있으면 붙이지 않는다. 조사(으로/로)는 마지막 글자 받침을 본다 (ㄹ 받침은 "로").
     */
    static String mealkitSuggestion(String productName) {
        return mealkitSuggestion(productName, false);
    }

    // 냉장고에 있는 밀키트면 "요리하기 번거로우면 냉장고에 있는 된장국 밀키트로 간편하게 드셔 보세요."
    static String mealkitSuggestion(String productName, boolean inFridge) {
        String name = productName.strip();
        String label = name.contains(MEALKIT_WORD) ? name : name + " " + MEALKIT_WORD;
        return "요리하기 번거로우면 " + (inFridge ? "냉장고에 있는 " : "") + label + instrumentalParticle(label) + " 간편하게 드셔 보세요.";
    }

    // "으로" / "로" (받침 없음·ㄹ 받침은 "로", 한글이 아니면 "로")
    private static String instrumentalParticle(String word) {
        char last = word.charAt(word.length() - 1);
        if (last < '가' || last > '힣') {
            return "로";
        }
        int finalConsonant = (last - '가') % 28;
        return finalConsonant == 0 || finalConsonant == FINAL_RIEUL ? "로" : "으로";
    }

    /**
     * 밀키트만 보여 주는 답의 글 (AI를 부르지 않는다. 레시피 카드가 없어 AI가 레시피를 소개하지 않게).
     * 말한 조건(분류·맛·요리 이름)이 있는데 맞은 밀키트가 하나도 없으면 그렇다고 먼저 말한다.
     */
    static String mealkitOnlyReply(RecipeQuery query, List<MealkitPicker.Picked> picked) {
        boolean asked = query.hasRequest() || !query.wantedTastes().isEmpty();
        boolean matched = picked.stream().anyMatch(p -> p.requestMatches() > 0);
        String head = !asked ? "지금 살 수 있는 밀키트를 골라 봤어요."
                : matched ? "말씀하신 조건에 맞춰 밀키트를 골라 봤어요."
                : "말씀하신 조건에 맞는 밀키트는 없어서, 지금 살 수 있는 밀키트를 골라 봤어요.";
        String fridge = picked.stream().anyMatch(MealkitPicker.Picked::inFridge) ? " 냉장고에 이미 있는 밀키트는 먼저 보여 드려요." : "";
        return head + fridge + " 카드를 누르면 상품을 볼 수 있고, 장바구니 아이콘으로 바로 담을 수 있어요.";
    }

    private static void logMealkits(RecipeQuery query, MealkitWordChecker.Mode mode, List<MealkitPicker.Picked> picked) {
        log.info("상담 밀키트 - memberId: {}, 방식: {}, 고른 밀키트: {}", query.memberId(), mode, picked.stream()
                .map(p -> p.mealkit().getProduct().getId() + " " + p.mealkit().getProduct().getName()
                        + " [레시피 " + p.mealkit().getRecipeId() + ", 요청 " + p.requestMatches() + "]")
                .toList());
    }

    // 요리 이름(keywordText)은 의도 단계(IntentCorrector)에서 정해진 그대로 쓴다. 여기서 추측하거나 비우지 않는다
    private RecommendationReply recommendRecipes(String content, RecipeQuery query, Collection<Long> previousCardIds) {
        RecipeCandidates found = recipeCandidateFacade.findCandidates(query);
        List<RecipeCandidate> candidates = withoutPrevious(found.candidates(), previousCardIds);
        // 온도 말: 따뜻하면 찬 요리(냉국 등)를 빼고, 차가우면 뜨거운 국물 요리를 뒤로 보낸다 (다시 추천은 원한 표현으로 이어 간다)
        Optional<TemperatureWordChecker.Temperature> temperature =
                TemperatureWordChecker.wanted(chatTemperatureWordProperties, content, query.preferenceText());
        List<RecipeCandidate> arranged = TemperatureWordChecker.arrange(candidates, temperature, chatTemperatureWordProperties);
        if (temperature.isPresent()) {
            log.info("상담 추천 온도 말 - memberId: {}, 온도: {}, 뺀 찬 요리: {}, 뒤로 보낸 뜨거운 국물: {}",
                    query.memberId(), temperature.get(),
                    candidates.stream().filter(c -> !arranged.contains(c)).map(RecipeCandidate::name).toList(),
                    temperature.get() == TemperatureWordChecker.Temperature.COLD
                            ? candidates.stream().filter(c -> TemperatureWordChecker.hotDish(c.name(), chatTemperatureWordProperties))
                            .map(RecipeCandidate::name).toList()
                            : List.of());
        }
        candidates = arranged;
        logCandidates(query, candidates);
        // 말한 조건이 있으면 조건을 모두 맞춘 후보부터 고르고, 모자라면 분류가 맞고 반대 맛·온도가 아닌 후보로만 채운다
        PickRule rule = PickRule.of(query, found, chatTasteOppositeProperties::oppositesOf)
                .withTemperatureMismatched(
                        TemperatureWordChecker.mismatched(candidates, temperature, chatTemperatureWordProperties));
        List<RecipeCandidate> picked = RecommendationPicker.pick(candidates, query.keywords(), rule);
        // 조건에 맞는 후보가 하나도 없으면 같은 후보에서 조건 없이 고르고(냉장고 재료 순), 맞는 레시피가 없었다고 안내한다
        boolean unconditioned = rule.hasRequest() && picked.isEmpty() && !candidates.isEmpty();
        if (unconditioned) {
            picked = RecommendationPicker.pick(candidates, query.keywords(), PickRule.NONE);
            log.info("상담 추천 조건에 맞는 후보가 없어 조건 없이 고름 - memberId: {}, 조건 수: {}, 후보 {}개, 카드 {}장",
                    query.memberId(), rule.requestCount(), candidates.size(), picked.size());
        } else if (rule.hasRequest() && picked.size() < RecommendationPicker.PICK_COUNT) {
            log.info("상담 추천 조건에 맞는 후보가 모자라 카드를 줄임 - memberId: {}, 조건 수: {}, 후보 {}개 중 모두 맞음 {}개, 카드 {}장",
                    query.memberId(), rule.requestCount(), candidates.size(),
                    candidates.stream().filter(rule::matchesAll).count(), picked.size());
        }

        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(
                picked.stream().map(RecipeCandidate::recipeId).toList());
        // 카드 이유는 앞 카드와 겹치지 않게 고른다 (프롬프트 후보 줄도 같은 이유를 쓴다)
        List<String> reasons = RecommendationReasonWriter.withAvoidedTastes(
                RecommendationReasonWriter.writeDistinct(picked, query.keywordText(), !query.hasRequest()), picked,
                found.keptAvoidTastes());
        Map<Long, ShoppingProduct> products = shoppingProducts(picked);
        List<RecommendedRecipe> recipes = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            RecipeCandidate candidate = picked.get(i);
            recipes.add(toCard(candidate, summaries.get(candidate.recipeId()), reasons.get(i), null, products));
        }
        logPicked(query, picked, recipes);

        String candidateInfo = RecommendationPromptBuilder.build(
                content, query, ingredientNames(query), picked, found.keywordsNotFound(), reasons, unconditioned);
        // 안내 문장은 AI가 아니라 서버가 소개 맨 앞에 직접 보낸다 (후보가 없으면 정해진 답만 나가므로 붙이지 않는다)
        List<String> notices = picked.isEmpty()
                ? List.of()
                : RecommendationPromptBuilder.notices(query, picked, found.keywordsNotFound(), unconditioned);
        return new RecommendationReply(ChatRecommendationsEventResponse.of(recipes), candidateInfo, notices);
    }

    /**
     * 뺄 재료(알레르기 재료 포함)가 들어간 밀키트를 뺀다. 연결된 레시피가 있으면 그 레시피가 뺄 재료를 쓰는지,
     * 없으면 상품 이름에 뺄 재료 이름(두 글자 이상)이 들어 있는지 본다 ("새우 볶음밥 밀키트"). 넓히지 않을 이름("땅콩호박")은 지우고 본다.
     */
    private List<MealkitProduct> withoutExcludedIngredients(List<MealkitProduct> mealkits, RecipeQuery query) {
        if (mealkits.isEmpty() || query.excludeIngredientIds().isEmpty()) {
            return mealkits;
        }
        Set<Long> excludedRecipeIds = recipeService.findCandidateIngredients(query.excludeIngredientIds()).stream()
                .map(RecipeCandidateIngredient::recipeId)
                .collect(Collectors.toSet());
        List<String> excludedNames = ingredientService.getIngredientsByIds(query.excludeIngredientIds()).values().stream()
                .map(ingredient -> compact(ingredient.getName()))
                .filter(name -> name.length() >= 2)
                .distinct()
                .toList();
        // 뺄 재료 자신인 이름은 끊지 않는다 ("마늘 알레르기"면 "마늘빵 밀키트"의 마늘을 끊으면 안 된다)
        List<String> otherKinds = chatAllergyWordProperties.allNotSameKindNames().stream()
                .filter(name -> !excludedNames.contains(name))
                .toList();
        List<MealkitProduct> kept = mealkits.stream()
                .filter(mealkit -> mealkit.getRecipeId() != null
                        ? !excludedRecipeIds.contains(mealkit.getRecipeId())
                        : excludedNames.stream().noneMatch(cutOtherKinds(mealkit.getProduct().getName(), otherKinds)::contains))
                .toList();
        if (kept.size() < mealkits.size()) {
            log.info("상담 밀키트 뺄 재료가 들어 있어 뺌 - 뺀 수: {}", mealkits.size() - kept.size());
        }
        return kept;
    }

    // 넓히지 않을 이름(땅콩호박 등)은 끊어 두고 본다 ("땅콩호박 수프 밀키트"가 "땅콩"으로 빠지지 않게)
    private static String cutOtherKinds(String name, List<String> otherKinds) {
        String text = compact(name);
        for (String other : otherKinds) {
            text = text.replace(other, "|");
        }
        return text;
    }

    private static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }

    // 후보 10개의 정렬 단계별 값 (DEBUG). 정렬 순서: 요청 > 선택 > (선택이 있으면) 모자람 아님 > 임박 > 맛 > 보유 비율 > 없는 재료 수 > id
    private static void logCandidates(RecipeQuery query, List<RecipeCandidate> candidates) {
        if (!log.isDebugEnabled()) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            RecipeCandidate candidate = candidates.get(i);
            RecipeCandidate.Ranking ranking = candidate.ranking();
            text.append(String.format("%n  %2d. %d %s [%s] | 요청 %d | 선택 %d | 모자람 %s | 임박 %d | 맛 %d | 보유 %d/%d | 없는 재료 %d | 냉장고에 없는 선택 %s",
                    i + 1, candidate.recipeId(), candidate.name(), candidate.category(),
                    ranking.requestMatches(), ranking.selectedMatches(), candidate.reason().lacksManyIngredients() ? "Y" : "N",
                    ranking.imminentScore(), ranking.tasteMatches(), ranking.ownedCount(), ranking.nonBasicIngredientCount(),
                    ranking.missingCount(), candidate.reason().missingSelectedIngredients()));
        }
        log.debug("상담 추천 후보 정렬 - memberId: {}, 선택 재료: {}, 선택 재료만: {}, 후보 {}개:{}",
                query.memberId(), query.selectedIngredientIds(), query.useSelectedOnly(), candidates.size(), text);
    }

    // 고른 레시피마다 어떻게 맞았는지(이름·늘린 이름·재료·쪼개짐·벡터·없음)와 카드 이유
    private static void logPicked(RecipeQuery query, List<RecipeCandidate> picked, List<RecommendedRecipe> recipes) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            RecipeCandidate candidate = picked.get(i);
            KeywordMatch match = candidate.reason().keywordMatch();
            lines.add(candidate.recipeId() + " " + candidate.name() + " [" + (match == null ? "없음" : match.type().label())
                    + "] " + recipes.get(i).reason());
        }
        log.info("상담 추천 후보 - memberId: {}, 키워드: {}, 고른 레시피: {}", query.memberId(), query.keywords(), lines);
    }

    /**
     * 레시피 자세히 답을 만들 재료: 그 레시피 카드 하나와 [레시피 정보]. 레시피가 지워졌으면 빈 값.
     * 카드의 임박·장볼 재료와 [냉장고 기준]은 지금 냉장고 기준으로 계산한다.
     */
    public Optional<RecipeDetailReply> describe(Long memberId, Long recipeId) {
        RecipeDetailResponse recipe;
        try {
            recipe = recipeService.getRecipe(recipeId);
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.RECIPE_NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
        RecipeCandidate candidate = recipeCandidateFacade.describeRecipes(memberId, List.of(recipeId)).get(recipeId);
        String recipeInfo = RecipeDetailPromptBuilder.build(recipe, candidate);
        if (log.isDebugEnabled()) {
            log.debug("상담 레시피 정보 - recipeId: {}, 글자 수: {}, 앞 300자: {}", recipeId, recipeInfo.length(),
                    recipeInfo.substring(0, Math.min(RECIPE_INFO_LOG_LENGTH, recipeInfo.length())));
        }
        RecommendedRecipe card = candidate == null
                ? new RecommendedRecipe(recipe.recipeId(), recipe.name(), recipe.imageUrl(), recipe.category(),
                recipe.cookTimeMin(), List.of(), List.of(), List.of(), List.of(), List.of(), false, DETAIL_CARD_REASON,
                DETAIL_CARD_REASON)
                : toCard(candidate, new RecipeSummary(recipe.recipeId(), recipe.name(), recipe.imageUrl(), recipe.tipSummary()),
                DETAIL_CARD_REASON, DETAIL_CARD_REASON, shoppingProducts(List.of(candidate)));
        return Optional.of(new RecipeDetailReply(ChatRecommendationsEventResponse.of(List.of(card)), recipeInfo));
    }

    // 직전 카드를 뺀 후보. 빼서 3개보다 적어지면 직전 카드를 정렬 순서대로 뒤에 다시 붙인다
    static List<RecipeCandidate> withoutPrevious(List<RecipeCandidate> candidates, Collection<Long> previousCardIds) {
        if (previousCardIds.isEmpty()) {
            return candidates;
        }
        Set<Long> previous = Set.copyOf(previousCardIds);
        List<RecipeCandidate> fresh = candidates.stream().filter(candidate -> !previous.contains(candidate.recipeId())).toList();
        if (fresh.size() >= RecommendationPicker.PICK_COUNT) {
            return fresh;
        }
        return Stream.concat(fresh.stream(), candidates.stream().filter(candidate -> previous.contains(candidate.recipeId())))
                .toList();
    }

    // 프롬프트에 쓸 선택·뺄 재료 이름
    private Map<Long, String> ingredientNames(RecipeQuery query) {
        List<Long> ids = Stream.concat(query.selectedIngredientIds().stream(), query.excludeIngredientIds().stream())
                .distinct()
                .toList();
        return ingredientService.getIngredientsByIds(ids).values().stream()
                .collect(Collectors.toMap(Ingredient::getId, Ingredient::getName));
    }

    /**
     * 저장된 추천 카드를 다시 만든다. 순서·설명은 저장값, 이름·이미지·분류·시간은 지금 레시피, 임박·장볼 재료·이유는 지금 냉장고 기준.
     * 지워진 레시피는 빼고, "냉장고에 없는 선택 재료"는 고른 재료를 저장하지 않으므로 비운다. 레시피 조회는 메시지 수와 상관없이 한 번씩이다.
     *
     * 밀키트 카드는 빼고 레시피 카드만 만든다 (밀키트는 rebuildMealkitCards).
     *
     * @return AI 메시지 ID별 카드 (보여 준 순서)
     */
    public Map<Long, List<RecommendedRecipe>> rebuildCards(Long memberId, List<SavedRecommendation> all) {
        List<SavedRecommendation> saved = all.stream().filter(recommendation -> !recommendation.isProduct()).toList();
        if (saved.isEmpty()) {
            return Map.of();
        }
        List<Long> recipeIds = saved.stream().map(SavedRecommendation::recipeId).distinct().toList();
        Map<Long, RecipeCandidate> candidates = recipeCandidateFacade.describeRecipes(memberId, recipeIds);
        Map<Long, RecipeSummary> summaries = recipeService.findSummaries(recipeIds);
        // 상품·판매가는 메시지 수와 상관없이 한 번씩, 지금 가격·품절 기준
        Map<Long, ShoppingProduct> products = shoppingProducts(candidates.values());

        Map<Long, List<SavedRecommendation>> byMessage = saved.stream()
                .collect(Collectors.groupingBy(SavedRecommendation::messageId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<RecommendedRecipe>> cards = new LinkedHashMap<>();
        byMessage.forEach((messageId, recommendations) -> {
            List<SavedRecommendation> alive = recommendations.stream()
                    .sorted(Comparator.comparingInt(SavedRecommendation::displayOrder))
                    .filter(recommendation -> candidates.containsKey(recommendation.recipeId()))
                    .toList();
            List<RecipeCandidate> picked = alive.stream().map(r -> candidates.get(r.recipeId())).toList();
            List<String> reasons = RecommendationReasonWriter.writeDistinct(picked, null);
            List<RecommendedRecipe> messageCards = new ArrayList<>();
            for (int i = 0; i < alive.size(); i++) {
                RecipeCandidate candidate = picked.get(i);
                messageCards.add(toCard(candidate, summaries.get(candidate.recipeId()), reasons.get(i),
                        alive.get(i).description(), products));
            }
            cards.put(messageId, messageCards);
        });
        return cards;
    }

    /**
     * 저장된 밀키트 카드를 다시 만든다. 순서·설명은 저장값, 이름·이미지·인분·가격·품절은 지금 상품, 레시피 이름은 지금 레시피 기준.
     * 판매 중지됐거나 지워진 밀키트는 빼고, 품절이면 카드는 두고 품절로 보여 준다.
     * "냉장고에 있어요"(inFridge·남은 일수)는 지금 냉장고 기준이다.
     * 상품 카드 중 밀키트(상품 종류 MEALKIT)만 만든다 (재료 상품 카드는 ChatFridgeShopFacade.rebuildProductCards).
     * 상품·판매가·레시피 이름·냉장고 조회는 메시지 수와 상관없이 한 번씩이고, 밀키트 카드가 없으면 조회하지 않는다.
     *
     * @return AI 메시지 ID별 밀키트 카드 (보여 준 순서). 밀키트 카드가 없는 메시지는 맵에 없다
     */
    public Map<Long, List<RecommendedMealkit>> rebuildMealkitCards(Long memberId, List<SavedRecommendation> all) {
        List<SavedRecommendation> saved = all.stream().filter(SavedRecommendation::isProduct).toList();
        if (saved.isEmpty()) {
            return Map.of();
        }
        Map<Long, MealkitProduct> mealkits = productService.getPublicMealkits(
                        saved.stream().map(SavedRecommendation::productId).distinct().toList()).stream()
                .collect(Collectors.toMap(mealkit -> mealkit.getProduct().getId(), Function.identity()));
        if (mealkits.isEmpty()) {
            return Map.of();
        }
        Map<Long, SellingPrice> prices = saleEventReader.findSellingPrices(
                mealkits.values().stream().map(MealkitProduct::getProduct).toList());
        Map<Long, RecipeSummary> recipes = recipeService.findSummaries(
                mealkits.values().stream().map(MealkitProduct::getRecipeId).filter(Objects::nonNull).distinct().toList());
        MealkitPicker.FridgeMealkits fridge = recipeCandidateFacade.findFridgeMealkits(memberId);

        Map<Long, List<RecommendedMealkit>> cards = new LinkedHashMap<>();
        saved.stream()
                .sorted(Comparator.comparing(SavedRecommendation::messageId)
                        .thenComparingInt(SavedRecommendation::displayOrder))
                .forEach(recommendation -> {
                    MealkitProduct mealkit = mealkits.get(recommendation.productId());
                    SellingPrice price = mealkit == null ? null : prices.get(recommendation.productId());
                    if (price == null) {
                        return;
                    }
                    RecipeSummary recipe = mealkit.getRecipeId() == null ? null : recipes.get(mealkit.getRecipeId());
                    cards.computeIfAbsent(recommendation.messageId(), id -> new ArrayList<>())
                            .add(toMealkitCard(mealkit, price, recipe == null ? null : recipe.name(),
                                    recommendation.description(), fridge.dDayOf(mealkit.getProduct())));
                });
        return cards;
    }

    /**
     * 카드들의 장볼 재료마다 담을 상품. 장볼 재료 ID를 모아 상품 조회 한 번, 판매가 계산 한 번만 한다.
     * 장볼 재료가 없거나 판매 상품이 없으면 조회를 줄이고 빈 맵을 돌려준다.
     *
     * @return 재료 ID별 담을 상품
     */
    private Map<Long, ShoppingProduct> shoppingProducts(Collection<RecipeCandidate> cards) {
        Set<Long> ingredientIds = cards.stream()
                .flatMap(card -> card.reason().missingIngredients().stream())
                .map(RecipeCandidate.MissingIngredient::ingredientId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ingredientIds.isEmpty()) {
            return Map.of();
        }
        List<IngredientProduct> links = productService.getPublicIngredientProducts(ingredientIds);
        if (links.isEmpty()) {
            return Map.of();
        }
        return ShoppingProductPicker.pick(links,
                saleEventReader.findSellingPrices(links.stream().map(IngredientProduct::getProduct).distinct().toList()));
    }

    private static RecommendedRecipe toCard(RecipeCandidate candidate, RecipeSummary summary, String cardReason,
                                            String description, Map<Long, ShoppingProduct> products) {
        Reason reason = candidate.reason();
        // 원한 맛을 말했으면 그 맛, 아니면 회원 선호 맛과 겹친 맛
        List<Member.Taste> tastes = reason.matchedWantedTastes().isEmpty()
                ? reason.matchedTastes()
                : reason.matchedWantedTastes();
        return new RecommendedRecipe(
                candidate.recipeId(),
                candidate.name(),
                summary == null ? null : summary.imageUrl(),
                candidate.category(),
                candidate.cookTimeMin(),
                reason.usedFridgeIngredients(),
                reason.imminentIngredients().stream()
                        .map(imminent -> new ImminentIngredient(imminent.name(), imminent.dDay()))
                        .toList(),
                tastes.stream().map(TasteLabel::of).toList(),
                reason.missingIngredients().stream()
                        .map(missing -> new MissingIngredient(missing.ingredientId(), missing.name(),
                                products.get(missing.ingredientId())))
                        .toList(),
                reason.missingSelectedIngredients(),
                reason.lacksManyIngredients(),
                cardReason,
                description
        );
    }
}
