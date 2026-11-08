package org.example.takecareofmyfridgebe.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.ItemLookup;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.FridgeItemCard;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ProductCard;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.ShoppingProduct;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.chat.dto.SavedRecommendation;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductListRequest;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductSort;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.domain.sale.dto.ActiveSale;
import org.example.takecareofmyfridgebe.domain.sale.dto.SellingPrice;
import org.example.takecareofmyfridgebe.domain.sale.service.SaleEventReader;
import org.example.takecareofmyfridgebe.domain.search.config.RecipeSearchProperties;
import org.example.takecareofmyfridgebe.domain.search.service.ProductSearchService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 상담 냉장고 보기 답 (FRIDGE)과 사기 답 (SHOP). 냉장고 데이터·판매 상품으로 카드와 서버 문장을 만들고, 답 생성 AI는 부르지 않는다.
 * 냉장고 보기:
 * <ul>
 *   <li>재료 이름을 말했으면 그 재료(같은 종류 포함, 기본 양념도)만, 아니면 범위(전체는 기본 양념 제외·밀키트·임박)로 고른다</li>
 *   <li>소비기한 순이고 지난 줄은 맨 아래에 둔다. 카드는 최대 {@value #CARD_LIMIT}장, 글은 FridgeReplyWriter가 쓴다</li>
 *   <li>밀키트 줄은 fridge.product_id로 상품 이미지를 붙인다</li>
 *   <li>카드는 저장하지 않는다 (다시 불러오면 그때 상태의 글만 보인다)</li>
 *   <li>말한 재료가 냉장고에 없으면 "○○는 지금 냉장고에 없어요."와 살 상품 카드를 붙인다</li>
 * </ul>
 * 사기: 이름마다 상품 하나 (재료 사전 상품 → 상품 검색 보충). 상품 카드는 저장하고 다시 불러올 때 지금 가격으로 만든다.
 * 냉장고·재료 매칭·같은 종류·밀키트 상품 조회는 한 번씩이고, 필요 없으면 조회하지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatFridgeShopFacade {

    static final int CARD_LIMIT = 10;
    // 사기 답에서 재료·상품 이름을 못 뽑았을 때
    static final String NO_NAME_REPLY = "어떤 재료를 찾으시는지 이름으로 말씀해 주시면 살 수 있는 상품을 찾아 드릴게요.";
    // 할인 상품을 물었을 때 보여 줄 카드 수 (더 있으면 상품 메뉴로 안내)
    static final int SALE_SHOWN = 3;
    // 화면 상단 상품 메뉴 이름 (Header.tsx)
    private static final String PRODUCT_MENU = "재료/밀키트";

    private final FridgeService fridgeService;
    private final IngredientMatcher ingredientMatcher;
    private final IngredientService ingredientService;
    private final ProductService productService;
    private final ProductSearchService productSearchService;
    private final SaleEventReader saleEventReader;
    private final RecipeSearchProperties recipeSearchProperties;

    public RecommendationReply fridge(Long memberId, ItemLookup lookup) {
        List<FridgeItemResponse> items = fridgeService.getItems(memberId, FridgeSortType.EXPIRATION);
        List<FridgeItemResponse> shown;
        List<String> missing = List.of();
        List<ProductCard> products = List.of();
        boolean named = !lookup.names().isEmpty();
        if (named) {
            Map<String, Ingredient> dictionary = dictionaryIngredients(lookup.names());
            Map<String, List<FridgeItemResponse>> byName = itemsByName(items, lookup.names(), dictionary);
            shown = byName.values().stream().flatMap(List::stream).distinct().toList();
            missing = byName.entrySet().stream().filter(entry -> entry.getValue().isEmpty()).map(Map.Entry::getKey).toList();
            // 냉장고에 없는 재료는 살 상품 카드를 붙인다 (사기 답과 같은 방법으로 찾는다)
            products = missing.isEmpty() ? List.of() : productCards(missing, dictionary);
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
        log.info("상담 냉장고 보기 - memberId: {}, 범위: {}, 말한 재료: {}, 냉장고 {}줄 중 {}줄, 없는 재료: {}, 카드 {}장, 상품 카드 {}장",
                memberId, lookup.scope(), lookup.names(), items.size(), ordered.size(), missing, cards.size(), products.size());
        return RecommendationReply.fixed(ChatRecommendationsEventResponse.fridge(cards, products), reply);
    }

    /**
     * 상담 사기 답 (SHOP). 말한 재료·상품 이름마다 살 상품 하나를 카드로 보여 주고, 서버 문장으로 답한다 (답 생성 AI 없음).
     * 장바구니에는 직접 담지 않는다 (카드의 [담기]로 사용자가 담는다).
     */
    public RecommendationReply shop(Long memberId, ItemLookup lookup) {
        List<String> names = lookup.names();
        if (lookup.saleOnly() && names.isEmpty()) {
            return saleProducts(memberId, lookup.productType());
        }
        if (lookup.saleOnly()) {
            List<ProductCard> cards = productCards(names, dictionaryIngredients(names));
            Set<String> found = cards.stream().map(ProductCard::name).collect(Collectors.toSet());
            List<String> missing = names.stream().filter(name -> !found.contains(name)).toList();
            log.info("상담 사기(할인 확인) - memberId: {}, 말한 이름: {}, 상품 카드: {}, 상품 없음: {}", memberId, names,
                    cards.stream().map(card -> card.name() + "=" + card.product().productId()).toList(), missing);
            return RecommendationReply.fixed(ChatRecommendationsEventResponse.products(cards), saleCheckReply(cards, missing));
        }
        if (names.isEmpty()) {
            log.info("상담 사기 - memberId: {}, 말한 이름 없음", memberId);
            return RecommendationReply.fixed(ChatRecommendationsEventResponse.products(List.of()), NO_NAME_REPLY);
        }
        List<ProductCard> cards = productCards(names, dictionaryIngredients(names));
        Set<String> found = cards.stream().map(ProductCard::name).collect(Collectors.toSet());
        List<String> missing = names.stream().filter(name -> !found.contains(name)).toList();
        log.info("상담 사기 - memberId: {}, 말한 이름: {}, 상품 카드: {}, 상품 없음: {}", memberId, names,
                cards.stream().map(card -> card.name() + "=" + card.product().productId()).toList(), missing);
        return RecommendationReply.fixed(ChatRecommendationsEventResponse.products(cards), shopReply(cards, missing));
    }

    /**
     * 할인 중인 상품 ("세일하는 재료 있어?", "할인하는 밀키트 뭐 있어?"). 진행 중인 할인 전체에서 종류로 거르고,
     * 지금 살 수 없는 것(상품 품절·번개 드롭 수량 0)은 빼고, 할인율 높은 순 → 상품 ID 순으로 {@value #SALE_SHOWN}장까지 보여 준다.
     * 더 있으면 "재료/밀키트" 메뉴에서 보라고 쓴다. 할인·상품 조회는 한 번씩이다.
     */
    private RecommendationReply saleProducts(Long memberId, ProductType productType) {
        Map<Long, ActiveSale> sales = saleEventReader.findAllActiveSales();
        List<Product> products = sales.isEmpty() ? List.of() : productService.getProductsByIds(List.copyOf(sales.keySet()));
        List<Product> onSale = products.stream()
                .filter(product -> productType == null || product.getProductType() == productType)
                .filter(product -> !SellingPrice.of(product, sales.get(product.getId())).soldOut())
                .sorted(Comparator.comparingInt((Product product) -> sales.get(product.getId()).discountRate()).reversed()
                        .thenComparing(Product::getId))
                .toList();
        List<ProductCard> cards = onSale.stream()
                .limit(SALE_SHOWN)
                .map(product -> new ProductCard(product.getName(),
                        ShoppingProduct.from(product, SellingPrice.of(product, sales.get(product.getId())))))
                .toList();
        log.info("상담 사기(할인 상품) - memberId: {}, 종류: {}, 진행 중 할인 {}건 중 살 수 있는 {}건, 카드: {}", memberId,
                productType, sales.size(), onSale.size(), cards.stream().map(card -> card.product().productId()).toList());
        return RecommendationReply.fixed(ChatRecommendationsEventResponse.products(cards),
                saleListReply(productType, cards.size(), onSale.size()));
    }

    // "지금 할인 중인 재료예요. 더 많은 할인 상품은 재료/밀키트 메뉴에서 확인해 주세요." / "지금 할인 중인 밀키트가 없어요."
    static String saleListReply(ProductType productType, int shown, int total) {
        String label = productType == ProductType.MEALKIT ? "밀키트" : productType == ProductType.INGREDIENT ? "재료" : "상품";
        if (shown == 0) {
            return "지금 할인 중인 " + label + (hasFinalConsonant(label) ? "이" : "가") + " 없어요.";
        }
        String head = "지금 할인 중인 " + label + (hasFinalConsonant(label) ? "이에요." : "예요.");
        return total > shown ? head + " 더 많은 할인 상품은 " + PRODUCT_MENU + " 메뉴에서 확인해 주세요." : head;
    }

    // "두부는 지금 할인 중이에요. 대파는 지금 할인 중이 아니에요. 참치캔은 지금 파는 상품이 없어요."
    static String saleCheckReply(List<ProductCard> cards, List<String> missing) {
        List<String> onSale = cards.stream().filter(card -> card.product().salePrice() != null).map(ProductCard::name).toList();
        List<String> notOnSale = cards.stream().filter(card -> card.product().salePrice() == null).map(ProductCard::name).toList();
        List<String> sentences = new ArrayList<>();
        if (!onSale.isEmpty()) {
            sentences.add(String.join(", ", onSale) + topicParticle(onSale.get(onSale.size() - 1)) + " 지금 할인 중이에요.");
        }
        if (!notOnSale.isEmpty()) {
            sentences.add(String.join(", ", notOnSale) + topicParticle(notOnSale.get(notOnSale.size() - 1))
                    + " 지금 할인 중이 아니에요.");
        }
        if (!missing.isEmpty()) {
            sentences.add(String.join(", ", missing) + topicParticle(missing.get(missing.size() - 1)) + " 지금 파는 상품이 없어요.");
        }
        return String.join(" ", sentences);
    }

    // "당근은 아래 상품으로 담을 수 있어요. 참치캔은 지금 파는 상품이 없어요."
    static String shopReply(List<ProductCard> cards, List<String> missing) {
        List<String> sentences = new ArrayList<>();
        if (!cards.isEmpty()) {
            List<String> names = cards.stream().map(ProductCard::name).toList();
            sentences.add(String.join(", ", names) + topicParticle(names.get(names.size() - 1)) + " 아래 상품으로 담을 수 있어요.");
        }
        if (!missing.isEmpty()) {
            sentences.add(String.join(", ", missing) + topicParticle(missing.get(missing.size() - 1)) + " 지금 파는 상품이 없어요.");
        }
        return String.join(" ", sentences);
    }

    /**
     * 이름마다 살 상품 하나. 재료 사전 이름이면 그 재료에 이어진 상품 중 ShoppingProductPicker가 고른 것(장볼 재료 [담기]와 같음),
     * 사전에 없거나 이어진 상품이 없으면 상품 검색 1등으로 보충한다 (ES를 쓰지 않으면 DB 검색). 검색이 실패한 이름은 상품 없음으로 둔다.
     *
     * @return 말한 순서대로 상품을 찾은 이름의 카드
     */
    private List<ProductCard> productCards(List<String> names, Map<String, Ingredient> dictionary) {
        Map<Long, ShoppingProduct> byIngredient = Map.of();
        Set<Long> ingredientIds = dictionary.values().stream().map(Ingredient::getId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!ingredientIds.isEmpty()) {
            List<IngredientProduct> links = productService.getPublicIngredientProducts(ingredientIds);
            byIngredient = links.isEmpty() ? Map.of() : ShoppingProductPicker.pick(links,
                    saleEventReader.findSellingPrices(links.stream().map(IngredientProduct::getProduct).distinct().toList()));
        }
        Map<String, Product> searched = new LinkedHashMap<>();
        for (String name : names) {
            Ingredient ingredient = dictionary.get(name);
            if (ingredient == null || !byIngredient.containsKey(ingredient.getId())) {
                searchProduct(name).ifPresent(product -> searched.put(name, product));
            }
        }
        Map<Long, SellingPrice> prices = searched.isEmpty() ? Map.of()
                : saleEventReader.findSellingPrices(searched.values().stream().distinct().toList());
        List<ProductCard> cards = new ArrayList<>();
        for (String name : names) {
            Ingredient ingredient = dictionary.get(name);
            ShoppingProduct linked = ingredient == null ? null : byIngredient.get(ingredient.getId());
            if (linked != null) {
                cards.add(new ProductCard(name, linked));
                continue;
            }
            Product product = searched.get(name);
            SellingPrice price = product == null ? null : prices.get(product.getId());
            if (price != null) {
                cards.add(new ProductCard(name, ShoppingProduct.from(product, price)));
            }
        }
        return cards;
    }

    // 상품 검색 1등 (관련도 순, 판매 중지 제외). ES를 쓰지 않으면 DB 검색. 실패하면 빈 값
    private Optional<Product> searchProduct(String name) {
        ProductListRequest request = new ProductListRequest(ProductSort.RELEVANCE, name, null, null, null, 1, 1);
        try {
            List<Long> ids = recipeSearchProperties.useElasticSearch()
                    ? productSearchService.search(request).productIds()
                    : productService.getProducts(request).getContent().stream().map(Product::getId).toList();
            return ids.isEmpty() ? Optional.empty() : productService.getProductsByIds(ids).stream().findFirst();
        } catch (IOException | RuntimeException e) {
            log.warn("상담 사기 상품 검색 실패, 상품 없음으로 - 이름: {}, 원인: {}", name, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 저장된 상품 카드를 다시 만든다 (사기 답·냉장고 보기의 없는 재료). 순서·이름은 저장값, 이름·이미지·가격·품절은 지금 상품 기준.
     * 상품 종류가 재료(INGREDIENT)인 것만 만든다 (밀키트는 밀키트 카드로 다시 만든다). 판매 중지·지워진 상품은 뺀다.
     * 상품·판매가 조회는 메시지 수와 상관없이 한 번씩이고, 상품 카드가 없으면 조회하지 않는다.
     *
     * @return AI 메시지 ID별 상품 카드 (보여 준 순서). 상품 카드가 없는 메시지는 맵에 없다
     */
    public Map<Long, List<ProductCard>> rebuildProductCards(List<SavedRecommendation> all) {
        List<SavedRecommendation> saved = all.stream().filter(SavedRecommendation::isProduct).toList();
        if (saved.isEmpty()) {
            return Map.of();
        }
        Map<Long, Product> products = productService.getProductsByIds(
                        saved.stream().map(SavedRecommendation::productId).distinct().toList()).stream()
                .filter(product -> product.getProductType() == ProductType.INGREDIENT)
                .collect(Collectors.toMap(Product::getId, product -> product));
        if (products.isEmpty()) {
            return Map.of();
        }
        Map<Long, SellingPrice> prices = saleEventReader.findSellingPrices(List.copyOf(products.values()));
        Map<Long, List<ProductCard>> cards = new LinkedHashMap<>();
        saved.stream()
                .sorted(Comparator.comparing(SavedRecommendation::messageId).thenComparingInt(SavedRecommendation::displayOrder))
                .forEach(recommendation -> {
                    Product product = products.get(recommendation.productId());
                    SellingPrice price = product == null ? null : prices.get(product.getId());
                    if (price == null) {
                        return;
                    }
                    String name = recommendation.description() == null ? product.getName() : recommendation.description();
                    cards.computeIfAbsent(recommendation.messageId(), id -> new ArrayList<>())
                            .add(new ProductCard(name, ShoppingProduct.from(product, price)));
                });
        return cards;
    }

    private static String topicParticle(String word) {
        return hasFinalConsonant(word) ? "은" : "는";
    }

    private static boolean hasFinalConsonant(String word) {
        char last = word.charAt(word.length() - 1);
        return last >= '가' && last <= '힣' && (last - '가') % 28 != 0;
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
     *
     * @param dictionary 말한 이름별 재료 사전 재료 (dictionaryIngredients)
     * @return 말한 이름(순서대로)별 줄. 없으면 빈 목록
     */
    private Map<String, List<FridgeItemResponse>> itemsByName(List<FridgeItemResponse> items, List<String> names,
                                                              Map<String, Ingredient> dictionary) {
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

    // 말한 이름별 재료 사전 재료 (직접 입력·밀키트 재료로 잡힌 이름은 뺀다). 매칭이 실패하면(DB·검색 장애) 빈 맵이라 모두 이름으로 찾는다
    private Map<String, Ingredient> dictionaryIngredients(List<String> names) {
        Map<String, Ingredient> matched;
        try {
            matched = ingredientMatcher.matchAll(names);
        } catch (RuntimeException e) {
            log.warn("상담 냉장고·사기 재료 매칭 실패, 이름으로 찾음 - 이름: {}, 원인: {}", names, e.toString());
            return Map.of();
        }
        Map<String, Ingredient> dictionary = new LinkedHashMap<>();
        matched.forEach((name, ingredient) -> {
            if (ingredient != null && !ingredient.isCustom() && !ingredient.isMealkit()) {
                dictionary.put(name, ingredient);
            }
        });
        return dictionary;
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
