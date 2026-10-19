package org.example.takecareofmyfridgebe.domain.fridge.facade;

import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemCreateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.fridge.service.FridgeService;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.service.IngredientMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FridgeFacade 단위 테스트.
 * 재료 등록 시 IngredientMatcher 매핑, 보관방식·소비기한 권장값 계산,
 * 그리고 사용자 입력 원문(rawName) 보존 여부를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FridgeFacadeTest {

    @Mock
    private IngredientMatcher ingredientMatcher;

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

        when(ingredientMatcher.match("[특가] 국산 두부 300g")).thenReturn(tofu);
        when(ingredientMatcher.match("엄마표 겉절이")).thenReturn(custom);
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
}
