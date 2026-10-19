package org.example.takecareofmyfridgebe.domain.fridge.service;

import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemResponse;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemUpdateRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeRemoveReason;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeSortType;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemLog;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeItemLogRepository;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FridgeService 단위 테스트.
 * Repository 조회·저장·수정·삭제 결과가 정책대로 동작하는지,
 * 연관된 Ingredient 정보와 임박(imminent)·만료(expired) 상태 매핑,
 * 본인 재료 확인, 더티 체킹 수정, 소진·폐기 이력 기록 후 삭제 순서를 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FridgeServiceTest {

    @Mock
    private FridgeRepository fridgeRepository;

    @Mock
    private FridgeItemLogRepository fridgeItemLogRepository;

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

    /**
     * saveItems 호출 시 전달받은 Fridge 엔티티 목록을 saveAll로 일괄 저장하고
     * 저장된 엔티티들을 FridgeItemResponse 목록으로 변환해 반환하는지 검증한다.
     */
    @Test
    void saveItemsSavesAllEntitiesAndMapsToResponses() {
        // Given
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

        Fridge fridge = Fridge.builder()
                .memberId(7L)
                .ingredient(ingredient)
                .rawName("국산 두부 300g")
                .quantity(new BigDecimal("1.00"))
                .storage(StorageType.REFRIGERATED)
                .expirationDate(today.plusDays(5))
                .source(FridgeSource.MANUAL)
                .build();
        ReflectionTestUtils.setField(fridge, "id", 100L);

        List<Fridge> itemsToSave = List.of(fridge);
        when(fridgeRepository.saveAll(itemsToSave)).thenReturn(itemsToSave);

        // When
        List<FridgeItemResponse> responses = fridgeService.saveItems(itemsToSave);

        // Then
        assertEquals(1, responses.size());
        assertEquals(100L, responses.get(0).id());
        assertEquals(10L, responses.get(0).ingredientId());
        assertEquals("두부", responses.get(0).ingredientName());
        assertEquals("국산 두부 300g", responses.get(0).rawName());
        assertFalse(responses.get(0).imminent());
        assertFalse(responses.get(0).expired());
        verify(fridgeRepository).saveAll(itemsToSave);
    }

    /**
     * 본인 소유의 냉장고 재료를 수정할 때:
     * 1) 엔티티의 ingredient, rawName(원문 그대로), quantity, storage, expirationDate가 변경되는지
     * 2) 커머스 입고(COMMERCE) 재료라도 source와 orderItemId는 바뀌지 않고 유지되는지
     * 3) JPA 더티 체킹으로 반영하므로 fridgeRepository.save를 호출하지 않는지 검증한다.
     */
    @Test
    void updateItemUpdatesEntityViaDirtyCheckingAndPreservesSourceAndOrderItemId() {
        // Given: 커머스 입고(COMMERCE, orderItemId=55L)로 등록된 7번 회원의 냉장고 재료
        LocalDate today = LocalDate.now();
        Ingredient oldIngredient = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("두부")
                .baseUnit("모")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(5)
                .basicSeasoning(false)
                .build();
        ReflectionTestUtils.setField(oldIngredient, "id", 10L);

        Ingredient newIngredient = Ingredient.builder()
                .ingredientCode("ING_KIMCHI")
                .name("배추김치")
                .baseUnit("포기")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(30)
                .basicSeasoning(false)
                .build();
        ReflectionTestUtils.setField(newIngredient, "id", 20L);

        Fridge fridge = Fridge.builder()
                .memberId(7L)
                .ingredient(oldIngredient)
                .orderItemId(55L)
                .rawName("찌개용 두부")
                .quantity(new BigDecimal("1.00"))
                .storage(StorageType.REFRIGERATED)
                .expirationDate(today.plusDays(3))
                .source(FridgeSource.COMMERCE)
                .build();
        ReflectionTestUtils.setField(fridge, "id", 100L);

        when(fridgeRepository.findByIdAndMemberId(100L, 7L)).thenReturn(Optional.of(fridge));

        FridgeItemUpdateRequest request = new FridgeItemUpdateRequest(
                "비비고 썰은배추김치(500g)",
                new BigDecimal("2.50"),
                StorageType.FROZEN,
                today.plusDays(10)
        );

        // When: 수정 실행
        FridgeItemResponse response = fridgeService.updateItem(7L, 100L, newIngredient, request);

        // Then: 엔티티와 응답 DTO에 변경된 값이 반영되고 source·orderItemId는 그대로 유지되며 save는 미호출
        assertSame(newIngredient, fridge.getIngredient());
        assertEquals(20L, response.ingredientId());
        assertEquals("배추김치", response.ingredientName());
        assertEquals("포기", response.baseUnit());
        assertEquals("비비고 썰은배추김치(500g)", response.rawName());
        assertEquals(new BigDecimal("2.50"), response.quantity());
        assertEquals(StorageType.FROZEN, response.storage());
        assertEquals(today.plusDays(10), response.expirationDate());
        assertEquals(FridgeSource.COMMERCE, response.source());
        assertEquals(55L, response.orderItemId());

        verify(fridgeRepository, never()).save(any(Fridge.class));
    }

    /**
     * 존재하지 않거나 다른 회원의 fridgeId로 조회·수정·삭제를 시도하면
     * BusinessException(ErrorCode.FRIDGE_ITEM_NOT_FOUND) 예외가 발생하는지 검증한다.
     */
    @Test
    void getFridgeEntityThrowsNotFoundWhenOwnedByAnotherMemberOrMissing() {
        // Given: 7번 회원이 다른 회원의 재료(또는 존재하지 않는 재료) 999L을 조회
        when(fridgeRepository.findByIdAndMemberId(999L, 7L)).thenReturn(Optional.empty());

        // When & Then: FRIDGE_ITEM_NOT_FOUND 예외 발생 확인
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fridgeService.getFridgeEntity(7L, 999L)
        );
        assertEquals(ErrorCode.FRIDGE_ITEM_NOT_FOUND, exception.getErrorCode());
    }

    /**
     * 재료를 빼면서 reason(CONSUMED 또는 DISCARDED)을 전달했을 때:
     * 1) Fridge의 값(memberId, ingredient, fridgeId, rawName, 남은 수량 전부, action)을 복사하고 cookingLogId는 null인 FridgeItemLog를 생성하는지
     * 2) 반드시 기록을 먼저 저장(save)한 뒤 냉장고에서 재료를 삭제(delete)하는지 검증한다.
     */
    @Test
    void removeItemSavesLogBeforeDeletingFridgeWhenReasonRequiresLog() {
        // Given: 7번 회원이 보유한 100번 재료(국산 두부 300g, 남은 수량 1.50)
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("두부")
                .baseUnit("모")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(5)
                .basicSeasoning(false)
                .build();
        ReflectionTestUtils.setField(ingredient, "id", 10L);

        Fridge fridge = Fridge.builder()
                .memberId(7L)
                .ingredient(ingredient)
                .rawName("국산 두부 300g")
                .quantity(new BigDecimal("1.50"))
                .storage(StorageType.REFRIGERATED)
                .expirationDate(LocalDate.now().plusDays(2))
                .source(FridgeSource.MANUAL)
                .build();
        ReflectionTestUtils.setField(fridge, "id", 100L);

        when(fridgeRepository.findByIdAndMemberId(100L, 7L)).thenReturn(Optional.of(fridge));

        // When: 다 먹음(CONSUMED) 사유로 재료 빼기 실행
        fridgeService.removeItem(7L, 100L, FridgeRemoveReason.CONSUMED);

        // Then: 1) 기록(FridgeItemLog) 저장이 먼저 호출되고 2) 이어서 냉장고 재료 삭제가 호출되었는지 순서 및 필드 검증
        ArgumentCaptor<FridgeItemLog> logCaptor = ArgumentCaptor.forClass(FridgeItemLog.class);
        InOrder inOrder = inOrder(fridgeItemLogRepository, fridgeRepository);
        inOrder.verify(fridgeItemLogRepository).save(logCaptor.capture());
        inOrder.verify(fridgeRepository).delete(fridge);

        FridgeItemLog savedLog = logCaptor.getValue();
        assertEquals(7L, savedLog.getMemberId());
        assertSame(ingredient, savedLog.getIngredient());
        assertEquals(100L, savedLog.getFridgeId());
        assertEquals("국산 두부 300g", savedLog.getRawName());
        assertEquals(new BigDecimal("1.50"), savedLog.getQuantity());
        assertEquals(FridgeItemAction.CONSUMED, savedLog.getAction());
        assertNull(savedLog.getCookingLogId());
    }

    /**
     * 재료를 빼면서 reason으로 WRONG_ENTRY(잘못 등록해서 삭제)를 전달했을 때:
     * fridge_item_logs에 기록을 남기지 않고 냉장고에서만 재료를 삭제하며,
     * WRONG_ENTRY.toAction()을 직접 호출하면 BusinessException(INVALID_FRIDGE_LOG)이 발생하는지 검증한다.
     */
    @Test
    void removeItemDeletesFridgeWithoutSavingLogWhenReasonIsWrongEntry() {
        // Given
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("두부")
                .baseUnit("모")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(5)
                .basicSeasoning(false)
                .build();

        Fridge fridge = Fridge.builder()
                .memberId(7L)
                .ingredient(ingredient)
                .rawName("잘못 등록한 두부")
                .quantity(new BigDecimal("1.00"))
                .storage(StorageType.REFRIGERATED)
                .expirationDate(LocalDate.now().plusDays(5))
                .source(FridgeSource.MANUAL)
                .build();
        ReflectionTestUtils.setField(fridge, "id", 100L);

        when(fridgeRepository.findByIdAndMemberId(100L, 7L)).thenReturn(Optional.of(fridge));

        // When: WRONG_ENTRY 사유로 삭제 실행
        fridgeService.removeItem(7L, 100L, FridgeRemoveReason.WRONG_ENTRY);

        // Then: 이력 저장소는 전혀 호출되지 않고 재료 삭제만 수행되며, WRONG_ENTRY.toAction()은 BusinessException(INVALID_FRIDGE_LOG)을 던짐
        verifyNoInteractions(fridgeItemLogRepository);
        verify(fridgeRepository).delete(fridge);
        BusinessException exception = assertThrows(
                BusinessException.class,
                FridgeRemoveReason.WRONG_ENTRY::toAction
        );
        assertEquals(ErrorCode.INVALID_FRIDGE_LOG, exception.getErrorCode());
    }

    /**
     * 다른 회원의 재료(또는 존재하지 않는 fridgeId)를 빼려고 하면
     * FRIDGE_ITEM_NOT_FOUND(404) 예외가 발생하고 기록 저장과 삭제 모두 수행되지 않는지 검증한다.
     */
    @Test
    void removeItemThrowsNotFoundWhenTargetBelongsToAnotherMember() {
        // Given
        when(fridgeRepository.findByIdAndMemberId(999L, 7L)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fridgeService.removeItem(7L, 999L, FridgeRemoveReason.DISCARDED)
        );
        assertEquals(ErrorCode.FRIDGE_ITEM_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(fridgeItemLogRepository);
        verify(fridgeRepository, never()).delete(any(Fridge.class));
    }
}
