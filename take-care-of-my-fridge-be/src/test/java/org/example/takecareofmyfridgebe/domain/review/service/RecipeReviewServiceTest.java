package org.example.takecareofmyfridgebe.domain.review.service;

import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.RecipeRatingStat;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewResponse;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSearchRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewSortType;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewUpdateRequest;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.example.takecareofmyfridgebe.domain.review.repository.RecipeReviewRepository;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.example.takecareofmyfridgebe.global.response.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecipeReviewServiceTest {

    @Mock
    private RecipeReviewRepository recipeReviewRepository;

    @Mock
    private RecipeService recipeService;

    @InjectMocks
    private RecipeReviewService recipeReviewService;

    private Member createMember(Long id, String nickname) {
        Member member = Member.builder().email("user" + id + "@example.com").passwordHash("encoded_password").nickname(nickname).tastes(Collections.emptySet()).build();
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private RecipeReview createReview(Long id, Member member, Long recipeId, int rating, String content) {
        RecipeReview review = RecipeReview.builder()
                .member(member)
                .recipeId(recipeId)
                .rating(rating)
                .content(content)
                .build();
        ReflectionTestUtils.setField(review, "id", id);
        return review;
    }

    /**
     * mine=true일 때 비로그인 상태(currentMemberId == null)이면
     * 401 UNAUTHORIZED 예외가 발생하는지 검증한다.
     */
    @Test
    @DisplayName("로그인하지 않고 내 후기만 조회하면 UNAUTHORIZED 예외를 던진다")
    void throwsUnauthorized_whenMineIsRequestedWithoutLogin() {
        // Given
        ReviewSearchRequest request = new ReviewSearchRequest(10L, true, ReviewSortType.LATEST, 1, 10);

        // When & Then
        assertThatThrownBy(() -> recipeReviewService.getReviews(request, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(recipeReviewRepository, never()).findReviewsByCondition(any(), any());
    }

    /**
     * 후기 목록 조회 시 mine 플래그가 현재 로그인 사용자와 일치 여부에 따라
     * 올바르게 설정되는지 검증한다.
     */
    @Test
    @DisplayName("후기 목록을 조회하면 현재 회원이 쓴 후기에만 mine을 표시한다")
    void marksMine_whenReviewIsWrittenByCurrentMember() {
        // Given
        Long currentMemberId = 1L;
        Member member1 = createMember(1L, "사용자1");
        Member member2 = createMember(2L, "사용자2");

        RecipeReview review1 = createReview(101L, member1, 10L, 5, "최고예요!");
        RecipeReview review2 = createReview(102L, member2, 10L, 4, "좋아요");

        ReviewSearchRequest request = new ReviewSearchRequest(10L, false, ReviewSortType.LATEST, 1, 20);
        Page<RecipeReview> reviewPage = new PageImpl<>(List.of(review1, review2));

        when(recipeReviewRepository.findReviewsByCondition(request, currentMemberId)).thenReturn(reviewPage);
        when(recipeService.findSummaries(List.of(10L))).thenReturn(Map.of(
                10L, new RecipeSummary(10L, "김치찌개", "image1.jpg", null)
        ));

        // When
        PageResponse<ReviewResponse> response = recipeReviewService.getReviews(request, currentMemberId);

        // Then
        assertThat(response.content().size()).isEqualTo(2);
        assertThat(response.content().get(0).id()).isEqualTo(101L);
        assertThat(response.content().get(0).mine()).isTrue();
        assertThat(response.content().get(0).nickname()).isEqualTo("사용자1");
        assertThat(response.content().get(0).recipeName()).isEqualTo("김치찌개");

        assertThat(response.content().get(1).id()).isEqualTo(102L);
        assertThat(response.content().get(1).mine()).isFalse();
        assertThat(response.content().get(1).nickname()).isEqualTo("사용자2");
        assertThat(response.content().get(1).recipeName()).isEqualTo("김치찌개");
    }

    /**
     * 여러 레시피의 후기가 섞여 있어도 recipeId를 모아 findSummaries로 한 번에 조회하고,
     * 각각 알맞은 레시피 이름이 바인딩되는지 검증한다.
     */
    @Test
    @DisplayName("여러 레시피 후기가 섞여도 이름이 맞게 붙고 레시피 조회가 단 1회 수행된다")
    void mapsRecipeNamesWithSingleLookup_whenReviewsSpanRecipes() {
        // Given: 레시피 10L과 20L이 섞여 있는 후기 3개
        Long currentMemberId = 1L;
        Member member = createMember(1L, "사용자1");

        RecipeReview review1 = createReview(101L, member, 10L, 5, "김치찌개 후기 1");
        RecipeReview review2 = createReview(102L, member, 20L, 4, "된장찌개 후기");
        RecipeReview review3 = createReview(103L, member, 10L, 5, "김치찌개 후기 2");

        ReviewSearchRequest request = new ReviewSearchRequest(null, false, ReviewSortType.LATEST, 1, 10);
        Page<RecipeReview> reviewPage = new PageImpl<>(List.of(review1, review2, review3));

        when(recipeReviewRepository.findReviewsByCondition(request, currentMemberId)).thenReturn(reviewPage);
        when(recipeService.findSummaries(argThat(ids -> ids.size() == 2 && ids.containsAll(List.of(10L, 20L)))))
                .thenReturn(Map.of(
                        10L, new RecipeSummary(10L, "김치찌개", "kimchi.jpg", null),
                        20L, new RecipeSummary(20L, "된장찌개", "doenjang.jpg", null)
                ));

        // When
        PageResponse<ReviewResponse> response = recipeReviewService.getReviews(request, currentMemberId);

        // Then
        assertThat(response.content().size()).isEqualTo(3);
        assertThat(response.content().get(0).recipeName()).isEqualTo("김치찌개");
        assertThat(response.content().get(1).recipeName()).isEqualTo("된장찌개");
        assertThat(response.content().get(2).recipeName()).isEqualTo("김치찌개");

        // findSummaries는 N번이 아닌 정확히 1번만 호출되어야 함
        verify(recipeService, times(1)).findSummaries(any());
    }

    /**
     * 후기 목록이 비어 있으면 findSummaries를 호출하지 않고 빈 목록을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("후기 목록이 비어 있으면 findSummaries를 0회 호출하고 빈 목록을 반환한다")
    void skipsRecipeLookup_whenReviewsAreEmpty() {
        // Given: 조회 결과가 빈 페이지
        Long currentMemberId = 1L;
        ReviewSearchRequest request = new ReviewSearchRequest(10L, false, ReviewSortType.LATEST, 1, 10);
        Page<RecipeReview> emptyPage = Page.empty();

        when(recipeReviewRepository.findReviewsByCondition(request, currentMemberId)).thenReturn(emptyPage);

        // When
        PageResponse<ReviewResponse> response = recipeReviewService.getReviews(request, currentMemberId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.content().isEmpty()).isTrue();
        // findSummaries를 절대 호출하지 않아야 함
        verify(recipeService, never()).findSummaries(any());
    }

    /**
     * 레시피 요약 조회 결과에 해당 레시피가 없으면 recipeName이 null로 매핑되는지 검증한다.
     */
    @Test
    @DisplayName("레시피가 없으면 recipeName은 null로 반환된다")
    void returnsNullRecipeName_whenRecipeIsMissing() {
        // Given: 999L 레시피 후기지만 레시피 서비스 요약 결과에는 없음
        Long currentMemberId = 1L;
        Member member = createMember(1L, "사용자1");
        RecipeReview review = createReview(101L, member, 999L, 5, "알 수 없는 레시피 후기");

        ReviewSearchRequest request = new ReviewSearchRequest(999L, false, ReviewSortType.LATEST, 1, 10);
        Page<RecipeReview> reviewPage = new PageImpl<>(List.of(review));

        when(recipeReviewRepository.findReviewsByCondition(request, currentMemberId)).thenReturn(reviewPage);
        when(recipeService.findSummaries(List.of(999L))).thenReturn(Collections.emptyMap());

        // When
        PageResponse<ReviewResponse> response = recipeReviewService.getReviews(request, currentMemberId);

        // Then
        assertThat(response.content().size()).isEqualTo(1);
        assertThat(response.content().get(0).recipeName()).isNull();
    }

    /**
     * 본인의 후기를 수정할 때 엔티티의 별점과 내용이 변경되고 recipeName을 포함하여 반환하는지 검증한다.
     */
    @Test
    @DisplayName("본인 후기를 수정하면 별점과 내용이 바뀌고 레시피 이름을 담아 반환한다")
    void updatesRatingAndContent() {
        // Given: 1번 회원의 100번 후기
        Long memberId = 1L;
        Long reviewId = 100L;
        Member member = createMember(memberId, "사용자1");
        RecipeReview review = createReview(reviewId, member, 10L, 3, "보통이에요");

        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest(5, "다시 해보니 정말 맛있네요!");
        when(recipeReviewRepository.findByIdAndMemberId(reviewId, memberId)).thenReturn(Optional.of(review));
        when(recipeService.findSummaries(List.of(10L))).thenReturn(Map.of(
                10L, new RecipeSummary(10L, "김치찌개", "image.jpg", null)
        ));

        // When
        ReviewResponse response = recipeReviewService.updateReview(memberId, reviewId, updateRequest);

        // Then
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.content()).isEqualTo("다시 해보니 정말 맛있네요!");
        assertThat(response.recipeName()).isEqualTo("김치찌개");
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getContent()).isEqualTo("다시 해보니 정말 맛있네요!");
        verify(recipeService, times(1)).findSummaries(List.of(10L));
    }

    /**
     * 타인의 후기이거나 존재하지 않는 후기 수정 시 404 REVIEW_NOT_FOUND 예외가 발생하는지 검증한다.
     */
    @Test
    @DisplayName("다른 회원의 후기이거나 없는 후기를 수정하면 REVIEW_NOT_FOUND 예외를 던진다")
    void throwsReviewNotFound_whenReviewIsOthersOrMissing() {
        // Given: 존재하지 않거나 타인의 후기 조회 결과 empty
        Long memberId = 1L;
        Long reviewId = 999L;
        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest(5, "수정 내용");

        when(recipeReviewRepository.findByIdAndMemberId(reviewId, memberId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> recipeReviewService.updateReview(memberId, reviewId, updateRequest))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_NOT_FOUND);
    }

    /**
     * 본인의 후기를 삭제할 때 정상적으로 repository.delete가 호출되는지 검증한다.
     */
    @Test
    @DisplayName("본인 후기를 삭제하면 저장소에서 삭제한다")
    void deletesReview() {
        // Given: 1번 회원의 100번 후기
        Long memberId = 1L;
        Long reviewId = 100L;
        Member member = createMember(memberId, "사용자1");
        RecipeReview review = createReview(reviewId, member, 10L, 4, "삭제할 후기");

        when(recipeReviewRepository.findByIdAndMemberId(reviewId, memberId)).thenReturn(Optional.of(review));

        // When
        recipeReviewService.deleteReview(memberId, reviewId);

        // Then
        verify(recipeReviewRepository).delete(review);
    }

    @Test
    @DisplayName("recipeIds가 비어있거나 null이면 저장소 호출 없이 빈 Map을 반환한다")
    void returnsEmptyMap_whenRecipeIdsAreEmptyOrNull() {
        // When & Then
        assertThat(recipeReviewService.findRatingStats(List.of()).isEmpty()).isTrue();
        assertThat(recipeReviewService.findRatingStats(null).isEmpty()).isTrue();
        verify(recipeReviewRepository, never()).findRatingStatsByRecipeIds(any());
    }

    @Test
    @DisplayName("레시피 ID 목록으로 별점 통계를 조회하면 ID를 키로 하는 Map을 반환하고 평점은 소수 첫째자리로 반올림된다")
    void returnsRoundedRatingsByRecipeId() {
        // Given
        RecipeRatingStat stat1 = new RecipeRatingStat(10L, 4.666, 3L);
        RecipeRatingStat stat2 = new RecipeRatingStat(20L, 3.0, 1L);
        when(recipeReviewRepository.findRatingStatsByRecipeIds(argThat(ids -> ids.containsAll(List.of(10L, 20L)))))
                .thenReturn(List.of(stat1, stat2));

        // When
        Map<Long, RecipeRatingStat> result = recipeReviewService.findRatingStats(List.of(10L, 20L, 10L));

        // Then
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(10L).averageRating()).isEqualTo(4.7);
        assertThat(result.get(10L).reviewCount()).isEqualTo(3L);
        assertThat(result.get(20L).averageRating()).isEqualTo(3.0);
        assertThat(result.get(20L).reviewCount()).isEqualTo(1L);
    }
}
