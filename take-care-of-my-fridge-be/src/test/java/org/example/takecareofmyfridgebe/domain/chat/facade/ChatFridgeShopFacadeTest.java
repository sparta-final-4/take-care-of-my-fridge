package org.example.takecareofmyfridgebe.domain.chat.facade;

import org.example.takecareofmyfridgebe.domain.chat.dto.ChatIntentResolution.ItemLookup;
import org.example.takecareofmyfridgebe.domain.chat.dto.ChatRecommendationsEventResponse.FridgeItemCard;
import org.example.takecareofmyfridgebe.domain.chat.dto.FridgeScope;
import org.example.takecareofmyfridgebe.domain.chat.dto.RecommendationReply;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemStatus;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.IngredientType;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.product.entity.MealkitProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductStatus;
import org.example.takecareofmyfridgebe.domain.product.entity.ProductType;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatFridgeShopFacadeTest {

    private static final Long MEMBER_ID = 1L;

    private FridgeService fridgeService;
    private IngredientMatcher ingredientMatcher;
    private IngredientService ingredientService;
    private ProductService productService;
    private ChatFridgeShopFacade facade;

    @BeforeEach
    void setUp() {
        fridgeService = mock(FridgeService.class);
        ingredientMatcher = mock(IngredientMatcher.class);
        ingredientService = mock(IngredientService.class);
        productService = mock(ProductService.class);
        facade = new ChatFridgeShopFacade(fridgeService, ingredientMatcher, ingredientService, productService);
    }

    @Test
    @DisplayName("전체 보기는 기본 양념을 빼고 남은 줄을 소비기한 순으로, 지난 줄은 맨 아래에 두며 글과 카드를 같은 순서로 만든다")
    void showsAllWithoutBasicSeasoning_expiredLast() {
        givenFridge(
                item(1L, 10L, "우유", -2, IngredientType.NORMAL, false, null),
                item(2L, 11L, "두부", 1, IngredientType.NORMAL, false, null),
                item(3L, 12L, "소금", 100, IngredientType.NORMAL, true, null),
                item(4L, 13L, "대파", 3, IngredientType.NORMAL, false, null));

        RecommendationReply reply = facade.fridge(MEMBER_ID, new ItemLookup(FridgeScope.ALL, List.of()));

        assertThat(reply.fixedReply()).isEqualTo("냉장고에 두부(내일까지), 대파(3일 남음)가 있어요. 우유는 지났어요.");
        assertThat(reply.event().fridgeItems()).extracting(FridgeItemCard::fridgeId).containsExactly(2L, 4L, 1L);
        assertThat(reply.event().fridgeItems().get(2).status()).isEqualTo(FridgeItemStatus.EXPIRED);
        assertThat(reply.event().recipes()).isEmpty();
        assertThat(reply.event().empty()).isFalse();
    }

    @Test
    @DisplayName("밀키트 보기는 밀키트 줄만 등록 이름으로 보여 주고, product_id가 있으면 상품 이미지를 붙인다")
    void showsMealkitsWithProductImage() {
        givenFridge(
                item(1L, 10L, "두부", 1, IngredientType.NORMAL, false, null),
                item(2L, 99L, "된장국 밀키트", 2, IngredientType.MEALKIT, false, 24L),
                item(3L, 99L, "만두 밀키트", 5, IngredientType.MEALKIT, false, null));
        when(productService.getPublicMealkits(any())).thenReturn(List.of(mealkit(24L, "img/24.png")));

        RecommendationReply reply = facade.fridge(MEMBER_ID, new ItemLookup(FridgeScope.MEALKIT, List.of()));

        assertThat(reply.fixedReply()).isEqualTo("냉장고에 된장국 밀키트(모레까지), 만두 밀키트가 있어요.");
        assertThat(reply.event().fridgeItems()).extracting(FridgeItemCard::name, FridgeItemCard::productId, FridgeItemCard::imageUrl)
                .containsExactly(
                        tuple("된장국 밀키트", 24L, "img/24.png"),
                        tuple("만두 밀키트", null, null));
    }

    @Test
    @DisplayName("임박 보기는 D-3 이내와 지난 줄만 보여 준다")
    void showsImminentAndExpiredOnly() {
        givenFridge(
                item(1L, 10L, "우유", -1, IngredientType.NORMAL, false, null),
                item(2L, 11L, "두부", 2, IngredientType.NORMAL, false, null),
                item(3L, 12L, "양파", 10, IngredientType.NORMAL, false, null));

        RecommendationReply reply = facade.fridge(MEMBER_ID, new ItemLookup(FridgeScope.IMMINENT, List.of()));

        assertThat(reply.event().fridgeItems()).extracting(FridgeItemCard::fridgeId).containsExactly(2L, 1L);
        verify(productService, never()).getPublicMealkits(any());
    }

    @Test
    @DisplayName("재료를 말했으면 그 재료와 같은 종류(기본 양념 포함) 줄만 보여 주고, 없는 재료는 없다고 쓴다")
    void showsNamedIngredients_andMissing() {
        givenFridge(
                item(1L, 10L, "표고버섯", 2, IngredientType.NORMAL, false, null),
                item(2L, 11L, "두부", 1, IngredientType.NORMAL, false, null),
                item(3L, 12L, "간장", 100, IngredientType.NORMAL, true, null));
        when(ingredientMatcher.matchAll(List.of("버섯", "간장", "당근"))).thenReturn(Map.of(
                "버섯", ingredient(45L, "버섯"), "간장", ingredient(12L, "간장"), "당근", ingredient(20L, "당근")));
        when(ingredientService.findSameKindIdsByNames(any())).thenReturn(Map.of("버섯", List.of(10L, 45L), "간장", List.of(12L)));

        RecommendationReply reply = facade.fridge(MEMBER_ID, new ItemLookup(FridgeScope.ALL, List.of("버섯", "간장", "당근")));

        assertThat(reply.event().fridgeItems()).extracting(FridgeItemCard::fridgeId).containsExactly(1L, 3L);
        assertThat(reply.fixedReply()).isEqualTo("냉장고에 표고버섯(모레까지), 간장이 있어요. 당근은 지금 냉장고에 없어요.");
    }

    @Test
    @DisplayName("사전에 없는 이름(밀키트 이름 등)은 등록 이름에 그 이름이 들어간 줄로 찾는다")
    void findsByRawName_whenNotInDictionary() {
        givenFridge(item(2L, 99L, "된장국 밀키트", 2, IngredientType.MEALKIT, false, null));
        when(ingredientMatcher.matchAll(List.of("된장국 밀키트"))).thenReturn(Map.of("된장국 밀키트", mealkitIngredient()));

        RecommendationReply reply = facade.fridge(MEMBER_ID, new ItemLookup(FridgeScope.ALL, List.of("된장국 밀키트")));

        assertThat(reply.event().fridgeItems()).extracting(FridgeItemCard::fridgeId).containsExactly(2L);
    }

    @Test
    @DisplayName("카드는 최대 10장이고 글은 6개 + \"외 N개\"로 줄인다")
    void limitsCards() {
        givenFridge(IntStream.rangeClosed(1, 12)
                .mapToObj(i -> item((long) i, 100L + i, "재료" + i, 10 + i, IngredientType.NORMAL, false, null))
                .toArray(FridgeItemResponse[]::new));

        RecommendationReply reply = facade.fridge(MEMBER_ID, new ItemLookup(FridgeScope.ALL, List.of()));

        assertThat(reply.event().fridgeItems()).hasSize(ChatFridgeShopFacade.CARD_LIMIT);
        assertThat(reply.fixedReply()).endsWith("재료6 외 6개가 있어요.");
    }

    @Test
    @DisplayName("냉장고가 비었으면 안내 글만 보내고 카드는 비운다")
    void writesEmptyReply_whenNothing() {
        givenFridge();

        RecommendationReply reply = facade.fridge(MEMBER_ID, new ItemLookup(FridgeScope.MEALKIT, List.of()));

        assertThat(reply.fixedReply()).isEqualTo("냉장고에 밀키트가 없어요.");
        assertThat(reply.event().empty()).isTrue();
    }

    private void givenFridge(FridgeItemResponse... items) {
        // 실제 조회와 같이 소비기한 순 (지난 줄이 앞)
        List<FridgeItemResponse> sorted = new ArrayList<>(List.of(items));
        sorted.sort((a, b) -> Long.compare(a.dDay(), b.dDay()));
        when(fridgeService.getItems(any(), any())).thenReturn(sorted);
    }

    private static FridgeItemResponse item(Long id, Long ingredientId, String name, long dDay, IngredientType type,
                                           boolean basicSeasoning, Long productId) {
        FridgeItemStatus status = FridgeItemStatus.of(dDay);
        return new FridgeItemResponse(id, ingredientId, type == IngredientType.MEALKIT ? "밀키트" : name, "개", name,
                BigDecimal.ONE, StorageType.REFRIGERATED, LocalDate.now().plusDays(dDay),
                productId == null ? FridgeSource.MANUAL : FridgeSource.COMMERCE, null,
                status == FridgeItemStatus.IMMINENT, status == FridgeItemStatus.EXPIRED, dDay, status, type, basicSeasoning,
                LocalDateTime.now(), LocalDateTime.now(), productId);
    }

    private static Ingredient ingredient(Long id, String name) {
        Ingredient ingredient = Ingredient.builder().ingredientCode("ING_" + id).name(name).baseUnit("g").build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }

    private static Ingredient mealkitIngredient() {
        Ingredient ingredient = Ingredient.builder().ingredientCode("ING_MEALKIT").name("밀키트").baseUnit("개").build();
        ReflectionTestUtils.setField(ingredient, "id", 99L);
        return ingredient;
    }

    private static MealkitProduct mealkit(Long productId, String imageUrl) {
        Product product = Product.builder()
                .productType(ProductType.MEALKIT)
                .name("된장국 밀키트")
                .price(9000)
                .stock(10)
                .storage(StorageType.REFRIGERATED)
                .shelfDays(3)
                .status(ProductStatus.ON_SALE)
                .imageUrl(imageUrl)
                .build();
        ReflectionTestUtils.setField(product, "id", productId);
        return MealkitProduct.builder().product(product).servings(2).build();
    }
}
