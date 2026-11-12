package org.example.takecareofmyfridgebe.domain.review.facade;

import org.example.takecareofmyfridgebe.domain.cooking.service.CookingService;
import org.example.takecareofmyfridgebe.domain.member.entity.Member;
import org.example.takecareofmyfridgebe.domain.member.service.MemberService;
import org.example.takecareofmyfridgebe.domain.recipe.dto.RecipeSummary;
import org.example.takecareofmyfridgebe.domain.recipe.service.RecipeService;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewCreateRequest;
import org.example.takecareofmyfridgebe.domain.review.dto.ReviewResponse;
import org.example.takecareofmyfridgebe.domain.review.entity.RecipeReview;
import org.example.takecareofmyfridgebe.domain.review.repository.RecipeReviewRepository;
import org.example.takecareofmyfridgebe.domain.review.service.RecipeReviewService;
import org.example.takecareofmyfridgebe.global.error.BusinessException;
import org.example.takecareofmyfridgebe.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 후기 작성 흐름(레시피 → 요리 기록 → 중복 후기 → 회원 → 저장). 후기 저장은 같은 목으로 만든 실제 RecipeReviewService가 한다.
 */
@ExtendWith(MockitoExtension.class)
class ReviewFacadeTest {

    @Mock
    private RecipeReviewRepository recipeReviewRepository;

    @Mock
    private MemberService memberService;

    @Mock
    private RecipeService recipeService;

    @Mock
    private CookingService cookingService;

    private ReviewFacade reviewFacade;

    @BeforeEach
    void setUp() {
        reviewFacade = new ReviewFacade(recipeService, cookingService, memberService,
                new RecipeReviewService(recipeReviewRepository, recipeService));
    }

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
     * 후기 작성 시 레시피가 존재하지 않으면 404 RECIPE_NOT_FOUND 예외가 발생하고 요리 기록 확인을 수행하지 않는지 검증한다.
     */
    @Test
    @DisplayName("없는 레시피에 후기를 쓰면 RECIPE_NOT_FOUND 예외를 던지고 요리 기록을 확인하지 않는다")
    void throwsRecipeNotFound_whenRecipeIsMissing() {
        // Given
        Long memberId = 1L;
        ReviewCreateRequest request = new ReviewCreateRequest(999L, 5, "후기 내용");
        when(recipeService.existsById(999L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> reviewFacade.createReview(memberId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECIPE_NOT_FOUND);
        verify(cookingService, never()).hasCooked(any(), any());
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 후기 작성 시 요리 기록이 없으면 403 REVIEW_NOT_COOKED 예외가 발생하고 중복 확인을 수행하지 않는지 검증한다.
     */
    @Test
    @DisplayName("요리 기록 없이 후기를 쓰면 REVIEW_NOT_COOKED 예외를 던지고 중복 확인을 하지 않는다")
    void throwsNotCooked_whenMemberHasNotCooked() {
        // Given
        Long memberId = 1L;
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "후기 내용");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> reviewFacade.createReview(memberId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_NOT_COOKED);
        verify(recipeReviewRepository, never()).existsByMemberIdAndRecipeId(any(), any());
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 후기 작성 성공 시 recipeName을 포함하여 응답을 반환하는지 검증한다.
     */
    @Test
    @DisplayName("후기를 쓰면 저장하고 레시피 이름을 담아 반환한다")
    void savesReviewAndReturnsRecipeName() {
        // Given: 1번 회원의 10번 레시피 후기 작성 요청
        Long memberId = 1L;
        Member member = createMember(memberId, "사용자1");
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "정말 맛있어요!");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberService.getMember(memberId)).thenReturn(member);

        RecipeReview savedReview = createReview(100L, member, 10L, 5, "정말 맛있어요!");
        when(recipeReviewRepository.save(any(RecipeReview.class))).thenReturn(savedReview);
        when(recipeService.findSummaries(List.of(10L))).thenReturn(Map.of(
                10L, new RecipeSummary(10L, "김치찌개", "image.jpg", null)
        ));

        // When
        ReviewResponse response = reviewFacade.createReview(memberId, request);

        // Then
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.recipeName()).isEqualTo("김치찌개");
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.content()).isEqualTo("정말 맛있어요!");
        assertThat(response.mine()).isTrue();
        verify(recipeReviewRepository).save(any(RecipeReview.class));
        verify(recipeService, times(1)).findSummaries(List.of(10L));
    }

    /**
     * 후기 작성 시 회원이 존재하지 않으면 404 MEMBER_NOT_FOUND 예외가 발생하는지 검증한다.
     */
    @Test
    @DisplayName("없는 회원이 후기를 쓰면 MEMBER_NOT_FOUND 예외를 던진다")
    void throwsMemberNotFound_whenMemberIsMissing() {
        // Given
        Long memberId = 999L;
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "후기 내용");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberService.getMember(memberId)).thenThrow(new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        // When & Then
        assertThatThrownBy(() -> reviewFacade.createReview(memberId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 이미 동일 레시피에 후기를 작성한 회원이 중복 작성을 시도하면
     * 사전 조회에서 409 REVIEW_ALREADY_EXISTS 예외가 발생하고 저장을 호출하지 않는지 검증한다.
     */
    @Test
    @DisplayName("같은 레시피에 이미 후기를 쓴 회원이 다시 쓰면 REVIEW_ALREADY_EXISTS 예외를 던지고 저장하지 않는다")
    void throwsAlreadyExists_whenReviewAlreadyExists() {
        // Given: 이미 후기가 존재하는 레시피
        Long memberId = 1L;
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "중복 후기");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> reviewFacade.createReview(memberId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
        verify(recipeReviewRepository, never()).save(any(RecipeReview.class));
    }

    /**
     * 동시 요청으로 인해 DB 유니크 제약(uk_recipe_reviews_member_id_recipe_id) 위반 시
     * 대소문자 무관하게 409 REVIEW_ALREADY_EXISTS 예외로 변환하여 던지는지 검증한다.
     */
    @Test
    @DisplayName("동시 요청으로 후기 유니크 제약을 위반하면 REVIEW_ALREADY_EXISTS 예외로 바꿔 던진다")
    void throwsAlreadyExists_whenUniqueConstraintIsViolated() {
        // Given: 사전 조회는 통과했으나 save 시점에 DB 유니크 제약 위반 발생 (대문자 포함 메시지)
        Long memberId = 1L;
        Member member = createMember(memberId, "사용자1");
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "동시 후기");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberService.getMember(memberId)).thenReturn(member);
        when(recipeReviewRepository.save(any(RecipeReview.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'UK_RECIPE_REVIEWS_MEMBER_ID_RECIPE_ID'"));

        // When & Then
        assertThatThrownBy(() -> reviewFacade.createReview(memberId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    /**
     * 유니크 제약 위반이 아닌 다른 DB 무결성 위반(예: 외래키 위반 등)은
     * 409로 변환하지 않고 DataIntegrityViolationException 그대로 다시 던지는지 검증한다.
     */
    @Test
    @DisplayName("유니크 제약이 아닌 다른 무결성 위반은 바꾸지 않고 그대로 던진다")
    void rethrowsDataIntegrityViolation_whenOtherConstraintIsViolated() {
        // Given: 유니크 제약이 아닌 다른 무결성 제약 위반 발생
        Long memberId = 1L;
        Member member = createMember(memberId, "사용자1");
        ReviewCreateRequest request = new ReviewCreateRequest(10L, 5, "후기 내용");

        when(recipeService.existsById(10L)).thenReturn(true);
        when(cookingService.hasCooked(memberId, 10L)).thenReturn(true);
        when(recipeReviewRepository.existsByMemberIdAndRecipeId(memberId, 10L)).thenReturn(false);
        when(memberService.getMember(memberId)).thenReturn(member);
        when(recipeReviewRepository.save(any(RecipeReview.class)))
                .thenThrow(new DataIntegrityViolationException("Cannot add or update a child row: a foreign key constraint fails"));

        // When & Then
        assertThatThrownBy(() -> reviewFacade.createReview(memberId, request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
