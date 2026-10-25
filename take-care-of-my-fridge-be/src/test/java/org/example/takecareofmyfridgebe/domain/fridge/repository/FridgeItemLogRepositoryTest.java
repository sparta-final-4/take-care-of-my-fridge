package org.example.takecareofmyfridgebe.domain.fridge.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemLog;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * FridgeItemLogRepositoryImpl의 실제 쿼리(MySQL Testcontainers) 테스트.
 * 회원 조건, 처리 방식·기간 필터, 최신순 정렬, 페이지와 전체 개수를 검증한다.
 * created_at은 JPA Auditing이 저장 시각으로 채우므로 저장 후 네이티브 쿼리로 원하는 시각으로 바꾼다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class FridgeItemLogRepositoryTest extends MySqlContainerSupport {

    private static final Long MEMBER_ID = 7L;
    private static final Long OTHER_MEMBER_ID = 8L;

    @Autowired
    private FridgeItemLogRepository fridgeItemLogRepository;

    @Autowired
    private EntityManager entityManager;

    private Ingredient tofu;

    @BeforeEach
    void setUp() {
        tofu = Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("두부")
                .baseUnit("모")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(5)
                .basicSeasoning(false)
                .build();
        entityManager.persist(tofu);
    }

    /**
     * 다른 회원의 기록은 섞이지 않고, 본인 기록만 최신순(created_at desc)으로 나오며
     * 페이지 크기(2)보다 기록이 많을 때 totalElements·totalPages가 본인 기록 수(3) 기준인지 검증한다.
     */
    @Test
    void findLogsByConditionReturnsOnlyMemberLogsInLatestOrderWithTotalCount() {
        // Given: 본인 기록 3건(시각 제각각), 다른 회원 기록 1건(가장 최근)
        Long oldest = saveLog(MEMBER_ID, FridgeItemAction.CONSUMED, LocalDateTime.of(2026, 10, 1, 9, 0));
        Long latest = saveLog(MEMBER_ID, FridgeItemAction.DISCARDED, LocalDateTime.of(2026, 10, 3, 9, 0));
        Long middle = saveLog(MEMBER_ID, FridgeItemAction.CONSUMED, LocalDateTime.of(2026, 10, 2, 9, 0));
        saveLog(OTHER_MEMBER_ID, FridgeItemAction.CONSUMED, LocalDateTime.of(2026, 10, 4, 9, 0));
        flushAndClear();

        // When: 첫 페이지(크기 2)와 둘째 페이지 조회
        Page<FridgeItemLog> first = fridgeItemLogRepository.findLogsByCondition(
                MEMBER_ID, null, null, null, PageRequest.of(0, 2));
        Page<FridgeItemLog> second = fridgeItemLogRepository.findLogsByCondition(
                MEMBER_ID, null, null, null, PageRequest.of(1, 2));

        // Then
        assertEquals(List.of(latest, middle), ids(first));
        assertEquals(List.of(oldest), ids(second));
        assertEquals(3, first.getTotalElements());
        assertEquals(2, first.getTotalPages());
        assertEquals("두부", first.getContent().get(0).getIngredient().getName());
    }

    /**
     * created_at이 같은 기록은 id가 큰(나중에 저장된) 기록이 먼저 나오는지 검증한다.
     */
    @Test
    void findLogsByConditionOrdersByIdDescWhenCreatedAtIsSame() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 10, 5, 12, 0);
        Long firstSaved = saveLog(MEMBER_ID, FridgeItemAction.CONSUMED, sameTime);
        Long secondSaved = saveLog(MEMBER_ID, FridgeItemAction.CONSUMED, sameTime);
        flushAndClear();

        Page<FridgeItemLog> page = fridgeItemLogRepository.findLogsByCondition(
                MEMBER_ID, null, null, null, PageRequest.of(0, 20));

        assertEquals(List.of(secondSaved, firstSaved), ids(page));
    }

    /**
     * action=DISCARDED로 조회하면 버린 기록만 나오고 totalElements도 버린 기록 수인지 검증한다.
     */
    @Test
    void findLogsByConditionFiltersByAction() {
        saveLog(MEMBER_ID, FridgeItemAction.CONSUMED, LocalDateTime.of(2026, 10, 1, 9, 0));
        Long discarded = saveLog(MEMBER_ID, FridgeItemAction.DISCARDED, LocalDateTime.of(2026, 10, 2, 9, 0));
        flushAndClear();

        Page<FridgeItemLog> page = fridgeItemLogRepository.findLogsByCondition(
                MEMBER_ID, FridgeItemAction.DISCARDED, null, null, PageRequest.of(0, 20));

        assertEquals(List.of(discarded), ids(page));
        assertEquals(1, page.getTotalElements());
    }

    /**
     * from=10-01, to=10-10으로 조회하면
     * 시작일 00:00과 종료일 23:59:59 기록은 포함되고, 시작일 전날 23:59:59와 종료일 다음 날 00:00 기록은 빠지는지 검증한다.
     */
    @Test
    void findLogsByConditionFiltersByPeriodIncludingWholeEndDate() {
        saveLog(MEMBER_ID, FridgeItemAction.CONSUMED, LocalDateTime.of(2026, 9, 30, 23, 59, 59));
        Long startOfFrom = saveLog(MEMBER_ID, FridgeItemAction.CONSUMED, LocalDateTime.of(2026, 10, 1, 0, 0));
        Long endOfTo = saveLog(MEMBER_ID, FridgeItemAction.CONSUMED, LocalDateTime.of(2026, 10, 10, 23, 59, 59));
        saveLog(MEMBER_ID, FridgeItemAction.CONSUMED, LocalDateTime.of(2026, 10, 11, 0, 0));
        flushAndClear();

        Page<FridgeItemLog> page = fridgeItemLogRepository.findLogsByCondition(
                MEMBER_ID, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10), PageRequest.of(0, 20));

        assertEquals(List.of(endOfTo, startOfFrom), ids(page));
        assertEquals(2, page.getTotalElements());
    }

    // 기록을 저장한 뒤 created_at을 지정한 시각으로 바꾸고 id를 반환한다.
    private Long saveLog(Long memberId, FridgeItemAction action, LocalDateTime createdAt) {
        FridgeItemLog itemLog = fridgeItemLogRepository.save(FridgeItemLog.builder()
                .memberId(memberId)
                .ingredient(tofu)
                .fridgeId(100L)
                .rawName("두부")
                .quantity(BigDecimal.ONE)
                .action(action)
                .build());
        entityManager.flush();
        entityManager.createNativeQuery("update fridge_item_logs set created_at = ?1 where id = ?2")
                .setParameter(1, createdAt)
                .setParameter(2, itemLog.getId())
                .executeUpdate();
        return itemLog.getId();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * countConsumedByCooking은 요리 완료로 소진된 재료의 고유 종류(ingredient_id) 수를 센다.
     * 1) 같은 재료(두부)로 여러 번 요리하거나 여러 줄 차감되어도 1종류로 집계된다.
     * 2) 다른 재료(양파)가 포함되어 총 2종류가 되면 2로 집계된다.
     * 3) 다른 회원의 요리 차감 이력 및 cookingLogId가 없는(단순 폐기 등) 이력은 제외된다.
     */
    @Test
    void countConsumedByCookingCountsDistinctIngredients() {
        Ingredient onion = Ingredient.builder()
                .ingredientCode("ING_ONION")
                .name("양파")
                .baseUnit("개")
                .defaultStorage(StorageType.ROOM_TEMP)
                .defaultShelfDays(7)
                .basicSeasoning(false)
                .build();
        entityManager.persist(onion);

        Long cookingLog1 = 101L;
        Long cookingLog2 = 102L;

        // 1) 같은 재료(두부)로 서로 다른 요리(cookingLog1, cookingLog2)에서 여러 번 차감
        saveCookingItemLog(MEMBER_ID, tofu, cookingLog1, 1L);
        saveCookingItemLog(MEMBER_ID, tofu, cookingLog1, 2L);
        saveCookingItemLog(MEMBER_ID, tofu, cookingLog2, 3L);
        flushAndClear();

        // 같은 재료로 여러 번 요리해도 1
        assertEquals(1L, fridgeItemLogRepository.countConsumedByCooking(MEMBER_ID));

        // 2) 다른 재료(양파) 추가 차감 -> 총 2종류
        saveCookingItemLog(MEMBER_ID, onion, cookingLog1, 4L);
        flushAndClear();

        // 다른 재료 2종이면 2
        assertEquals(2L, fridgeItemLogRepository.countConsumedByCooking(MEMBER_ID));

        // 3) 다른 회원(OTHER_MEMBER_ID)의 요리 차감 이력 및 cookingLogId 없는 이력
        saveCookingItemLog(OTHER_MEMBER_ID, tofu, cookingLog2, 5L);
        saveCookingItemLog(OTHER_MEMBER_ID, onion, cookingLog2, 6L);
        saveCookingItemLog(MEMBER_ID, tofu, null, 7L);
        flushAndClear();

        // 다른 회원 및 cookingLogId 없는 이력 제외 -> 여전히 2
        assertEquals(2L, fridgeItemLogRepository.countConsumedByCooking(MEMBER_ID));
    }

    private Long saveCookingItemLog(Long memberId, Ingredient ingredient, Long cookingLogId, Long fridgeId) {
        FridgeItemLog itemLog = fridgeItemLogRepository.save(FridgeItemLog.builder()
                .memberId(memberId)
                .ingredient(ingredient)
                .cookingLogId(cookingLogId)
                .fridgeId(fridgeId)
                .rawName(ingredient.getName())
                .quantity(BigDecimal.ONE)
                .action(FridgeItemAction.CONSUMED)
                .build());
        return itemLog.getId();
    }

    private static List<Long> ids(Page<FridgeItemLog> page) {
        return page.getContent().stream().map(FridgeItemLog::getId).toList();
    }
}
