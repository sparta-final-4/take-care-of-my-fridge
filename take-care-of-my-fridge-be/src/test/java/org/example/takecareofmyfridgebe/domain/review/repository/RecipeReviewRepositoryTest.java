package org.example.takecareofmyfridgebe.domain.review.repository;

import jakarta.persistence.EntityManager;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeReviewContentDto;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeReviewSummaryCandidate;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.example.takecareofmyfridgebe.global.config.JpaAuditingConfig;
import org.example.takecareofmyfridgebe.global.config.QueryDslConfig;
import org.example.takecareofmyfridgebe.support.MySqlContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

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
    @DisplayName("findSummaryCandidatesWithMinReviews는 후기가 5개 이상인 레시피만 조회하고 4개 이하는 제외한다")
    void findSummaryCandidatesWithMinReviews_filtersCorrectly() {
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
    @DisplayName("findRecentReviewsByRecipeId는 최근 수정된 후기의 별점과 내용만 최신순으로 조회한다")
    void findRecentReviewsByRecipeId_fetchesContentsOnly() {
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
