package org.example.takecareofmyfridgebe.domain.review.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeReviewContentDto;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeReviewSummaryCandidate;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSearchRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSortType;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.example.takecareofmyfridgebe.global.config.jpa.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.jpa.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
class RecipeReviewRepositoryTest extends MySqlContainerSupport {

    @Autowired
    private RecipeReviewRepository recipeReviewRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("요약 후보를 조회하면 후기가 5개 이상인 레시피만 나오고 4개 이하는 빠진다")
    void returnsOnlyRecipesWithFiveOrMoreReviews() {
        // 회원 5명 생성
        Member member1 = createMember("user1@test.com", "user1");
        Member member2 = createMember("user2@test.com", "user2");
        Member member3 = createMember("user3@test.com", "user3");
        Member member4 = createMember("user4@test.com", "user4");
        Member member5 = createMember("user5@test.com", "user5");

        // 레시피 1: 후기 5개 (후보 포함 대상)
        createReview(member1, 1L, 5, "최고예요 1");
        createReview(member2, 1L, 4, "최고예요 2");
        createReview(member3, 1L, 5, "최고예요 3");
        createReview(member4, 1L, 3, "최고예요 4");
        createReview(member5, 1L, 4, "최고예요 5");

        // 레시피 2: 후기 4개 (5개 미만이므로 제외 대상)
        createReview(member1, 2L, 5, "맛있어요 1");
        createReview(member2, 2L, 4, "맛있어요 2");
        createReview(member3, 2L, 5, "맛있어요 3");
        createReview(member4, 2L, 3, "맛있어요 4");

        entityManager.flush();
        entityManager.clear();

        // when
        List<RecipeReviewSummaryCandidate> candidates = recipeReviewRepository.findSummaryCandidatesWithMinReviews();

        // then
        assertThat(candidates).hasSize(1);
        RecipeReviewSummaryCandidate candidate = candidates.get(0);
        assertThat(candidate.recipeId()).isEqualTo(1L);
        assertThat(candidate.reviewCount()).isEqualTo(5L);
        assertThat(candidate.latestReviewUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("레시피의 최근 후기를 조회하면 별점과 내용만 최근 수정순으로 나온다")
    void returnsRatingAndContentInRecentOrder() {
        Member member1 = createMember("m1@test.com", "m1");
        Member member2 = createMember("m2@test.com", "m2");

        createReview(member1, 10L, 4, "첫 번째 후기");
        createReview(member2, 10L, 5, "두 번째 후기");

        entityManager.flush();
        entityManager.clear();

        // when
        List<RecipeReviewContentDto> reviews = recipeReviewRepository.findRecentReviewsByRecipeId(
                10L,
                10
        );

        // then
        assertThat(reviews).hasSize(2);
        assertThat(reviews.get(0).rating()).isPositive();
        assertThat(reviews.get(0).content()).isNotEmpty();
    }

    @Nested
    @DisplayName("후기 목록 조회 (findReviewsByCondition)")
    class FindReviewsByCondition {

        @Test
        @DisplayName("레시피 ID를 주면 그 레시피의 후기만 나온다")
        void returnsOnlyRecipeReviews_whenRecipeIdGiven() {
            Member member1 = createMember("list1@test.com", "list1");
            Member member2 = createMember("list2@test.com", "list2");
            createReview(member1, 10L, 4, "레시피10 후기1");
            createReview(member2, 10L, 5, "레시피10 후기2");
            createReview(member1, 20L, 3, "레시피20 후기");
            entityManager.flush();
            entityManager.clear();

            Page<RecipeReview> page = recipeReviewRepository.findReviewsByCondition(
                    new ReviewSearchRequest(10L, false, ReviewSortType.LATEST, 1, 10), null);

            assertThat(page.getContent()).extracting(RecipeReview::getRecipeId).containsOnly(10L);
            assertThat(page.getTotalElements()).isEqualTo(2L);
        }

        @Test
        @DisplayName("내 후기만 보기를 켜고 회원 ID를 주면 그 회원의 후기만 나온다")
        void returnsOnlyMyReviews_whenMineAndMemberGiven() {
            Member me = createMember("mine1@test.com", "mine1");
            Member other = createMember("mine2@test.com", "mine2");
            createReview(me, 10L, 4, "내 후기1");
            createReview(other, 10L, 5, "다른 사람 후기");
            createReview(me, 20L, 3, "내 후기2");
            entityManager.flush();
            entityManager.clear();

            Page<RecipeReview> page = recipeReviewRepository.findReviewsByCondition(
                    new ReviewSearchRequest(null, true, ReviewSortType.LATEST, 1, 10), me.getId());

            assertThat(page.getContent()).extracting(review -> review.getMember().getId()).containsOnly(me.getId());
            assertThat(page.getContent()).extracting(RecipeReview::getContent).containsExactly("내 후기2", "내 후기1");
        }

        @Test
        @DisplayName("최신순이면 나중에 쓴 후기가 먼저 나온다")
        void sortsByLatest_whenSortIsLatest() {
            Member member1 = createMember("latest1@test.com", "latest1");
            Member member2 = createMember("latest2@test.com", "latest2");
            Member member3 = createMember("latest3@test.com", "latest3");
            createReview(member1, 30L, 5, "첫 번째");
            createReview(member2, 30L, 3, "두 번째");
            createReview(member3, 30L, 4, "세 번째");
            entityManager.flush();
            entityManager.clear();

            Page<RecipeReview> page = recipeReviewRepository.findReviewsByCondition(
                    new ReviewSearchRequest(30L, false, ReviewSortType.LATEST, 1, 10), null);

            assertThat(page.getContent()).extracting(RecipeReview::getContent).containsExactly("세 번째", "두 번째", "첫 번째");
        }

        @Test
        @DisplayName("별점순이면 별점이 높은 후기가 먼저 나오고, 별점이 같으면 나중에 쓴 후기가 먼저 나온다")
        void sortsByRatingThenLatest_whenSortIsRating() {
            Member member1 = createMember("rating1@test.com", "rating1");
            Member member2 = createMember("rating2@test.com", "rating2");
            Member member3 = createMember("rating3@test.com", "rating3");
            Member member4 = createMember("rating4@test.com", "rating4");
            createReview(member1, 40L, 3, "별점3");
            createReview(member2, 40L, 5, "별점5 먼저");
            createReview(member3, 40L, 5, "별점5 나중");
            createReview(member4, 40L, 4, "별점4");
            entityManager.flush();
            entityManager.clear();

            Page<RecipeReview> page = recipeReviewRepository.findReviewsByCondition(
                    new ReviewSearchRequest(40L, false, ReviewSortType.RATING, 1, 10), null);

            assertThat(page.getContent()).extracting(RecipeReview::getContent)
                    .containsExactly("별점5 나중", "별점5 먼저", "별점4", "별점3");
        }

        @Test
        @DisplayName("페이지 크기 2로 2페이지를 보면 3·4번째 후기와 전체 개수·페이지 수가 나온다")
        void returnsSecondPage_whenPageAndSizeGiven() {
            for (int i = 1; i <= 5; i++) {
                createReview(createMember("page" + i + "@test.com", "page" + i), 50L, 4, "후기" + i);
            }
            entityManager.flush();
            entityManager.clear();

            Page<RecipeReview> page = recipeReviewRepository.findReviewsByCondition(
                    new ReviewSearchRequest(50L, false, ReviewSortType.LATEST, 2, 2), null);

            assertThat(page.getContent()).extracting(RecipeReview::getContent).containsExactly("후기3", "후기2");
            assertThat(page.getNumber()).isEqualTo(1);
            assertThat(page.getTotalElements()).isEqualTo(5L);
            assertThat(page.getTotalPages()).isEqualTo(3);
        }
    }

    private Member createMember(String email, String nickname) {
        return memberRepository.save(Member.builder()
                .email(email)
                .passwordHash("encoded-pwd")
                .nickname(nickname)
                .role(Member.Role.USER)
                .build());
    }

    private RecipeReview createReview(Member member, Long recipeId, int rating, String content) {
        return recipeReviewRepository.save(RecipeReview.builder()
                .member(member)
                .recipeId(recipeId)
                .rating(rating)
                .content(content)
                .build());
    }
}
