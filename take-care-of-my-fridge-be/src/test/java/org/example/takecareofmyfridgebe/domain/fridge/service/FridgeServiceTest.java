package org.example.takecareofmyfridgebe.domain.fridge.service;

import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FridgeService 단위 테스트.
 * Repository 조회 결과가 FridgeItemResponse DTO로 변환될 때
 * 연관된 Ingredient 정보와 임박(imminent)·만료(expired) 상태가 정확히 매핑되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FridgeServiceTest {

    @Mock
    private FridgeRepository fridgeRepository;

    @InjectMocks
    private FridgeService fridgeService;

    /**
     * 1) 오늘+2일 남은 재료(수동 등록, MANUAL) -> imminent = true, expired = false
     * 2) 어제 날짜로 지난 재료(커머스 입고, COMMERCE) -> imminent = false, expired = true
     * 두 엔티티를 조회했을 때 각각의 필드와 상태값이 FridgeItemResponse로 올바르게 변환되는지 확인한다.
     */
    @Test
    void getItemsMapsFridgeEntitiesToResponses() {
        // Given: 테스트용 표준 재료(두부)와 냉장고 재료 엔티티 2건(임박 1건, 만료 1건) 생성
        LocalDate today = LocalDate.now();
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("두부")
                .baseUnit("모")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(5)
                .basicSeasoning(false)
                .build();
        ReflectionTestUtils.setField(ingredient, "id", 10L);

        // 소비기한이 D-2인 임박 재료 (직접 등록)
        Fridge imminentFridge = Fridge.builder()
                .memberId(7L)
                .ingredient(ingredient)
                .rawName("국산 두부")
                .quantity(new BigDecimal("2.00"))
                .storage(StorageType.REFRIGERATED)
                .expirationDate(today.plusDays(2))
                .source(FridgeSource.MANUAL)
                .build();
        ReflectionTestUtils.setField(imminentFridge, "id", 1L);
        ReflectionTestUtils.setField(imminentFridge, "createdAt", LocalDateTime.of(2026, 10, 18, 9, 0));
        ReflectionTestUtils.setField(imminentFridge, "updatedAt", LocalDateTime.of(2026, 10, 18, 9, 30));

        // 소비기한이 하루 지난 만료 재료 (커머스 구매 입고)
        Fridge expiredFridge = Fridge.builder()
                .memberId(7L)
                .ingredient(ingredient)
                .orderItemId(55L)
                .rawName("찌개용 두부")
                .quantity(new BigDecimal("1.00"))
                .storage(StorageType.REFRIGERATED)
                .expirationDate(today.minusDays(1))
                .source(FridgeSource.COMMERCE)
                .build();
        ReflectionTestUtils.setField(expiredFridge, "id", 2L);

        when(fridgeRepository.findItemsByCondition(
                eq(7L),
                eq(StorageType.REFRIGERATED),
                eq(FridgeSortType.EXPIRATION),
                eq(false),
                any(LocalDate.class)
        )).thenReturn(List.of(imminentFridge, expiredFridge));

        // When: 서비스 조회 실행
        List<FridgeItemResponse> result = fridgeService.getItems(
                7L,
                StorageType.REFRIGERATED,
                FridgeSortType.EXPIRATION,
                false
        );

        // Then: 첫 번째 재료(임박)와 두 번째 재료(만료)의 응답 DTO 필드 및 계산값 검증
        assertEquals(2, result.size());

        FridgeItemResponse first = result.get(0);
        assertEquals(1L, first.id());
        assertEquals(10L, first.ingredientId());
        assertEquals("두부", first.ingredientName());
        assertEquals("모", first.baseUnit());
        assertEquals("국산 두부", first.rawName());
        assertEquals(new BigDecimal("2.00"), first.quantity());
        assertEquals(StorageType.REFRIGERATED, first.storage());
        assertEquals(today.plusDays(2), first.expirationDate());
        assertEquals(FridgeSource.MANUAL, first.source());
        assertNull(first.orderItemId());
        assertTrue(first.imminent());
        assertFalse(first.expired());
        assertEquals(LocalDateTime.of(2026, 10, 18, 9, 0), first.createdAt());
        assertEquals(LocalDateTime.of(2026, 10, 18, 9, 30), first.updatedAt());

        FridgeItemResponse second = result.get(1);
        assertEquals(2L, second.id());
        assertEquals(55L, second.orderItemId());
        assertEquals(FridgeSource.COMMERCE, second.source());
        assertFalse(second.imminent());
        assertTrue(second.expired());

        verify(fridgeRepository).findItemsByCondition(
                eq(7L),
                eq(StorageType.REFRIGERATED),
                eq(FridgeSortType.EXPIRATION),
                eq(false),
                any(LocalDate.class)
        );
    }
}
