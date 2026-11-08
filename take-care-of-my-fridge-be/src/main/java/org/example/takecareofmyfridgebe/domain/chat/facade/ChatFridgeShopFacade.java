package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.ItemLookup;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.FridgeItemCard;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 상담 냉장고 보기 답 (FRIDGE). 냉장고 데이터로 카드와 서버 문장을 만들고, 답 생성 AI는 부르지 않는다.
 * <ul>
 *   <li>재료 이름을 말했으면 그 재료(같은 종류 포함, 기본 양념도)만, 아니면 범위(전체는 기본 양념 제외·밀키트·임박)로 고른다</li>
 *   <li>소비기한 순이고 지난 줄은 맨 아래에 둔다. 카드는 최대 {@value #CARD_LIMIT}장, 글은 FridgeReplyWriter가 쓴다</li>
 *   <li>밀키트 줄은 fridge.product_id로 상품 이미지를 붙인다</li>
 *   <li>카드는 저장하지 않는다 (다시 불러오면 그때 상태의 글만 보인다)</li>
 * </ul>
 * 냉장고·재료 매칭·같은 종류·밀키트 상품 조회는 한 번씩이고, 필요 없으면 조회하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatFridgeShopFacade {

    static final int CARD_LIMIT = 10;

    private final FridgeService fridgeService;
    private final IngredientMatcher ingredientMatcher;
    private final IngredientService ingredientService;
    private final ProductService productService;

    public RecommendationReply fridge(Long memberId, ItemLookup lookup) {
        List<FridgeItemResponse> items = fridgeService.getItems(memberId, FridgeSortType.EXPIRATION);
        List<FridgeItemResponse> shown;
        List<String> missing = List.of();
        boolean named = !lookup.names().isEmpty();
        if (named) {
            Map<String, List<FridgeItemResponse>> byName = itemsByName(items, lookup.names());
            shown = byName.values().stream().flatMap(List::stream).distinct().toList();
            missing = byName.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).map(Map.Entry::getKey).toList();
        } else {
            shown = items.stream().filter(inScope(lookup.scope())).toList();
        }
        // 소비기한 순 조회라 지난 줄이 앞에 온다. 남은 줄을 앞에, 지난 줄을 맨 아래로 (각각 소비기한 순)
        List<FridgeItemResponse> ordered = Stream.concat(
                        shown.stream().filter(item -> item.dDay() >= 0),
                        shown.stream().filter(item -> item.dDay() < 0))
                .toList();
        String reply = FridgeReplyWriter.write(lookup.scope(),
                ordered.stream().map(item -> new FridgeReplyWriter.Line(displayName(item), item.dDay())).toList(),
                missing, named);
        List<FridgeItemResponse> cardItems = ordered.stream().limit(CARD_LIMIT).toList();
        List<FridgeItemCard> cards = toCards(cardItems);
        log.info("상담 냉장고 보기 - memberId: {}, 범위: {}, 말한 재료: {}, 냉장고 {}줄 중 {}줄, 없는 재료: {}, 카드 {}장",
                memberId, lookup.scope(), lookup.names(), items.size(), ordered.size(), missing, cards.size());
        return RecommendationReply.fixed(ChatRecommendationsEventResponse.fridge(cards), reply);
    }

    private static Predicate<FridgeItemResponse> inScope(FridgeScope scope) {
        return switch (scope) {
            case ALL -> item -> !item.basicSeasoning();
            case MEALKIT -> item -> item.ingredientType() == IngredientType.MEALKIT;
            case IMMINENT -> item -> item.status() != FridgeItemStatus.NORMAL;
        };
    }

    /**
     * 말한 이름마다 냉장고 줄. 사전 재료면 그 재료와 같은 종류(버섯 → 표고버섯) 줄, 사전에 없거나 밀키트면 등록 이름에 그 이름이 들어간 줄.
     * 재료 매칭이 실패하면(DB·검색 장애) 모두 이름으로 찾는다.
     *
     * @return 말한 이름(순서대로)별 줄. 없으면 빈 목록
     */
    private Map<String, List<FridgeItemResponse>> itemsByName(List<FridgeItemResponse> items, List<String> names) {
        Map<String, Ingredient> matched;
        try {
            matched = ingredientMatcher.matchAll(names);
        } catch (RuntimeException e) {
            log.warn("상담 냉장고 보기 재료 매칭 실패, 이름으로 찾음 - 이름: {}, 원인: {}", names, e.toString());
            matched = Map.of();
        }
        Map<String, Ingredient> dictionary = new LinkedHashMap<>();
        matched.forEach((name, ingredient) -> {
            if (ingredient != null && !ingredient.isCustom() && !ingredient.isMealkit()) {
                dictionary.put(name, ingredient);
            }
        });
        Map<String, List<Long>> sameKind = dictionary.isEmpty() ? Map.of()
                : ingredientService.findSameKindIdsByNames(dictionary.values().stream().map(Ingredient::getName).distinct().toList());

        Map<String, List<FridgeItemResponse>> byName = new LinkedHashMap<>();
        for (String name : names) {
            Ingredient ingredient = dictionary.get(name);
            Predicate<FridgeItemResponse> matches;
            if (ingredient == null) {
                String target = compact(name);
                matches = item -> compact(item.rawName()).contains(target)
                        || (item.ingredientName() != null && compact(item.ingredientName()).contains(target));
            } else {
                Set<Long> ids = new HashSet<>(sameKind.getOrDefault(ingredient.getName().trim(), List.of()));
                ids.add(ingredient.getId());
                matches = item -> ids.contains(item.ingredientId());
            }
            byName.put(name, items.stream().filter(matches).toList());
        }
        return byName;
    }

    // 일반 재료는 재료 이름, 밀키트·직접 입력은 등록 이름 ("된장국 밀키트")
    static String displayName(FridgeItemResponse item) {
        if (item.ingredientType() == IngredientType.NORMAL && item.ingredientName() != null) {
            return item.ingredientName();
        }
        return item.rawName();
    }

    // 밀키트 줄의 상품 이미지는 상품 조회 한 번으로 붙인다 (product_id가 없으면 이미지 없이)
    private List<FridgeItemCard> toCards(List<FridgeItemResponse> items) {
        Set<Long> productIds = items.stream()
                .filter(item -> item.ingredientType() == IngredientType.MEALKIT)
                .map(FridgeItemResponse::productId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> images = productIds.isEmpty() ? Map.of()
                : productService.getPublicMealkits(productIds).stream()
                .filter(mealkit -> mealkit.getProduct().getImageUrl() != null)
                .collect(Collectors.toMap(mealkit -> mealkit.getProduct().getId(), mealkit -> mealkit.getProduct().getImageUrl(),
                        (first, second) -> first));
        List<FridgeItemCard> cards = new ArrayList<>();
        for (FridgeItemResponse item : items) {
            boolean mealkit = item.ingredientType() == IngredientType.MEALKIT;
            cards.add(new FridgeItemCard(item.id(), item.ingredientId(), displayName(item), item.rawName(), item.quantity(),
                    item.baseUnit(), item.dDay(), item.status(), mealkit, item.productId(),
                    mealkit && item.productId() != null ? images.get(item.productId()) : null));
        }
        return cards;
    }

    private static String compact(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }
}
