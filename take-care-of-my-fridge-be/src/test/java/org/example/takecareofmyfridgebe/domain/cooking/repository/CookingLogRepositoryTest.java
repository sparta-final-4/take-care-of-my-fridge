package org.example.takecareofmyfridgebe.domain.cooking.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.cooking.dto.CookingStatsResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MostCookedRecipeDto;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewStatus;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSortType;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryCount;
import org.example.takecareofmyfridgebe.domain.cooking.dto.PopularRecipeCookingCount;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemAction;
import org.example.takecareofmyfridgebe.domain.fridge.entity.FridgeItemLog;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.Ingredient;
import org.example.takecareofmyfridgebe.domain.ingredient.entity.StorageType;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.example.takecareofmyfridgebe.global.config.jpa.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.jpa.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CookingLogRepositoryImpl의 실제 쿼리(MySQL Testcontainers) 테스트.
 * 회원 기준 격리, Recipe left join(이름·이미지), RecipeReview left join 및 내 후기 매핑,
 * AI 상담 여부(fromChat), reviewStatus 필터, sort(RECENT/MOST) 정렬,
 * 전체 묶인 레시피 수 기준 페이징 및 단일 쿼리 전체 summary 통계,
 * 레시피별 성공/실패 집계 통계를 검증한다.
 * created_at/updated_at은 JPA Auditing이 채우므로 저장 후 네이티브 쿼리로 원하는 시각으로 바꾼다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class CookingLogRepositoryTest extends MySqlContainerSupport {

    private static final Long OTHER_MEMBER_ID = 999L;

    @Autowired
    private CookingLogRepository cookingLogRepository;

    @Autowired
    private EntityManager em;

    private Member testMember;
    private Recipe recipeA;
    private Recipe recipeB;
    private Recipe recipeC;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .email("test@example.com")
                .passwordHash("hashed_password")
                .nickname("테스터")
                .build();
        em.persist(testMember);

        recipeA = Recipe.builder()
                .source("MAFRA")
                .sourceId("M001")
                .name("불닭 참치 두부조림")
                .imageUrl("https://image.example.com/tofu.jpg")
                .difficulty(3)
                .build();
        em.persist(recipeA);

        recipeB = Recipe.builder()
                .source("MAFRA")
                .sourceId("M002")
                .name("계란말이")
                .imageUrl("https://image.example.com/egg.jpg")
                .difficulty(2)
                .build();
        em.persist(recipeB);

        recipeC = Recipe.builder()
                .source("MFDS")
                .sourceId("F001")
                .name("된장찌개")
                .imageUrl(null)
                .difficulty(4)
                .build();
        em.persist(recipeC);

        flushAndClear();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private CookingLog saveCookingLog(
            Long memberId, Long recipeId, Long chatSessionId, CookingResult result, String memo, LocalDateTime createdAt
    ) {
        CookingLog log = CookingLog.builder()
                .memberId(memberId)
                .recipeId(recipeId)
                .chatSessionId(chatSessionId)
                .result(result)
                .memo(memo)
                .build();
        em.persist(log);
        em.flush();
        if (createdAt != null) {
            em.createNativeQuery("UPDATE cooking_logs SET created_at = :createdAt, updated_at = :createdAt WHERE id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", log.getId())
                    .executeUpdate();
        }
        return log;
    }

    private RecipeReview saveReview(
            Member member, Long recipeId, int rating, String content, LocalDateTime createdAt
    ) {
        RecipeReview review = RecipeReview.builder()
                .member(member)
                .recipeId(recipeId)
                .rating(rating)
                .content(content)
                .build();
        em.persist(review);
        em.flush();
        if (createdAt != null) {
            em.createNativeQuery("UPDATE recipe_reviews SET created_at = :createdAt, updated_at = :createdAt WHERE id = :id")
                    .setParameter("createdAt", createdAt)
                    .setParameter("id", review.getId())
                    .executeUpdate();
        }
        return review;
    }

    /**
     * findMyRecipes 조회 시 타 회원의 요리 기록은 완전히 제외되고 본인 요리 기록만 집계되는지 검증한다.
     */
    @Test
    @DisplayName("내 레시피를 조회하면 다른 회원의 요리 기록은 빼고 본인 기록만 센다")
    void excludesOtherMembersLogs_whenFindingMyRecipes() {
        // Given
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "내 요리", LocalDateTime.of(2026, 10, 10, 10, 0));
        saveCookingLog(OTHER_MEMBER_ID, recipeA.getId(), null, CookingResult.SUCCESS, "타인 요리", LocalDateTime.of(2026, 10, 11, 10, 0));
        saveCookingLog(OTHER_MEMBER_ID, recipeB.getId(), null, CookingResult.SUCCESS, "타인 요리B", LocalDateTime.of(2026, 10, 12, 10, 0));
        flushAndClear();

        // When
        Page<MyRecipeResponse> result = cookingLogRepository.findMyRecipes(
                testMember.getId(), MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, PageRequest.of(0, 10));

        // Then: 본인 레시피만 1건 집계
        assertThat(result.getTotalElements()).isEqualTo(1);
        MyRecipeResponse item = result.getContent().get(0);
        assertThat(item.recipeId()).isEqualTo(recipeA.getId());
        assertThat(item.recipeName()).isEqualTo("불닭 참치 두부조림");
        assertThat(item.cookCount()).isEqualTo(1L);
    }

    /**
     * findMyRecipes 조회 시 recipe_reviews 테이블과 left join하여
     * 본인이 작성한 후기 정보(id, rating, content)가 정상 매핑되는지 검증한다.
     */
    @Test
    @DisplayName("내 레시피를 조회하면 본인이 쓴 후기 정보를 함께 담는다")
    void returnsOwnReview_whenFindingMyRecipes() {
        // Given: recipeA는 후기 있음, recipeB는 후기 없음
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A", LocalDateTime.of(2026, 10, 10, 10, 0));
        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B", LocalDateTime.of(2026, 10, 11, 10, 0));
        RecipeReview reviewA = saveReview(testMember, recipeA.getId(), 5, "진짜 맛있어요!", LocalDateTime.of(2026, 10, 10, 12, 0));
        flushAndClear();

        // When
        Page<MyRecipeResponse> result = cookingLogRepository.findMyRecipes(
                testMember.getId(), MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, PageRequest.of(0, 10));

        // Then: recipeB는 review가 null, recipeA는 review가 존재
        MyRecipeResponse itemB = result.getContent().stream()
                .filter(r -> r.recipeId().equals(recipeB.getId())).findFirst().orElseThrow();
        assertThat(itemB.review()).isNull();

        MyRecipeResponse itemA = result.getContent().stream()
                .filter(r -> r.recipeId().equals(recipeA.getId())).findFirst().orElseThrow();
        assertThat(itemA.review()).isNotNull();
        assertThat(itemA.review().id()).isEqualTo(reviewA.getId());
        assertThat(itemA.review().rating()).isEqualTo(5);
        assertThat(itemA.review().content()).isEqualTo("진짜 맛있어요!");
    }

    /**
     * AI 상담 여부: 여러 요리 기록 중 하나라도 chat_session_id가 있으면 fromChat이 true인지 검증한다.
     */
    @Test
    @DisplayName("요리 기록 중 하나라도 상담에서 나왔으면 fromChat을 true로 표시한다")
    void marksFromChat_whenAnyLogHasChatSession() {
        // Given: recipeA 1회차(chatSessionId=null), 2회차(chatSessionId=10L)
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "1회차", LocalDateTime.of(2026, 10, 10, 10, 0));
        saveCookingLog(testMember.getId(), recipeA.getId(), 10L, CookingResult.SUCCESS, "2회차", LocalDateTime.of(2026, 10, 12, 10, 0));
        flushAndClear();

        // When
        Page<MyRecipeResponse> result = cookingLogRepository.findMyRecipes(
                testMember.getId(), MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, PageRequest.of(0, 10));

        // Then: cookCount 2, fromChat true
        assertThat(result.getContent().size()).isEqualTo(1);
        MyRecipeResponse item = result.getContent().get(0);
        assertThat(item.cookCount()).isEqualTo(2L);
        assertThat(item.fromChat()).isTrue();
    }

    /**
     * reviewStatus 필터: WRITTEN은 후기 있는 레시피만, NOT_WRITTEN은 후기 없는 레시피만 조회되는지 검증한다.
     */
    @Test
    @DisplayName("후기 작성 여부로 내 레시피를 조회하면 그 조건에 맞는 레시피만 나온다")
    void filtersByReviewStatus() {
        // Given: recipeA(후기 작성), recipeB(후기 미작성)
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A", LocalDateTime.of(2026, 10, 10, 10, 0));
        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B", LocalDateTime.of(2026, 10, 11, 10, 0));
        saveReview(testMember, recipeA.getId(), 4, "좋아요", LocalDateTime.of(2026, 10, 10, 11, 0));
        flushAndClear();

        // When: WRITTEN 조회
        Page<MyRecipeResponse> written = cookingLogRepository.findMyRecipes(
                testMember.getId(), MyRecipeReviewStatus.WRITTEN, MyRecipeSortType.RECENT, PageRequest.of(0, 10));

        // Then: recipeA만 반환
        assertThat(written.getTotalElements()).isEqualTo(1);
        assertThat(written.getContent().get(0).recipeId()).isEqualTo(recipeA.getId());

        // When: NOT_WRITTEN 조회
        Page<MyRecipeResponse> notWritten = cookingLogRepository.findMyRecipes(
                testMember.getId(), MyRecipeReviewStatus.NOT_WRITTEN, MyRecipeSortType.RECENT, PageRequest.of(0, 10));

        // Then: recipeB만 반환
        assertThat(notWritten.getTotalElements()).isEqualTo(1);
        assertThat(notWritten.getContent().get(0).recipeId()).isEqualTo(recipeB.getId());
    }

    /**
     * sort=MOST 정렬 시 요리 횟수 많은 순, 횟수가 같으면 마지막 요리 최신순으로 정렬되는지 검증한다.
     */
    @Test
    @DisplayName("많이 만든 순으로 조회하면 요리 횟수 순, 같으면 마지막 요리가 최근인 순으로 나온다")
    void sortsByCookCountThenRecent_whenSortIsMost() {
        // Given: recipeA(1회, 10.10), recipeB(3회, 10.05), recipeC(1회, 10.12)
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A1", LocalDateTime.of(2026, 10, 10, 10, 0));

        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B1", LocalDateTime.of(2026, 10, 1, 10, 0));
        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B2", LocalDateTime.of(2026, 10, 3, 10, 0));
        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B3", LocalDateTime.of(2026, 10, 5, 10, 0));

        saveCookingLog(testMember.getId(), recipeC.getId(), null, CookingResult.SUCCESS, "C1", LocalDateTime.of(2026, 10, 12, 10, 0));
        flushAndClear();

        // When: MOST 정렬 조회
        Page<MyRecipeResponse> result = cookingLogRepository.findMyRecipes(
                testMember.getId(), MyRecipeReviewStatus.ALL, MyRecipeSortType.MOST, PageRequest.of(0, 10));

        // Then: 1위 recipeB(3회), 2위 recipeC(1회, 10.12), 3위 recipeA(1회, 10.10)
        assertThat(result.getContent().size()).isEqualTo(3);
        assertThat(result.getContent().get(0).recipeId()).isEqualTo(recipeB.getId());
        assertThat(result.getContent().get(0).cookCount()).isEqualTo(3L);
        assertThat(result.getContent().get(1).recipeId()).isEqualTo(recipeC.getId());
        assertThat(result.getContent().get(2).recipeId()).isEqualTo(recipeA.getId());
    }

    /**
     * getMyRecipesSummaryCount 단일 쿼리로 요리 완료 레시피 수, 총 요리 횟수, 작성 및 미작성 후기 수를 정확히 집계하는지 검증한다.
     */
    @Test
    @DisplayName("내 레시피 요약은 레시피 수, 요리 횟수, 후기 작성·미작성 수를 정확히 센다")
    void calculatesMyRecipesSummary() {
        // Given: recipeA(2회 요리, 후기 작성), recipeB(1회 요리, 후기 미작성), 타 회원 요리 기록
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A1", LocalDateTime.of(2026, 10, 1, 10, 0));
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A2", LocalDateTime.of(2026, 10, 2, 10, 0));
        saveReview(testMember, recipeA.getId(), 5, "굿", LocalDateTime.of(2026, 10, 2, 12, 0));

        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B1", LocalDateTime.of(2026, 10, 3, 10, 0));

        saveCookingLog(OTHER_MEMBER_ID, recipeC.getId(), null, CookingResult.SUCCESS, "Other", LocalDateTime.of(2026, 10, 4, 10, 0));
        flushAndClear();

        // When: 요약 통계 단일 쿼리 실행
        MyRecipeSummaryCount summary = cookingLogRepository.getMyRecipesSummaryCount(testMember.getId());

        // Then: 본인 기록 기준 recipeCount 2, totalCookCount 3, writtenCount 1, notWrittenCount 1 확인
        assertThat(summary.recipeCount()).isEqualTo(2L);
        assertThat(summary.totalCookCount()).isEqualTo(3L);
        assertThat(summary.writtenCount()).isEqualTo(1L);
        assertThat(summary.notWrittenCount()).isEqualTo(1L);
    }

    /**
     * 요리 기록이 없는 경우 findMostCookedRecipe는 Optional.empty()를 반환한다.
     */
    @Test
    @DisplayName("요리 기록이 없으면 가장 많이 만든 레시피는 비어 있다")
    void returnsEmpty_whenNoCookingLogs() {
        Optional<MostCookedRecipeDto> result = cookingLogRepository.findMostCookedRecipe(testMember.getId());
        assertThat(result.isEmpty()).isTrue();
    }

    /**
     * 가장 많이 요리한 레시피를 조회하고, 요리 횟수가 같을 때는 가장 최근에 요리한 레시피가 반환된다.
     */
    @Test
    @DisplayName("가장 많이 만든 레시피를 조회하면 횟수가 같을 때 최근에 만든 레시피가 나온다")
    void returnsMostRecent_whenCookCountIsTied() {
        // Given: recipeA 1회(10.01), recipeB 2회(10.05), recipeC 2회(10.10)
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A1", LocalDateTime.of(2026, 10, 1, 10, 0));

        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B1", LocalDateTime.of(2026, 10, 3, 10, 0));
        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B2", LocalDateTime.of(2026, 10, 5, 10, 0));

        saveCookingLog(testMember.getId(), recipeC.getId(), null, CookingResult.SUCCESS, "C1", LocalDateTime.of(2026, 10, 8, 10, 0));
        saveCookingLog(testMember.getId(), recipeC.getId(), null, CookingResult.SUCCESS, "C2", LocalDateTime.of(2026, 10, 10, 10, 0));
        flushAndClear();

        // When
        Optional<MostCookedRecipeDto> result = cookingLogRepository.findMostCookedRecipe(testMember.getId());

        // Then: 2회 중 최근(10.10)인 recipeC가 1위
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().recipeId()).isEqualTo(recipeC.getId());
        assertThat(result.get().cookCount()).isEqualTo(2L);
    }

    /**
     * findMostCookedRecipe 조회 시 다른 회원의 요리 기록은 완전히 제외된다.
     */
    @Test
    @DisplayName("가장 많이 만든 레시피를 조회하면 다른 회원의 요리 기록은 뺀다")
    void excludesOtherMembersLogs_whenFindingMostCooked() {
        // Given: testMember는 recipeA 1회, OTHER_MEMBER는 recipeB 10회
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A1", LocalDateTime.of(2026, 10, 1, 10, 0));
        saveCookingLog(OTHER_MEMBER_ID, recipeB.getId(), null, CookingResult.SUCCESS, "B_other", LocalDateTime.of(2026, 10, 2, 10, 0));
        flushAndClear();

        // When
        Optional<MostCookedRecipeDto> result = cookingLogRepository.findMostCookedRecipe(testMember.getId());

        // Then: testMember의 recipeA만 집계되어야 함
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().recipeId()).isEqualTo(recipeA.getId());
        assertThat(result.get().cookCount()).isEqualTo(1L);
    }

    /**
     * findByIdAndMemberId로 본인의 요리 기록만 정상 조회되고, 다른 회원의 기록이거나 없으면 Optional.empty()가 반환되는지 검증한다.
     */
    @Test
    @DisplayName("요리 기록을 ID와 회원으로 조회하면 본인 기록만 나오고 다른 회원 기록이나 없는 기록은 비어 있다")
    void returnsOnlyOwnLog() {
        CookingLog myLog = saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "내 기록", LocalDateTime.now());
        CookingLog otherLog = saveCookingLog(OTHER_MEMBER_ID, recipeA.getId(), null, CookingResult.SUCCESS, "타인 기록", LocalDateTime.now());
        flushAndClear();

        // When & Then
        assertThat(cookingLogRepository.findByIdAndMemberId(myLog.getId(), testMember.getId()).isPresent()).isTrue();
        assertThat(cookingLogRepository.findByIdAndMemberId(otherLog.getId(), testMember.getId()).isEmpty()).isTrue();
        assertThat(cookingLogRepository.findByIdAndMemberId(99999L, testMember.getId()).isEmpty()).isTrue();
    }

    /**
     * 요리 기록을 수정해도 데이터베이스의 다른 필드(recipeId, chatSessionId 등)와
     * 연결된 fridge_item_logs(재료 차감 이력)가 변경되지 않는지 검증한다.
     */
    @Test
    @DisplayName("요리 기록을 수정해도 다른 필드와 재료 차감 기록은 바뀌지 않는다")
    void keepsOtherFieldsAndFridgeLogs_whenCookingLogIsUpdated() {
        // Given: 요리 기록 생성
        CookingLog log = saveCookingLog(testMember.getId(), recipeA.getId(), 10L, CookingResult.SUCCESS, "초기 메모", LocalDateTime.now());

        // 재료 및 차감 이력(fridge_item_logs) 저장
        Ingredient ingredient = Ingredient.builder()
                .ingredientCode("ING_ONION")
                .name("양파")
                .baseUnit("g")
                .defaultStorage(StorageType.ROOM_TEMP)
                .defaultShelfDays(7)
                .basicSeasoning(false)
                .build();
        em.persist(ingredient);

        FridgeItemLog itemLog = FridgeItemLog.builder()
                .memberId(testMember.getId())
                .ingredient(ingredient)
                .cookingLogId(log.getId())
                .fridgeId(50L)
                .rawName("깐양파")
                .quantity(new BigDecimal("100.00"))
                .action(FridgeItemAction.CONSUMED)
                .build();
        em.persist(itemLog);
        flushAndClear();

        // When: 요리 기록 수정 (결과, 메모 변경)
        CookingLog foundLog = cookingLogRepository.findByIdAndMemberId(log.getId(), testMember.getId()).orElseThrow();
        foundLog.update(CookingResult.FAILED, "수정된 메모");
        flushAndClear();

        // Then: 요리 기록의 결과와 메모만 변경되고 다른 필드는 보존됨
        CookingLog updatedCookingLog = cookingLogRepository.findById(log.getId()).orElseThrow();
        assertThat(updatedCookingLog.getResult()).isEqualTo(CookingResult.FAILED);
        assertThat(updatedCookingLog.getMemo()).isEqualTo("수정된 메모");
        assertThat(updatedCookingLog.getRecipeId()).isEqualTo(recipeA.getId());
        assertThat(updatedCookingLog.getChatSessionId()).isEqualTo(10L);

        // 연결된 fridge_item_logs는 아무런 변경 없이 유지됨
        FridgeItemLog foundFridgeItemLog = em.find(FridgeItemLog.class, itemLog.getId());
        assertThat(foundFridgeItemLog).isNotNull();
        assertThat(foundFridgeItemLog.getCookingLogId()).isEqualTo(log.getId());
        assertThat(foundFridgeItemLog.getQuantity()).isEqualTo(new BigDecimal("100.00"));
        assertThat(foundFridgeItemLog.getAction()).isEqualTo(FridgeItemAction.CONSUMED);
        assertThat(foundFridgeItemLog.getRawName()).isEqualTo("깐양파");
    }

    /**
     * getCookingStats 단일 쿼리로 특정 레시피의 총 요리 횟수, 성공 횟수, 실패 횟수가 정확히 집계되는지 검증한다.
     * 다른 레시피의 요리 기록은 집계에 포함되지 않아야 한다.
     */
    @Test
    @DisplayName("레시피 요리 통계는 그 레시피의 요리·성공·실패 횟수만 정확히 센다")
    void calculatesCookingStatsForRecipe() {
        // Given: recipeA 성공 2회, 실패 1회 (총 3회) / recipeB 성공 1회
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A1", LocalDateTime.of(2026, 10, 1, 10, 0));
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A2", LocalDateTime.of(2026, 10, 2, 10, 0));
        saveCookingLog(OTHER_MEMBER_ID, recipeA.getId(), null, CookingResult.FAILED, "A3", LocalDateTime.of(2026, 10, 3, 10, 0));

        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B1", LocalDateTime.of(2026, 10, 4, 10, 0));
        flushAndClear();

        // When
        CookingStatsResponse statsA = cookingLogRepository.getCookingStats(recipeA.getId());

        // Then: recipeA 기준 총 3회, 성공 2회, 실패 1회
        assertThat(statsA.recipeId()).isEqualTo(recipeA.getId());
        assertThat(statsA.totalCount()).isEqualTo(3L);
        assertThat(statsA.successCount()).isEqualTo(2L);
        assertThat(statsA.failedCount()).isEqualTo(1L);

        // When: 요리 기록이 없는 recipeC 조회 시 404가 아닌 0으로 반환
        CookingStatsResponse statsC = cookingLogRepository.getCookingStats(recipeC.getId());
        assertThat(statsC.recipeId()).isEqualTo(recipeC.getId());
        assertThat(statsC.totalCount()).isEqualTo(0L);
        assertThat(statsC.successCount()).isEqualTo(0L);
        assertThat(statsC.failedCount()).isEqualTo(0L);
    }

    /**
     * findPopularCookingCounts:
     * - 30일 이전 기록은 집계에서 제외된다.
     * - 같은 회원이 여러 번 만들어도 회원 수(cookedMemberCount)는 1명으로 집계된다.
     * - 정렬: 만든 회원 수 많은 순 -> 요리 횟수 많은 순 -> 마지막 요리 최근 순 -> recipe_id 순
     * - size 파라미터만큼만 조회된다.
     */
    @Test
    @DisplayName("인기 레시피는 최근 30일 기록만 만든 회원 수 순으로 세어 size만큼 반환한다")
    void returnsPopularRecipesByMemberCount() {
        // Given
        LocalDateTime now = LocalDateTime.of(2026, 10, 26, 12, 0);
        LocalDateTime since = now.minusDays(30);

        Long member1 = testMember.getId();
        Long member2 = OTHER_MEMBER_ID;
        Long member3 = 888L;

        // recipeA: member1(2회), member2(1회) -> 고유 회원 2명, 요리 3회
        saveCookingLog(member1, recipeA.getId(), null, CookingResult.SUCCESS, "A1", now.minusDays(10));
        saveCookingLog(member1, recipeA.getId(), null, CookingResult.SUCCESS, "A2", now.minusDays(5));
        saveCookingLog(member2, recipeA.getId(), null, CookingResult.SUCCESS, "A3", now.minusDays(2));

        // recipeB: member1(1회), member2(1회) -> 고유 회원 2명, 요리 2회
        saveCookingLog(member1, recipeB.getId(), null, CookingResult.SUCCESS, "B1", now.minusDays(8));
        saveCookingLog(member2, recipeB.getId(), null, CookingResult.SUCCESS, "B2", now.minusDays(1));

        // recipeC: member3(1회) -> 고유 회원 1명, 요리 1회
        saveCookingLog(member3, recipeC.getId(), null, CookingResult.SUCCESS, "C1", now.minusDays(3));

        // 30일 이전 기록 (31일 전): 집계에 포함되지 않아야 함
        saveCookingLog(member1, recipeC.getId(), null, CookingResult.SUCCESS, "C_OLD", now.minusDays(31));

        flushAndClear();

        // When: size 2로 조회
        List<PopularRecipeCookingCount> top2 = cookingLogRepository.findPopularCookingCounts(since, 2);

        // Then: size 제한 및 순위 검증 (recipeA 1위, recipeB 2위)
        assertThat(top2.size()).isEqualTo(2);
        assertThat(top2.get(0).recipeId()).isEqualTo(recipeA.getId());
        assertThat(top2.get(0).cookedMemberCount()).isEqualTo(2L); // member1 중복 제거되어 2명
        assertThat(top2.get(1).recipeId()).isEqualTo(recipeB.getId());
        assertThat(top2.get(1).cookedMemberCount()).isEqualTo(2L);

        // When: 전체 조회 (size 10)
        List<PopularRecipeCookingCount> all = cookingLogRepository.findPopularCookingCounts(since, 10);

        // Then: 총 3개 레시피가 순서대로 정렬됨 (recipeA -> recipeB -> recipeC)
        assertThat(all.size()).isEqualTo(3);
        assertThat(all.get(0).recipeId()).isEqualTo(recipeA.getId());
        assertThat(all.get(1).recipeId()).isEqualTo(recipeB.getId());
        assertThat(all.get(2).recipeId()).isEqualTo(recipeC.getId());
        assertThat(all.get(2).cookedMemberCount()).isEqualTo(1L); // 31일 전 기록 제외되어 1명

        // When: 요리 기록이 없는 기간(미래)으로 조회 시 빈 목록 반환
        List<PopularRecipeCookingCount> empty = cookingLogRepository.findPopularCookingCounts(now.plusDays(1), 10);
        assertThat(empty.isEmpty()).isTrue();
    }
}
