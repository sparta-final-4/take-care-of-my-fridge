package org.example.takecareofmyfridgebe.domain.fridge.facade;

import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemCreateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeNameInfo;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.fridge.event.PurchaseConfirmedMessage;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientService;
import org.example.takecareofmyfridgebe.domain.product.dto.ProductDetail;
import org.example.takecareofmyfridgebe.domain.product.entity.IngredientProduct;
import org.example.takecareofmyfridgebe.domain.product.entity.Product;
import org.example.takecareofmyfridgebe.domain.product.service.ProductService;
import org.example.takecareofmyfridgebe.dummy.ProductFixture;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FridgeFacade 단위 테스트.
 * 재료 등록 및 수정 시 IngredientMatcher 매핑 호출 여부, 보관방식·소비기한 권장값 계산,
 * 그리고 사용자 입력 원문(rawName) 보존 여부를 검증한다.
 * 구매 확정 입고 시 상품 종류별 재료·수량·보관방식·소비기한 계산과 중복·빈 메시지 처리도 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FridgeFacadeTest {

    private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 10, 30, 14, 30);

    @Mock
    private IngredientMatcher ingredientMatcher;

    @Mock
    private IngredientService ingredientService;

    @Mock
    private ProductService productService;

    @Mock
    private FridgeService fridgeService;

    @InjectMocks
    private FridgeFacade fridgeFacade;

    /**
     * 1) storage와 expirationDate가 비어 있으면(null) 매핑된 재료의 권장값(defaultStorage, 오늘+defaultShelfDays)을 적용하는지
     * 2) storage와 expirationDate를 직접 입력하면 재료 권장값 대신 입력한 값을 그대로 사용하는지
     * 3) rawName은 다듬어진 이름이 아니라 사용자가 입력한 원문 그대로 엔티티에 저장되는지 검증한다.
     */
    @Test
    @SuppressWarnings("unchecked")
    void createItemsAppliesDefaultValuesWhenNullAndPreservesRawName() {
        // Given: 기본 보관방식이 냉장(REFRIGERATED)이고 기본 소비기한이 5일인 두부 표준 재료
        LocalDate today = LocalDate.now();
        Ingredient tofu = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("국산 두부")
                .baseUnit("모")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(5)
                .basicSeasoning(false)
                .build();

        Ingredient custom = Ingredient.builder()
                .ingredientCode(Ingredient.ING_CUSTOM)
                .name("기타")
                .baseUnit("개")
                .defaultStorage(StorageType.ROOM_TEMP)
                .defaultShelfDays(7)
                .basicSeasoning(false)
                .build();

        when(ingredientMatcher.matchAll(List.of("[특가] 국산 두부 300g", "엄마표 겉절이")))
                .thenReturn(Map.of("[특가] 국산 두부 300g", tofu, "엄마표 겉절이", custom));
        when(fridgeService.saveItems(anyList())).thenReturn(List.of());

        LocalDate customExpiration = today.plusDays(14);
        FridgeItemCreateRequest request = new FridgeItemCreateRequest(List.of(
                // 첫 번째 항목: storage, expirationDate 미지정 -> 두부 권장값 적용 대상
                new FridgeItemCreateRequest.Item(
                        "[특가] 국산 두부 300g",
                        new BigDecimal("2"),
                        null,
                        null
                ),
                // 두 번째 항목: storage(FROZEN), expirationDate(오늘+14일) 직접 지정 -> 지정값 유지 대상
                new FridgeItemCreateRequest.Item(
                        "엄마표 겉절이",
                        new BigDecimal("1"),
                        StorageType.FROZEN,
                        customExpiration
                )
        ));

        // When
        List<FridgeItemResponse> result = fridgeFacade.createItems(7L, request);

        // Then: FridgeService.saveItems로 전달된 Fridge 엔티티 목록 캡처 및 검증
        assertEquals(0, result.size());
        ArgumentCaptor<List<Fridge>> captor = ArgumentCaptor.forClass(List.class);
        verify(fridgeService).saveItems(captor.capture());

        List<Fridge> savedFridges = captor.getValue();
        assertEquals(2, savedFridges.size());

        // 첫 번째 재료: 원문 rawName 유지 + 두부의 권장 보관방식(REFRIGERATED) 및 권장 소비기한(오늘+5일) 적용 확인
        Fridge first = savedFridges.get(0);
        assertEquals(7L, first.getMemberId());
        assertSame(tofu, first.getIngredient());
        assertEquals("[특가] 국산 두부 300g", first.getRawName());
        assertEquals(new BigDecimal("2"), first.getQuantity());
        assertEquals(StorageType.REFRIGERATED, first.getStorage());
        assertEquals(today.plusDays(5), first.getExpirationDate());
        assertEquals(FridgeSource.MANUAL, first.getSource());

        // 두 번째 재료: 원문 rawName 유지 + 사용자가 직접 입력한 보관방식(FROZEN) 및 소비기한(오늘+14일) 적용 확인
        Fridge second = savedFridges.get(1);
        assertEquals(7L, second.getMemberId());
        assertSame(custom, second.getIngredient());
        assertEquals("엄마표 겉절이", second.getRawName());
        assertEquals(new BigDecimal("1"), second.getQuantity());
        assertEquals(StorageType.FROZEN, second.getStorage());
        assertEquals(customExpiration, second.getExpirationDate());
        assertEquals(FridgeSource.MANUAL, second.getSource());
    }

    /**
     * 재료 수정 시 요청한 rawName이 기존 재료의 rawName과 같으면
     * IngredientMatcher.match를 호출하지 않고 ingredient를 null(기존 재료 유지)로
     * FridgeService.updateItem에 전달하는지 검증한다.
     */
    @Test
    void updateItemDoesNotCallMatcherWhenRawNameIsSame() {
        // Given: 기존 rawName이 "국산 두부 300g"인 냉장고 재료
        FridgeItemUpdateRequest request = new FridgeItemUpdateRequest(
                "국산 두부 300g",
                new BigDecimal("3"),
                StorageType.FROZEN,
                LocalDate.of(2026, 10, 25)
        );
        FridgeItemResponse expectedResponse = mock(FridgeItemResponse.class);

        when(fridgeService.getNameInfo(7L, 100L)).thenReturn(new FridgeNameInfo("국산 두부 300g", false));
        when(fridgeService.updateItem(7L, 100L, null, request)).thenReturn(expectedResponse);

        // When: 동일한 rawName으로 수정 요청
        FridgeItemResponse actual = fridgeFacade.updateItem(7L, 100L, request);

        // Then: 매핑기(IngredientMatcher)를 호출하지 않고 ingredient를 null(기존 재료 유지)로 전달했는지 확인
        assertSame(expectedResponse, actual);
        verifyNoInteractions(ingredientMatcher);
        verify(fridgeService).updateItem(7L, 100L, null, request);
    }

    /**
     * 이름(rawName)을 보내지 않은 부분 수정이면 IngredientMatcher.match를 호출하지 않고
     * ingredient를 null(기존 재료 유지)로 전달하는지 검증한다.
     */
    @Test
    void updateItemDoesNotCallMatcherWhenRawNameIsNotSent() {
        FridgeItemUpdateRequest request = new FridgeItemUpdateRequest(
                null,
                null,
                null,
                LocalDate.of(2026, 11, 5)
        );
        FridgeItemResponse expectedResponse = mock(FridgeItemResponse.class);

        when(fridgeService.getNameInfo(7L, 100L)).thenReturn(new FridgeNameInfo("국산 두부 300g", false));
        when(fridgeService.updateItem(7L, 100L, null, request)).thenReturn(expectedResponse);

        FridgeItemResponse actual = fridgeFacade.updateItem(7L, 100L, request);

        assertSame(expectedResponse, actual);
        verifyNoInteractions(ingredientMatcher);
        verify(fridgeService).updateItem(7L, 100L, null, request);
    }

    /**
     * 재료 수정 시 요청한 rawName이 기존 재료의 rawName과 다르면
     * IngredientMatcher.match를 호출해 새로 매핑된 표준 재료(ingredient)를
     * FridgeService.updateItem으로 전달하는지 검증한다.
     */
    @Test
    void updateItemCallsMatcherWhenRawNameIsChanged() {
        // Given: 기존 rawName이 "두부"인 재료를 "[특가] 신라면 5개입"으로 이름을 바꿔 수정하는 상황
        Ingredient newIngredient = Ingredient.builder()
                .ingredientCode("ING_RAMEN")
                .name("신라면")
                .baseUnit("봉")
                .defaultStorage(StorageType.ROOM_TEMP)
                .defaultShelfDays(180)
                .basicSeasoning(false)
                .build();

        FridgeItemUpdateRequest request = new FridgeItemUpdateRequest(
                "[특가] 신라면 5개입",
                new BigDecimal("5"),
                StorageType.ROOM_TEMP,
                LocalDate.of(2027, 4, 20)
        );
        FridgeItemResponse expectedResponse = mock(FridgeItemResponse.class);

        when(fridgeService.getNameInfo(7L, 100L)).thenReturn(new FridgeNameInfo("두부", false));
        when(ingredientMatcher.match("[특가] 신라면 5개입")).thenReturn(newIngredient);
        when(fridgeService.updateItem(7L, 100L, newIngredient, request)).thenReturn(expectedResponse);

        // When: 변경된 rawName으로 수정 요청
        FridgeItemResponse actual = fridgeFacade.updateItem(7L, 100L, request);

        // Then: IngredientMatcher.match가 호출되고 새로 매핑된 newIngredient가 전달되었는지 확인
        assertSame(expectedResponse, actual);
        verify(ingredientMatcher).match("[특가] 신라면 5개입");
        verify(fridgeService).updateItem(7L, 100L, newIngredient, request);
    }

    /**
     * 기존 재료가 밀키트(ING_MEALKIT)이면 rawName이 바뀌어도
     * IngredientMatcher.match를 호출하지 않고 ingredient를 null(밀키트 유지)로
     * FridgeService.updateItem에 전달하는지 검증한다.
     */
    @Test
    void updateItemKeepsMealkitWhenRawNameIsChanged() {
        // Given: 기존 rawName이 "밀푀유나베 밀키트"인 밀키트 재료를 다른 이름으로 수정하는 상황
        FridgeItemUpdateRequest request = new FridgeItemUpdateRequest(
                "밀푀유나베 밀키트 2인분",
                new BigDecimal("1"),
                StorageType.REFRIGERATED,
                LocalDate.now().plusDays(3)
        );
        FridgeItemResponse expectedResponse = mock(FridgeItemResponse.class);

        when(fridgeService.getNameInfo(7L, 200L)).thenReturn(new FridgeNameInfo("밀푀유나베 밀키트", true));
        when(fridgeService.updateItem(7L, 200L, null, request)).thenReturn(expectedResponse);

        // When
        FridgeItemResponse actual = fridgeFacade.updateItem(7L, 200L, request);

        // Then: 매핑을 호출하지 않고 null(기존 밀키트 유지)을 전달
        assertSame(expectedResponse, actual);
        verifyNoInteractions(ingredientMatcher);
        verify(fridgeService).updateItem(7L, 200L, null, request);
    }

    /**
     * 재료 상품 2개를 받으면 냉장고 2줄을 만든다.
     * 수량은 확정 수량 × 상품 1개의 기본 단위 수량, 보관방식은 상품 보관 방법, 소비기한은 구매 확정일 + 권장 보관 일수이고,
     * 표시 이름은 상품명, 출처는 COMMERCE와 주문 상품 ID다.
     */
    @Test
    @SuppressWarnings("unchecked")
    void stockPurchasedItemsStocksIngredientProducts() {
        // Given: 양파 상품(1개 = 기본 단위 5)과 두부 상품(1개 = 기본 단위 1)
        Product onionProduct = ProductFixture.createProduct(1L, "양파 1kg", 3900);
        Product tofuProduct = ProductFixture.createProduct(2L, "국산 두부", 2000);
        Ingredient onion = ingredientWithId(10L, "ING_ONION", "양파");
        Ingredient tofu = ingredientWithId(11L, "ING_TOFU", "두부");
        PurchaseConfirmedMessage message = message(List.of(
                new PurchaseConfirmedMessage.Item(101L, 1L, "양파 1kg", 2),
                new PurchaseConfirmedMessage.Item(102L, 2L, "국산 두부", 3)
        ));

        when(fridgeService.getStockedOrderItemIds(List.of(101L, 102L))).thenReturn(Set.of());
        when(productService.getProductDetailsByIds(List.of(1L, 2L))).thenReturn(List.of(
                ProductDetail.ofIngredient(onionProduct, ingredientLink(onionProduct, 10L, "5")),
                ProductDetail.ofIngredient(tofuProduct, ingredientLink(tofuProduct, 11L, "1"))
        ));
        when(ingredientService.getIngredientsByIds(Set.of(10L, 11L))).thenReturn(Map.of(10L, onion, 11L, tofu));

        // When
        fridgeFacade.stockPurchasedItems(message);

        // Then
        ArgumentCaptor<List<Fridge>> captor = ArgumentCaptor.forClass(List.class);
        verify(fridgeService).stockCommerceItems(captor.capture());
        List<Fridge> fridges = captor.getValue();
        assertEquals(2, fridges.size());

        Fridge onionFridge = fridges.get(0);
        assertSame(onion, onionFridge.getIngredient());
        assertEquals(0, new BigDecimal("10").compareTo(onionFridge.getQuantity()));
        assertEquals(StorageType.ROOM_TEMP, onionFridge.getStorage());
        assertEquals(LocalDate.of(2026, 11, 13), onionFridge.getExpirationDate());
        assertEquals("양파 1kg", onionFridge.getRawName());
        assertEquals(FridgeSource.COMMERCE, onionFridge.getSource());
        assertEquals(101L, onionFridge.getOrderItemId());
        assertEquals(7L, onionFridge.getMemberId());

        Fridge tofuFridge = fridges.get(1);
        assertSame(tofu, tofuFridge.getIngredient());
        assertEquals(0, new BigDecimal("3").compareTo(tofuFridge.getQuantity()));
        assertEquals(102L, tofuFridge.getOrderItemId());
        // 밀키트가 없으면 밀키트용 기본 재료를 조회하지 않는다
        verify(ingredientService, never()).getMealkitIngredient();
    }

    /**
     * 밀키트 상품은 밀키트용 기본 재료(ING_MEALKIT)로, 확정 수량(개수) 그대로 넣는다.
     */
    @Test
    @SuppressWarnings("unchecked")
    void stockPurchasedItemsStocksMealkitAsCount() {
        // Given
        Product mealkitProduct = ProductFixture.createMealkitProduct(3L, "된장찌개 밀키트", 12900);
        Ingredient mealkit = ingredientWithId(1150L, Ingredient.ING_MEALKIT, "밀키트");
        PurchaseConfirmedMessage message = message(List.of(
                new PurchaseConfirmedMessage.Item(103L, 3L, "된장찌개 밀키트", 2)
        ));

        when(fridgeService.getStockedOrderItemIds(List.of(103L))).thenReturn(Set.of());
        when(productService.getProductDetailsByIds(List.of(3L))).thenReturn(List.of(
                ProductDetail.ofMealkit(mealkitProduct, ProductFixture.createMealkitDetail(mealkitProduct, 2))
        ));
        when(ingredientService.getIngredientsByIds(Set.of())).thenReturn(Map.of());
        when(ingredientService.getMealkitIngredient()).thenReturn(mealkit);

        // When
        fridgeFacade.stockPurchasedItems(message);

        // Then
        ArgumentCaptor<List<Fridge>> captor = ArgumentCaptor.forClass(List.class);
        verify(fridgeService).stockCommerceItems(captor.capture());
        Fridge fridge = captor.getValue().get(0);
        assertSame(mealkit, fridge.getIngredient());
        assertEquals(0, new BigDecimal("2").compareTo(fridge.getQuantity()));
        assertEquals("된장찌개 밀키트", fridge.getRawName());
        assertEquals(LocalDate.of(2026, 11, 13), fridge.getExpirationDate());
        assertEquals(FridgeSource.COMMERCE, fridge.getSource());
    }

    /**
     * 재료 상품에 연결된 재료가 없으면 PRODUCT_DETAIL_MISSING 예외를 던지고 아무것도 넣지 않는다.
     * 주문 한 건을 한 번에 저장하므로 함께 온 정상 상품도 넣지 않는다.
     */
    @Test
    void stockPurchasedItemsThrowsWhenIngredientLinkIsMissing() {
        // Given: 양파 상품은 연결이 있고, 대파 상품은 상세 행이 없음
        Product onionProduct = ProductFixture.createProduct(1L, "양파 1kg", 3900);
        Product greenOnionProduct = ProductFixture.createProduct(4L, "대파", 2500);
        Ingredient onion = ingredientWithId(10L, "ING_ONION", "양파");
        PurchaseConfirmedMessage message = message(List.of(
                new PurchaseConfirmedMessage.Item(101L, 1L, "양파 1kg", 1),
                new PurchaseConfirmedMessage.Item(104L, 4L, "대파", 1)
        ));

        when(fridgeService.getStockedOrderItemIds(List.of(101L, 104L))).thenReturn(Set.of());
        when(productService.getProductDetailsByIds(List.of(1L, 4L))).thenReturn(List.of(
                ProductDetail.ofIngredient(onionProduct, ingredientLink(onionProduct, 10L, "5")),
                ProductDetail.ofIngredient(greenOnionProduct, null)
        ));
        when(ingredientService.getIngredientsByIds(Set.of(10L))).thenReturn(Map.of(10L, onion));

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class,
                () -> fridgeFacade.stockPurchasedItems(message));
        assertEquals(ErrorCode.PRODUCT_DETAIL_MISSING, exception.getErrorCode());
        verify(fridgeService, never()).stockCommerceItems(anyList());
    }

    /**
     * 모두 환불된 주문처럼 items가 비어 있으면 아무것도 조회·저장하지 않는다.
     */
    @Test
    void stockPurchasedItemsDoesNothingWhenItemsAreEmpty() {
        // When
        fridgeFacade.stockPurchasedItems(message(List.of()));

        // Then
        verifyNoInteractions(fridgeService, productService, ingredientService);
    }

    /**
     * 이미 입고 이력이 있는 주문 상품은 걸러 내고 남은 상품만 넣는다.
     */
    @Test
    @SuppressWarnings("unchecked")
    void stockPurchasedItemsSkipsAlreadyStockedOrderItems() {
        // Given: 101은 이미 입고됨
        Product tofuProduct = ProductFixture.createProduct(2L, "국산 두부", 2000);
        Ingredient tofu = ingredientWithId(11L, "ING_TOFU", "두부");
        PurchaseConfirmedMessage message = message(List.of(
                new PurchaseConfirmedMessage.Item(101L, 1L, "양파 1kg", 2),
                new PurchaseConfirmedMessage.Item(102L, 2L, "국산 두부", 1)
        ));

        when(fridgeService.getStockedOrderItemIds(List.of(101L, 102L))).thenReturn(Set.of(101L));
        when(productService.getProductDetailsByIds(List.of(2L))).thenReturn(List.of(
                ProductDetail.ofIngredient(tofuProduct, ingredientLink(tofuProduct, 11L, "1"))
        ));
        when(ingredientService.getIngredientsByIds(Set.of(11L))).thenReturn(Map.of(11L, tofu));

        // When
        fridgeFacade.stockPurchasedItems(message);

        // Then
        ArgumentCaptor<List<Fridge>> captor = ArgumentCaptor.forClass(List.class);
        verify(fridgeService).stockCommerceItems(captor.capture());
        assertEquals(List.of(102L), captor.getValue().stream().map(Fridge::getOrderItemId).toList());
    }

    /**
     * 같은 메시지를 다시 받아 모든 주문 상품이 이미 입고됐으면 상품을 조회하지 않고 끝낸다.
     */
    @Test
    void stockPurchasedItemsDoesNothingWhenAllOrderItemsAreStocked() {
        // Given
        PurchaseConfirmedMessage message = message(List.of(
                new PurchaseConfirmedMessage.Item(101L, 1L, "양파 1kg", 2)
        ));
        when(fridgeService.getStockedOrderItemIds(List.of(101L))).thenReturn(Set.of(101L));

        // When
        fridgeFacade.stockPurchasedItems(message);

        // Then
        verifyNoInteractions(productService, ingredientService);
        verify(fridgeService, never()).stockCommerceItems(anyList());
    }

    private static PurchaseConfirmedMessage message(List<PurchaseConfirmedMessage.Item> items) {
        return new PurchaseConfirmedMessage(500L, 7L, CONFIRMED_AT, items);
    }

    private static IngredientProduct ingredientLink(Product product, Long ingredientId, String baseQuantity) {
        return IngredientProduct.builder()
                .product(product)
                .ingredientId(ingredientId)
                .unitAmount(BigDecimal.ONE)
                .unit("개")
                .baseQuantity(new BigDecimal(baseQuantity))
                .build();
    }

    private static Ingredient ingredientWithId(Long id, String code, String name) {
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode(code)
                .name(name)
                .baseUnit("개")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(7)
                .basicSeasoning(false)
                .build();
        ReflectionTestUtils.setField(ingredient, "id", id);
        return ingredient;
    }
}
