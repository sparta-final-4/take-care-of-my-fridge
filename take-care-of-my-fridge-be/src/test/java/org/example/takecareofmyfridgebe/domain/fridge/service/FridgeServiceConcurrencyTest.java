package org.example.takecareofmyfridgebe.domain.fridge.service;

import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeItemTakeOutRequest;
import org.example.takecareofmyfridgebe.domain.fridge.dto.FridgeRemoveReason;
import org.example.takecareofmyfridgebe.domain.fridge.entity.Fridge;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeSource;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeItemLogRepository;
import org.example.takecareofmyfridgebe.domain.fridge.repository.FridgeRepository;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.ingredient.repository.IngredientRepository;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 냉장고 재료에 사용·삭제 요청이 동시에 들어올 때 행 잠금으로 순서대로 처리되는지 실제 MySQL로 검증한다.
 * 테스트 자체 트랜잭션을 끄고(NOT_SUPPORTED) 각 스레드가 서비스의 트랜잭션 경계로 따로 커밋하게 한다.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class, FridgeService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FridgeServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    private static final Long MEMBER_ID = 7L;

    private final FridgeService fridgeService;
    private final FridgeRepository fridgeRepository;
    private final FridgeItemLogRepository fridgeItemLogRepository;
    private final IngredientRepository ingredientRepository;

    private Ingredient tofu;

    @Autowired
    FridgeServiceConcurrencyTest(
            FridgeService fridgeService,
            FridgeRepository fridgeRepository,
            FridgeItemLogRepository fridgeItemLogRepository,
            IngredientRepository ingredientRepository) {
        this.fridgeService = fridgeService;
        this.fridgeRepository = fridgeRepository;
        this.fridgeItemLogRepository = fridgeItemLogRepository;
        this.ingredientRepository = ingredientRepository;
    }

    @BeforeEach
    void setUp() {
        tofu = ingredientRepository.save(Ingredient.builder()
                .ingredientCode("ING_TOFU")
                .name("두부")
                .baseUnit("모")
                .defaultStorage(StorageType.REFRIGERATED)
                .defaultShelfDays(5)
                .basicSeasoning(false)
                .build());
    }

    @AfterEach
    void cleanUp() {
        fridgeItemLogRepository.deleteAll();
        fridgeRepository.deleteAll();
        ingredientRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 재료를 동시에 일부씩 먹으면 두 차감이 모두 반영된다")
    void takeOutItem_concurrentPartialDeductionsAreBothApplied() throws Exception {
        Long fridgeId = saveFridge("3.00");
        FridgeItemTakeOutRequest takeOne = new FridgeItemTakeOutRequest(FridgeItemAction.CONSUMED, BigDecimal.ONE);

        List<Throwable> failures = runConcurrently(
                () -> fridgeService.takeOutItem(MEMBER_ID, fridgeId, takeOne),
                () -> fridgeService.takeOutItem(MEMBER_ID, fridgeId, takeOne)
        );

        assertThat(failures).isEmpty();
        assertThat(fridgeRepository.findById(fridgeId).orElseThrow().getQuantity())
                .isEqualByComparingTo("1.00");
        assertThat(fridgeItemLogRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 재료를 동시에 전부 먹으면 하나만 처리되고 나머지는 FRIDGE_ITEM_NOT_FOUND다")
    void takeOutItem_secondRequestGetsNotFoundAfterFirstRemoves() throws Exception {
        Long fridgeId = saveFridge("1.00");
        FridgeItemTakeOutRequest takeAll = new FridgeItemTakeOutRequest(FridgeItemAction.CONSUMED, null);

        List<Throwable> failures = runConcurrently(
                () -> fridgeService.takeOutItem(MEMBER_ID, fridgeId, takeAll),
                () -> fridgeService.takeOutItem(MEMBER_ID, fridgeId, takeAll)
        );

        assertNotFoundOnly(failures);
        assertThat(fridgeRepository.findById(fridgeId)).isEmpty();
        assertThat(fridgeItemLogRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 재료에 사용과 빼기가 동시에 오면 하나만 처리되고 나머지는 FRIDGE_ITEM_NOT_FOUND다")
    void takeOutItemAndRemoveItem_secondRequestGetsNotFound() throws Exception {
        Long fridgeId = saveFridge("1.00");
        FridgeItemTakeOutRequest takeAll = new FridgeItemTakeOutRequest(FridgeItemAction.CONSUMED, null);

        List<Throwable> failures = runConcurrently(
                () -> fridgeService.takeOutItem(MEMBER_ID, fridgeId, takeAll),
                () -> {
                    fridgeService.removeItem(MEMBER_ID, fridgeId, FridgeRemoveReason.DISCARDED);
                    return null;
                }
        );

        assertNotFoundOnly(failures);
        assertThat(fridgeRepository.findById(fridgeId)).isEmpty();
        assertThat(fridgeItemLogRepository.count()).isEqualTo(1);
    }

    private Long saveFridge(String quantity) {
        return fridgeRepository.save(Fridge.builder()
                .memberId(MEMBER_ID)
                .ingredient(tofu)
                .rawName("국산 두부 300g")
                .quantity(new BigDecimal(quantity))
                .storage(StorageType.REFRIGERATED)
                .expirationDate(LocalDate.now().plusDays(3))
                .source(FridgeSource.MANUAL)
                .build()).getId();
    }

    // 두 작업을 동시에 출발시키고, 실패한 작업의 예외만 모아 돌려준다.
    private List<Throwable> runConcurrently(Callable<?> first, Callable<?> second) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (Callable<?> task : List.of(first, second)) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return task.call();
            }));
        }
        ready.await();
        start.countDown();

        List<Throwable> failures = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                failures.add(e.getCause());
            } catch (Exception e) {
                failures.add(e);
            }
        }
        executor.shutdownNow();
        return failures;
    }

    private void assertNotFoundOnly(List<Throwable> failures) {
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FRIDGE_ITEM_NOT_FOUND);
    }
}
