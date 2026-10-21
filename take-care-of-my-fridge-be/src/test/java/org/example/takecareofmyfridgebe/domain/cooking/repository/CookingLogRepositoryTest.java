package org.example.takecareofmyfridgebe.domain.cooking.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeResponse;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeReviewStatus;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSortType;
import org.example.takecareofmyfridgebe.domain.cooking.dto.MyRecipeSummaryResponse;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingLog;
import org.example.takecareofmyfridgebe.domain.cooking.entity.CookingResult;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.entity.Recipe;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CookingLogRepositoryImpl의 실제 쿼리(MySQL Testcontainers) 테스트.
 * 회원 기준 격리, Recipe left join(이름·이미지), RecipeReview left join 및 내 후기 매핑,
 * AI 상담 여부(fromChat), reviewStatus 필터, sort(RECENT/MOST) 정렬,
 * 전체 묶인 레시피 수 기준 페이징 및 단일 쿼리 전체 summary 통계를 검증한다.
 * created_at/updated_at은 JPA Auditing이 채우므로 저장 후 네이티브 쿼리로 원하는 시각으로 바꾼다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class CookingLogRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

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
        testMember = Member.create("test@example.com", "hashed_password", "테스터", Set.of());
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

    private Long saveCookingLog(Long memberId, Long recipeId, Long chatSessionId, CookingResult result, String memo, LocalDateTime createdAt) {
        CookingLog log = CookingLog.builder()
                .memberId(memberId)
                .recipeId(recipeId)
                .chatSessionId(chatSessionId)
                .result(result)
                .memo(memo)
                .build();
        em.persist(log);
        em.flush();
        em.createNativeQuery("UPDATE cooking_logs SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", log.getId())
                .executeUpdate();
        return log.getId();
    }

    private void saveReview(Member member, Long recipeId, int rating, String content, LocalDateTime updatedAt) {
        Member refMember = em.getReference(Member.class, member.getId());
        RecipeReview review = RecipeReview.builder()
                .member(refMember)
                .recipeId(recipeId)
                .rating(rating)
                .content(content)
                .build();
        em.persist(review);
        em.flush();
        em.createNativeQuery("UPDATE recipe_reviews SET updated_at = :updatedAt WHERE id = :id")
                .setParameter("updatedAt", updatedAt)
                .setParameter("id", review.getId())
                .executeUpdate();
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    /**
     * 로그인한 회원의 요리 완료 레시피만 조회되며 Recipe 이름·이미지,
     * AI 추천 배지(fromChat), 내 후기가 올바르게 left join되는지 검증한다.
     */
    @Test
    void findMyRecipesReturnsOnlyMemberRecipesWithRecipeInfoAndReview() {
        // Given: recipeA(2회 요리, 1회 상담 포함, 후기 작성), recipeB(1회 요리, 상담 없음, 후기 미작성), 타인 기록(recipeC)
        saveCookingLog(testMember.getId(), recipeA.getId(), 10L, CookingResult.SUCCESS, "맛있음", LocalDateTime.of(2026, 10, 10, 12, 0));
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "또 만듦", LocalDateTime.of(2026, 10, 12, 18, 0));
        saveReview(testMember, recipeA.getId(), 5, "정말 최고예요", LocalDateTime.of(2026, 10, 12, 19, 0));

        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "간단함", LocalDateTime.of(2026, 10, 8, 12, 0));

        saveCookingLog(OTHER_MEMBER_ID, recipeC.getId(), 20L, CookingResult.SUCCESS, "타인 기록", LocalDateTime.of(2026, 10, 15, 12, 0));
        flushAndClear();

        // When: 본인 레시피 기본(RECENT) 조회
        Page<MyRecipeResponse> result = cookingLogRepository.findMyRecipes(
                testMember.getId(), MyRecipeReviewStatus.ALL, MyRecipeSortType.RECENT, PageRequest.of(0, 10));

        // Then: 본인의 2개 레시피만 반환되고 타인의 기록은 섞이지 않는지 확인
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());

        // 1번째: 최신 요리인 recipeA (10.12 요리)
        MyRecipeResponse first = result.getContent().get(0);
        assertEquals(recipeA.getId(), first.recipeId());
        assertEquals("불닭 참치 두부조림", first.recipeName());
        assertEquals("https://image.example.com/tofu.jpg", first.recipeImageUrl());
        assertEquals(2L, first.cookCount());
        assertTrue(first.fromChat());
        assertNotNull(first.review());
        assertEquals(5, first.review().rating());
        assertEquals("정말 최고예요", first.review().content());

        // 2번째: recipeB (10.08 요리)
        MyRecipeResponse second = result.getContent().get(1);
        assertEquals(recipeB.getId(), second.recipeId());
        assertEquals("계란말이", second.recipeName());
        assertEquals(1L, second.cookCount());
        assertFalse(second.fromChat());
        assertNull(second.review());
    }

    /**
     * reviewStatus 필터에 따라 후기 작성 완료(WRITTEN)와 미작성(NOT_WRITTEN) 레시피만 정확히 필터링되는지 검증한다.
     */
    @Test
    void findMyRecipesFiltersByReviewStatus() {
        // Given: recipeA(후기 작성), recipeB(후기 미작성)
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A", LocalDateTime.of(2026, 10, 1, 10, 0));
        saveReview(testMember, recipeA.getId(), 4, "후기 A", LocalDateTime.of(2026, 10, 1, 12, 0));

        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B", LocalDateTime.of(2026, 10, 2, 10, 0));
        flushAndClear();

        // When: WRITTEN 조회
        Page<MyRecipeResponse> written = cookingLogRepository.findMyRecipes(
                testMember.getId(), MyRecipeReviewStatus.WRITTEN, MyRecipeSortType.RECENT, PageRequest.of(0, 10));

        // Then: recipeA만 반환
        assertEquals(1, written.getTotalElements());
        assertEquals(recipeA.getId(), written.getContent().get(0).recipeId());

        // When: NOT_WRITTEN 조회
        Page<MyRecipeResponse> notWritten = cookingLogRepository.findMyRecipes(
                testMember.getId(), MyRecipeReviewStatus.NOT_WRITTEN, MyRecipeSortType.RECENT, PageRequest.of(0, 10));

        // Then: recipeB만 반환
        assertEquals(1, notWritten.getTotalElements());
        assertEquals(recipeB.getId(), notWritten.getContent().get(0).recipeId());
    }

    /**
     * sort=MOST 정렬 시 요리 횟수 많은 순, 횟수가 같으면 마지막 요리 최신순으로 정렬되는지 검증한다.
     */
    @Test
    void findMyRecipesSortsByMostCooked() {
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
        assertEquals(3, result.getContent().size());
        assertEquals(recipeB.getId(), result.getContent().get(0).recipeId());
        assertEquals(3L, result.getContent().get(0).cookCount());
        assertEquals(recipeC.getId(), result.getContent().get(1).recipeId());
        assertEquals(recipeA.getId(), result.getContent().get(2).recipeId());
    }

    /**
     * getMyRecipesSummary 단일 쿼리로 요리 완료 레시피 수, 총 요리 횟수, 작성 및 미작성 후기 수를 정확히 집계하는지 검증한다.
     */
    @Test
    void getMyRecipesSummaryCalculatesAccurately() {
        // Given: recipeA(2회 요리, 후기 작성), recipeB(1회 요리, 후기 미작성), 타 회원 요리 기록
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A1", LocalDateTime.of(2026, 10, 1, 10, 0));
        saveCookingLog(testMember.getId(), recipeA.getId(), null, CookingResult.SUCCESS, "A2", LocalDateTime.of(2026, 10, 2, 10, 0));
        saveReview(testMember, recipeA.getId(), 5, "굿", LocalDateTime.of(2026, 10, 2, 12, 0));

        saveCookingLog(testMember.getId(), recipeB.getId(), null, CookingResult.SUCCESS, "B1", LocalDateTime.of(2026, 10, 3, 10, 0));

        saveCookingLog(OTHER_MEMBER_ID, recipeC.getId(), null, CookingResult.SUCCESS, "Other", LocalDateTime.of(2026, 10, 4, 10, 0));
        flushAndClear();

        // When: 요약 통계 단일 쿼리 실행
        MyRecipeSummaryResponse summary = cookingLogRepository.getMyRecipesSummary(testMember.getId());

        // Then: 본인 기록 기준 recipeCount 2, totalCookCount 3, writtenCount 1, notWrittenCount 1 확인
        assertEquals(2L, summary.recipeCount());
        assertEquals(3L, summary.totalCookCount());
        assertEquals(1L, summary.writtenCount());
        assertEquals(1L, summary.notWrittenCount());
    }
}
