package org.example.takecareofmyfridgebe.domain.review.service;

import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.repository.MemberRepository;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewCreateRequest;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private MemberRepository memberRepository;

    @Mock
    private RecipeService recipeService;

    @Mock
    private CookingService cookingService;

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
    void getReviewsThrowsUnauthorizedWhenMineIsTrueAndUnauthenticated() {
        // Given
        ReviewSearchRequest request = new ReviewSearchRequest(10L, true, ReviewSortType.LATEST, 1, 10);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.getReviews(request, null)
        );
        assertEquals(ErrorCode.UNAUTHORIZED, exception.getErrorCode());
        verify(recipeReviewRepository, never()).findReviewsByCondition(any(), any());
    }

    /**
     * 후기 목록 조회 시 mine 플래그가 현재 로그인 사용자와 일치 여부에 따라
     * 올바르게 설정되는지 검증한다.
     */
    @Test
    void getReviewsReturnsPagedResponseWithMineFlag() {
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
                10L, new RecipeSummary(10L, "김치찌개", "image1.jpg")
        ));

        // When
        PageResponse<ReviewResponse> response = recipeReviewService.getReviews(request, currentMemberId);

        // Then
        assertEquals(2, response.content().size());
        assertEquals(101L, response.content().get(0).id());
        assertTrue(response.content().get(0).mine());
        assertEquals("사용자1", response.content().get(0).nickname());
        assertEquals("김치찌개", response.content().get(0).recipeName());

        assertEquals(102L, response.content().get(1).id());
        assertFalse(response.content().get(1).mine());
        assertEquals("사용자2", response.content().get(1).nickname());
        assertEquals("김치찌개", response.content().get(1).recipeName());
    }

    /**
     * 여러 레시피의 후기가 섞여 있어도 recipeId를 모아 findSummaries로 한 번에 조회하고,
     * 각각 알맞은 레시피 이름이 바인딩되는지 검증한다.
     */
    @Test
    @DisplayName("여러 레시피 후기가 섞여도 이름이 맞게 붙고 레시피 조회가 단 1회 수행된다")
    void getReviewsMapsRecipeNamesForMultipleMixedRecipesWithSingleBatchCall() {
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
                        10L, new RecipeSummary(10L, "김치찌개", "kimchi.jpg"),
                        20L, new RecipeSummary(20L, "된장찌개", "doenjang.jpg")
                ));

        // When
        PageResponse<ReviewResponse> response = recipeReviewService.getReviews(request, currentMemberId);

        // Then
        assertEquals(3, response.content().size());
        assertEquals("김치찌개", response.content().get(0).recipeName());
        assertEquals("된장찌개", response.content().get(1).recipeName());
        assertEquals("김치찌개", response.content().get(2).recipeName());

        // findSummaries는 N번이 아닌 정확히 1번만 호출되어야 함
        verify(recipeService, times(1)).findSummaries(any());
    }

    /**
     * 후기 목록이 비어 있으면 findSummaries를 호출하지 않고 빈 목록을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("후기 목록이 비어 있으면 findSummaries를 0회 호출하고 빈 목록을 반환한다")
    void getReviewsWhenEmptyDoesNotCallFindSummaries() {
        // Given: 조회 결과가 빈 페이지
        Long currentMemberId = 1L;
        ReviewSearchRequest request = new ReviewSearchRequest(10L, false, ReviewSortType.LATEST, 1, 10);
        Page<RecipeReview> emptyPage = Page.empty();

        when(recipeReviewRepository.findReviewsByCondition(request, currentMemberId)).thenReturn(emptyPage);

        // When
        PageResponse<ReviewResponse> response = recipeReviewService.getReviews(request, currentMemberId);

        // Then
        assertNotNull(response);
        assertTrue(response.content().isEmpty());
        // findSummaries를 절대 호출하지 않아야 함
        verify(recipeService, never()).findSummaries(any());
    }

    /**
     * 레시피 요약 조회 결과에 해당 레시피가 없으면 recipeName이 null로 매핑되는지 검증한다.
     */
    @Test
    @DisplayName("레시피가 없으면 recipeName은 null로 반환된다")
    void getReviewsWhenRecipeNotFoundInSummariesLeavesRecipeNameNull() {
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
        assertEquals(1, response.content().size());
        assertNull(response.content().get(0).recipeName());
    }

    /**
     * 후기 작성 시 레시피가 존재하지 않으면 404 RECIPE_NOT_FOUND 예외가 발생하고 요리 기록 확인을 수행하지 않는지 검증한다.
     */
    @Test
    void createReviewThrowsRecipeNotFoundWhenRecipeDoesNotExist() {
        // Given
        Long memberId = 1L;
        ReviewCreateRequest request = new ReviewCreateRequest(999L, 5, "후기 내용");
        when(recipeService.existsById(999L)).thenReturn(false);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.createReview(memberId, request)
        );
        assertEquals(ErrorCode.RECIPE_NOT_FOUND, exception.getErrorCode());
        verify(cookingService, never()).hasCooked(any(), any());
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 후기 작성 시 요리 기록이 없으면 403 REVIEW_NOT_COOKED 예외가 발생하고 중복 확인을 수행하지 않는지 검증한다.
     */
    @Test
    void createReviewThrowsForbiddenWhenNotCooked() {
        // Given
        Long memberId = 1L;
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "후기 내용");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(false);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.createReview(memberId, request)
        );
        assertEquals(ErrorCode.REVIEW_NOT_COOKED, exception.getErrorCode());
        verify(recipeReviewRepository, never()).existsByMemberIdAndRecipeId(any(), any());
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 후기 작성 성공 시 recipeName을 포함하여 응답을 반환하는지 검증한다.
     */
    @Test
    void createReviewSavesReviewAndReturnsResponse() {
        // Given: 1번 회원의 10번 레시피 후기 작성 요청
        Long memberId = 1L;
        Member member = createMember(memberId, "사용자1");
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "정말 맛있어요!");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));

        RecipeReview savedReview = createReview(100L, member, 10L, 5, "정말 맛있어요!");
        when(recipeReviewRepository.save(any(RecipeReview.class))).thenReturn(savedReview);
        when(recipeService.findSummaries(List.of(10L))).thenReturn(Map.of(
                10L, new RecipeSummary(10L, "김치찌개", "image.jpg")
        ));

        // When
        ReviewResponse response = recipeReviewService.createReview(memberId, request);

        // Then
        assertEquals(100L, response.id());
        assertEquals("김치찌개", response.recipeName());
        assertEquals(5, response.rating());
        assertEquals("정말 맛있어요!", response.content());
        assertTrue(response.mine());
        verify(recipeReviewRepository).save(any(RecipeReview.class));
        verify(recipeService, times(1)).findSummaries(List.of(10L));
    }

    /**
     * 후기 작성 시 회원이 존재하지 않으면 404 MEMBER_NOT_FOUND 예외가 발생하는지 검증한다.
     */
    @Test
    void createReviewThrowsMemberNotFoundWhenMemberDoesNotExist() {
        // Given
        Long memberId = 999L;
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "후기 내용");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.createReview(memberId, request)
        );
        assertEquals(ErrorCode.MEMBER_NOT_FOUND, exception.getErrorCode());
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 이미 동일 레시피에 후기를 작성한 회원이 중복 작성을 시도하면
     * 사전 조회에서 409 REVIEW_ALREADY_EXISTS 예외가 발생하고 저장을 호출하지 않는지 검증한다.
     */
    @Test
    void createReviewThrowsConflictWhenAlreadyExists() {
        // Given: 이미 후기가 존재하는 레시피
        Long memberId = 1L;
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "중복 후기");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(true);

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.createReview(memberId, request)
        );
        assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS, exception.getErrorCode());
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 동시 요청으로 인해 DB 유니크 제약(uk_recipe_reviews_member_id_recipe_id) 위반 시
     * 대소문자 무관하게 409 REVIEW_ALREADY_EXISTS 예외로 변환하여 던지는지 검증한다.
     */
    @Test
    void createReviewConvertsDataIntegrityViolationExceptionToConflict() {
        // Given: 사전 조회는 통과했으나 save 시점에 DB 유니크 제약 위반 발생 (대문자 포함 메시지)
        Long memberId = 1L;
        Member member = createMember(memberId, "사용자1");
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "동시 후기");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(recipeReviewRepository.save(any(RecipeReview.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'UK_RECIPE_REVIEWS_MEMBER_ID_RECIPE_ID'"));

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.createReview(memberId, request)
        );
        assertEquals(ErrorCode.REVIEW_ALREADY_EXISTS, exception.getErrorCode());
    }

    /**
     * 유니크 제약 위반이 아닌 다른 DB 무결성 위반(예: 외래키 위반 등)은
     * 409로 변환하지 않고 DataIntegrityViolationException 그대로 다시 던지는지 검증한다.
     */
    @Test
    void createReviewRethrowsDataIntegrityViolationExceptionWhenNotUniqueConstraint() {
        // Given: 유니크 제약이 아닌 다른 무결성 제약 위반 발생
        Long memberId = 1L;
        Member member = createMember(memberId, "사용자1");
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "후기 내용");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(recipeReviewRepository.save(any(RecipeReview.class)))
                .thenThrow(new DataIntegrityViolationException("Cannot add or update a child row: a foreign key constraint fails"));

        // When & Then
        assertThrows(
                DataIntegrityViolationException.class,
                () -> recipeReviewService.createReview(memberId, request)
        );
    }

    /**
     * 본인의 후기를 수정할 때 엔티티의 별점과 내용이 변경되고 recipeName을 포함하여 반환하는지 검증한다.
     */
    @Test
    void updateReviewModifiesEntityViaDirtyChecking() {
        // Given: 1번 회원의 100번 후기
        Long memberId = 1L;
        Long reviewId = 100L;
        Member member = createMember(memberId, "사용자1");
        RecipeReview review = createReview(reviewId, member, 10L, 3, "보통이에요");

        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest(5, "다시 해보니 정말 맛있네요!");
        when(recipeReviewRepository.findByIdAndMemberId(reviewId, memberId)).thenReturn(Optional.of(review));
        when(recipeService.findSummaries(List.of(10L))).thenReturn(Map.of(
                10L, new RecipeSummary(10L, "김치찌개", "image.jpg")
        ));

        // When
        ReviewResponse response = recipeReviewService.updateReview(memberId, reviewId, updateRequest);

        // Then
        assertEquals(5, response.rating());
        assertEquals("다시 해보니 정말 맛있네요!", response.content());
        assertEquals("김치찌개", response.recipeName());
        assertEquals(5, review.getRating());
        assertEquals("다시 해보니 정말 맛있네요!", review.getContent());
        verify(recipeService, times(1)).findSummaries(List.of(10L));
    }

    /**
     * 타인의 후기이거나 존재하지 않는 후기 수정 시 404 REVIEW_NOT_FOUND 예외가 발생하는지 검증한다.
     */
    @Test
    void updateReviewThrowsNotFoundWhenOwnedByAnotherMemberOrMissing() {
        // Given: 존재하지 않거나 타인의 후기 조회 결과 empty
        Long memberId = 1L;
        Long reviewId = 999L;
        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest(5, "수정 내용");

        when(recipeReviewRepository.findByIdAndMemberId(reviewId, memberId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> recipeReviewService.updateReview(memberId, reviewId, updateRequest)
        );
        assertEquals(ErrorCode.REVIEW_NOT_FOUND, exception.getErrorCode());
    }

    /**
     * 본인의 후기를 삭제할 때 정상적으로 repository.delete가 호출되는지 검증한다.
     */
    @Test
    void deleteReviewDeletesEntity() {
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
}
